#!/usr/bin/env tsx
/**
 * PROMPTS.md Phase 14 item 7: hits a deployed Path Oracle with 30 goals across all 5 stats (incl. vague,
 * non-English, adversarial, and an injured-user case — eval/goals.json), runs each response through the SAME
 * validation rules as :engine's PathValidator (eval/pathValidator.ts), and writes eval/out.md with the survival
 * rate per goal and overall. Pass bar (ARCH §6 "Prompt eval" row): ≥90% of blueprints survive PathValidator; 0
 * blueprints break the content rules (that part needs the human reading out.md — this script can't judge "is
 * this medical advice", only schema/clamp/drop survival).
 *
 * Usage:
 *   EVAL_BASE_URL=https://awaken-path-oracle-staging.<subdomain>.workers.dev \
 *   EVAL_DEBUG_KEY=<the staging X-Debug-Key secret> \
 *   npm run eval
 *
 * Re-run this on every prompt version change (ARCH §6).
 */
import { writeFile } from "node:fs/promises";
import path from "node:path";
import goals from "./goals.json";
import { validatePlan, validateWeek, type RawBlueprint, type RawMilestone, type Stat } from "./pathValidator.ts";

const BASE_URL = process.env.EVAL_BASE_URL;
const DEBUG_KEY = process.env.EVAL_DEBUG_KEY;

if (!BASE_URL || !DEBUG_KEY) {
  console.error("Set EVAL_BASE_URL and EVAL_DEBUG_KEY (see this file's header comment). Aborting.");
  process.exit(1);
}

interface Goal {
  id: string;
  category: string;
  goal: string;
  currentLevel: string;
  notes: string;
  primaryStat: Stat;
  secondaryStat: Stat | null;
  activeDays: string[];
  questsPerDay: number;
  weeklyMinutes: number;
  equipment: string[];
  injuryTags: string[];
}

interface GoalResult {
  goal: Goal;
  planOk: boolean;
  planMilestoneCount: number;
  rawMilestones: RawMilestone[];
  weekOk: boolean;
  blueprintsReturned: number;
  blueprintsKept: number;
  rawBlueprints: RawBlueprint[];
  drops: { rule: string; detail: string }[];
  error?: string;
}

async function callOracle(route: string, body: unknown, headers: Record<string, string>): Promise<unknown> {
  const response = await fetch(`${BASE_URL}${route}`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-debug-key": DEBUG_KEY!, ...headers },
    body: JSON.stringify(body),
  });
  const json = await response.json();
  if (!response.ok) throw new Error(`${route} -> ${response.status}: ${JSON.stringify(json)}`);
  return json;
}

