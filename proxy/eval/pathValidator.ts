/**
 * A TypeScript port of `:engine`'s `PathValidator` V1-V9 rules (GAME_DESIGN §6.6.4,
 * engine/src/main/kotlin/com/assismohanty/awaken/engine/path/PathValidator.kt), for `eval/run.ts` only.
 *
 * Ported rather than shelled out to a JVM CLI: the eval harness is a quick CI/local script with no reason to
 * require a JVM on whatever machine runs it, and these 9 rules are small and stable (GD §6.6.4 is the source of
 * truth — if it changes, update both this file and the Kotlin one; `:engine`'s own tests, not this file, are the
 * source of truth for the app's actual behaviour).
 */

export type Stat = "STR" | "INT" | "WIL" | "VIT" | "CHA";
export type PathUnit = "MINUTES" | "REPS" | "PAGES" | "PROBLEMS" | "ITEMS" | "SESSIONS" | "KM";
export type Effort = "EASY" | "NORMAL" | "HARD" | "EXTREME";
export type PathMode = "SELF" | "GATE";

const UNIT_BOUNDS: Record<PathUnit, { min: number; max: number }> = {
  MINUTES: { min: 5, max: 120 },
  REPS: { min: 5, max: 300 },
  PAGES: { min: 2, max: 100 },
  PROBLEMS: { min: 1, max: 10 },
  ITEMS: { min: 1, max: 20 },
  SESSIONS: { min: 1, max: 3 },
  KM: { min: 1, max: 21 },
};

const EST_MINUTES_MIN = 5;
const EST_MINUTES_MAX = 120;
const BUDGET_FACTOR = 1.2;
const TAGS = new Set(["impact", "legs", "upper", "outdoor", "indoor", "social", "quiet", "screen"]);

const EQUIPMENT_KEYWORDS: Record<string, RegExp[]> = {
  dumbbells: [/dumbbells?/i],
  kettlebell: [/kettlebells?/i],
  pullup_bar: [/pull-up bar/i, /pullup bar/i, /chin-up bar/i],
  resistance_band: [/resistance bands?/i, /exercise bands?/i],
  jump_rope: [/jump rope/i, /skipping rope/i, /skip rope/i],
  gym_access: [/gym/i, /barbell/i, /treadmill/i, /squat rack/i, /bench press/i],
  bicycle: [/bicycle/i, /bike/i, /cycling/i],
};

function unownedEquipmentMention(text: string, owned: Set<string>): string | null {
  for (const [id, patterns] of Object.entries(EQUIPMENT_KEYWORDS)) {
    if (owned.has(id)) continue;
    if (patterns.some((p) => p.test(text))) return id;
  }
  return null;
}

const URL_RE = /https?:\/\/\S+/gi;
const CONTROL_RE = /[\u0000-\u001f\u007f-\u009f]/g;
const WHITESPACE_RE = /\s+/g;

export function cleanText(raw: string, max: number): string {
  const collapsed = raw.replace(URL_RE, " ").replace(CONTROL_RE, " ").replace(WHITESPACE_RE, " ").trim();
  return [...collapsed].slice(0, max).join("").trim();
}

export function duplicateKey(title: string): string {
  return [...title.toLowerCase()].filter((ch) => /\p{L}|\p{N}/u.test(ch)).join("");
}

function clampRounded(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) return min;
  if (value >= max) return max;
  if (value <= min) return min;
  return Math.min(max, Math.max(min, Math.round(value)));
}

export interface RawBlueprint {
  title?: unknown;
  description?: unknown;
  stat?: unknown;
  unit?: unknown;
  target?: unknown;
  effort?: unknown;
  estMinutes?: unknown;
  mode?: unknown;
  tags?: unknown;
  rationale?: unknown;
}

export interface ValidatedBlueprint {
  title: string;
  description: string;
  stat: Stat;
  unit: PathUnit;
  target: number;
  effort: Effort;
  estMinutes: number;
  mode: PathMode;
  tags: string[];
  rationale: string;
}

export interface ValidationContext {
  primaryStat: Stat;
  secondaryStat: Stat | null;
  weeklyMinutes: number;
  weeklyQuestLimit: number;
  ownedEquipment: Set<string>;
  injuryTags: Set<string>;
  recentTitles: string[];
}

export interface DropReason {
  index: number;
  rule: "V1" | "V2" | "V6" | "V7" | "V8" | "V9";
  detail: string;
}