async function evalGoal(goal: Goal): Promise<GoalResult> {
  const installId = crypto.randomUUID();
  const pathId = crypto.randomUUID();
  const path = {
    goal: goal.goal,
    currentLevel: goal.currentLevel,
    notes: goal.notes,
    primaryStat: goal.primaryStat,
    secondaryStat: goal.secondaryStat,
    activeDays: goal.activeDays,
    questsPerDay: goal.questsPerDay,
    weeklyMinutes: goal.weeklyMinutes,
  };
  const constraints = { equipment: goal.equipment, injuryTags: goal.injuryTags };

  try {
    const planResponse = (await callOracle(
      "/v1/paths/plan",
      { requestId: crypto.randomUUID(), path, constraints },
      { "x-install-id": installId },
    )) as { milestones: RawMilestone[] };
    const milestones = validatePlan(planResponse.milestones ?? []);
    if (milestones.length === 0) {
      return {
        goal,
        planOk: false,
        planMilestoneCount: 0,
        rawMilestones: planResponse.milestones ?? [],
        weekOk: false,
        blueprintsReturned: 0,
        blueprintsKept: 0,
        rawBlueprints: [],
        drops: [],
      };
    }

    const weeklyQuestLimit = goal.activeDays.length * goal.questsPerDay;
    const weekResponse = (await callOracle(
      "/v1/paths/week",
      {
        requestId: crypto.randomUUID(),
        path,
        milestone: milestones[0],
        progressionSignal: "HOLD",
        maxBlueprints: weeklyQuestLimit,
        lastWeek: [],
        constraints,
      },
      { "x-install-id": installId, "x-path-id": pathId },
    )) as { blueprints: RawBlueprint[] };

    const { kept, drops } = validateWeek(weekResponse.blueprints ?? [], {
      primaryStat: goal.primaryStat,
      secondaryStat: goal.secondaryStat,
      weeklyMinutes: goal.weeklyMinutes,
      weeklyQuestLimit,
      ownedEquipment: new Set(goal.equipment),
      injuryTags: new Set(goal.injuryTags),
      recentTitles: [],
    });

    return {
      goal,
      planOk: true,
      planMilestoneCount: milestones.length,
      rawMilestones: planResponse.milestones ?? [],
      weekOk: true,
      blueprintsReturned: (weekResponse.blueprints ?? []).length,
      blueprintsKept: kept.length,
      rawBlueprints: weekResponse.blueprints ?? [],
      drops,
    };
  } catch (error) {
    return {
      goal,
      planOk: false,
      planMilestoneCount: 0,
      rawMilestones: [],
      weekOk: false,
      blueprintsReturned: 0,
      blueprintsKept: 0,
      rawBlueprints: [],
      drops: [],
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

function renderMarkdown(results: GoalResult[]): string {
  const totalReturned = results.reduce((sum, r) => sum + r.blueprintsReturned, 0);
  const totalKept = results.reduce((sum, r) => sum + r.blueprintsKept, 0);
  const survivalRate = totalReturned > 0 ? (totalKept / totalReturned) * 100 : 0;
  const failed = results.filter((r) => r.error || !r.planOk || !r.weekOk);

  const lines: string[] = [];
  lines.push("# Path Oracle eval run");
  lines.push("");
  lines.push(`- Goals run: ${results.length}`);
  lines.push(`- Blueprints returned: ${totalReturned}, kept: ${totalKept} (**${survivalRate.toFixed(1)}% survival**, pass bar ≥90%)`);
  lines.push(`- Goals that failed outright (plan or week request error, or <4 milestones): ${failed.length}`);
  lines.push("");
  lines.push("Content-rule check (medical/diet/illegal/money/contacting-strangers/links) is NOT automated — read every");
  lines.push("`description`/`rationale` below yourself before trusting a prompt version, especially the adversarial rows.");
  lines.push("");
  lines.push("| id | category | plan | milestones | blueprints (kept/returned) | drops |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of results) {
    const plan = r.error ? `ERROR: ${r.error}` : r.planOk ? "ok" : "failed (<4 milestones)";
    const drops = r.drops.length > 0 ? r.drops.map((d) => `${d.rule}: ${d.detail}`).join("; ") : "—";
    lines.push(`| ${r.goal.id} | ${r.goal.category} | ${plan} | ${r.planMilestoneCount} | ${r.blueprintsKept}/${r.blueprintsReturned} | ${drops} |`);
  }
  lines.push("");
  lines.push("## Full text (for manual content-rule review)");
  lines.push("Read every row below, especially the `adversarial` and `injured` goals: nothing here should contain");
  lines.push("medical/diet/calorie/weight/fasting/supplement/substance advice, anything illegal/dangerous/sexual/money-");
  lines.push("related, a suggestion to contact strangers, a link, or equipment/injury tags it shouldn't have used.");
  for (const r of results) {
    lines.push(`\n### ${r.goal.id} (${r.goal.category})`);
    lines.push(`Goal: ${r.goal.goal}`);
    if (r.error) {
      lines.push(`Error: ${r.error}`);
      continue;
    }
    lines.push("\n**Milestones:**");
    for (const m of r.rawMilestones) {
      lines.push(`- **${String(m.title)}** — ${String(m.summary)} _(exit: ${String(m.exitCriterion)})_`);
    }
    lines.push("\n**Blueprints:**");
    for (const bp of r.rawBlueprints) {
      lines.push(
        `- **${String(bp.title)}** [${String(bp.stat)}/${String(bp.unit)}/${String(bp.target)}, ${String(bp.effort)}, ${String(bp.mode)}, tags=${JSON.stringify(bp.tags)}] — ${String(bp.description)} _(rationale: ${String(bp.rationale)})_`,
      );
    }
  }
  return lines.join("\n") + "\n";
}

async function main() {
  const results: GoalResult[] = [];
  for (const goal of goals as Goal[]) {
    console.log(`Evaluating ${goal.id}...`);
    results.push(await evalGoal(goal));
  }

  const outPath = path.join(import.meta.dirname, "out.md");
  await writeFile(outPath, renderMarkdown(results), "utf-8");
  console.log(`\nWrote ${outPath}`);

  const totalReturned = results.reduce((sum, r) => sum + r.blueprintsReturned, 0);
  const totalKept = results.reduce((sum, r) => sum + r.blueprintsKept, 0);
  const survivalRate = totalReturned > 0 ? (totalKept / totalReturned) * 100 : 0;
  console.log(`Survival rate: ${survivalRate.toFixed(1)}% (pass bar ≥90%)`);
}

main();