export function validateWeek(blueprints: RawBlueprint[], context: ValidationContext): { kept: ValidatedBlueprint[]; drops: DropReason[] } {
  const drops: DropReason[] = [];
  const stage1: (ValidatedBlueprint & { index: number })[] = [];

  blueprints.forEach((raw, index) => {
    const drop = (rule: DropReason["rule"], detail: string) => drops.push({ index, rule, detail });

    if (typeof raw !== "object" || raw === null) return drop("V1", "not an object");
    const rawTitle = raw.title;
    const rawDescription = raw.description;
    if (typeof rawTitle !== "string") return drop("V1", "title missing");
    if (typeof rawDescription !== "string") return drop("V1", "description missing");
    const stat = raw.stat as Stat;
    if (!["STR", "INT", "WIL", "VIT", "CHA"].includes(stat)) return drop("V1", "unknown stat");
    const unit = raw.unit as PathUnit;
    if (!(unit in UNIT_BOUNDS)) return drop("V1", "unknown unit");
    const effort = raw.effort as Effort;
    if (!["EASY", "NORMAL", "HARD", "EXTREME"].includes(effort)) return drop("V1", "unknown effort");
    const mode = raw.mode as PathMode;
    if (!["SELF", "GATE"].includes(mode)) return drop("V1", "unknown mode");
    if (typeof raw.target !== "number") return drop("V1", "target missing or not a number");
    if (typeof raw.estMinutes !== "number") return drop("V1", "estMinutes missing or not a number");

    let tags: string[] = [];
    if (raw.tags != null) {
      if (!Array.isArray(raw.tags) || raw.tags.some((t) => typeof t !== "string" || !TAGS.has(t))) {
        return drop("V1", "unknown tag");
      }
      tags = raw.tags;
    }
    let rationale = "";
    if (raw.rationale != null) {
      if (typeof raw.rationale !== "string") return drop("V1", "rationale not a string");
      rationale = raw.rationale;
    }

    const title = cleanText(rawTitle, 48);
    if (title.length === 0) return drop("V2", "title empty after cleaning");
    const description = cleanText(rawDescription, 280);
    rationale = cleanText(rationale, 140);

    const keptStat: Stat = stat === context.primaryStat || stat === context.secondaryStat ? stat : context.primaryStat;
    const bounds = UNIT_BOUNDS[unit];
    const keptTarget = clampRounded(raw.target, bounds.min, bounds.max);
    const keptMinutes = clampRounded(raw.estMinutes, EST_MINUTES_MIN, EST_MINUTES_MAX);

    const injuredTag = tags.find((t) => context.injuryTags.has(t));
    if (injuredTag) return drop("V6", `tag "${injuredTag}" excluded by Injury Mode`);
    const unowned = unownedEquipmentMention(`${title} ${description}`, context.ownedEquipment);
    if (unowned) return drop("V6", `needs equipment "${unowned}"`);

    stage1.push({ index, title, description, stat: keptStat, unit, target: keptTarget, effort, estMinutes: keptMinutes, mode, tags, rationale });
  });

  const seen = new Set(context.recentTitles.map(duplicateKey).filter((k) => k.length > 0));
  const unique = stage1.filter((bp) => {
    const key = duplicateKey(bp.title);
    if (key.length > 0 && seen.has(key)) {
      drops.push({ index: bp.index, rule: "V7", detail: `duplicate title "${bp.title}"` });
      return false;
    }
    if (key.length > 0) seen.add(key);
    return true;
  });

  const budget = Math.round(context.weeklyMinutes * BUDGET_FACTOR);
  let used = 0;
  let overBudget = false;
  const withinBudget = unique.filter((bp) => {
    if (!overBudget && used + bp.estMinutes <= budget) {
      used += bp.estMinutes;
      return true;
    }
    overBudget = true;
    drops.push({ index: bp.index, rule: "V8", detail: `over the weekly budget of ${budget} min` });
    return false;
  });

  const limit = context.weeklyQuestLimit;
  withinBudget.slice(limit).forEach((bp) => drops.push({ index: bp.index, rule: "V9", detail: `more than ${limit} quests` }));

  drops.sort((a, b) => a.index - b.index);
  return { kept: withinBudget.slice(0, limit).map(({ index: _i, ...rest }) => rest), drops };
}

export interface RawMilestone {
  title?: unknown;
  summary?: unknown;
  exitCriterion?: unknown;
}

export function validatePlan(milestones: RawMilestone[]): { title: string; summary: string; exitCriterion: string }[] {
  const cleaned = milestones
    .map((m) => {
      const title = typeof m?.title === "string" ? cleanText(m.title, 48) : "";
      if (title.length === 0) return null;
      const summary = typeof m?.summary === "string" ? cleanText(m.summary, 200) : "";
      const exitCriterion = typeof m?.exitCriterion === "string" ? cleanText(m.exitCriterion, 140) : "";
      return { title, summary, exitCriterion };
    })
    .filter((m): m is { title: string; summary: string; exitCriterion: string } => m !== null)
    .slice(0, 10);
  return cleaned.length >= 4 ? cleaned : [];
}
