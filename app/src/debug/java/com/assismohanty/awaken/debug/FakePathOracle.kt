package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.data.ai.OracleOutcome
import com.assismohanty.awaken.core.data.ai.OracleReply
import com.assismohanty.awaken.core.data.ai.PathOracleClient
import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathWeekRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Debug builds and tests: answers like the Path Oracle without a network (ARCH §3.7, §7). Replies are deterministic and
 * shaped by the request (a ladder of 5, up to `maxBlueprints` quests with one in three as a Focus Gate), so the whole
 * Paths flow can be exercised offline. Output still goes through `PathValidator` like any real reply.
 */
class FakePathOracle(private val latencyMs: Long = 0) : PathOracleClient {
    var planCalls = 0
        private set
    var weekCalls = 0
        private set
    var reportCalls = 0
        private set

    /** Calls of any kind: "declining consent makes no network call" asserts this stays 0. */
    val totalCalls: Int get() = planCalls + weekCalls + reportCalls

    override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> {
        planCalls++
        delay(latencyMs)
        val milestones = buildJsonArray {
            MILESTONES.forEach { (title, summary, exit) ->
                add(
                    buildJsonObject {
                        put("title", title)
                        put("summary", "$summary Toward: ${request.path.goal.take(60)}.")
                        put("exitCriterion", exit)
                    },
                )
            }
        }
        return OracleOutcome.Success(reply("plan_path.v1", buildJsonObject { put("milestones", milestones) }))
    }

    override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean): OracleOutcome<OracleReply> {
        weekCalls++
        delay(latencyMs)
        val used = request.lastWeek.map { it.title.lowercase() }.toSet()
        val perQuest = (request.path.weeklyMinutes / request.maxBlueprints.coerceAtLeast(1)).coerceIn(5, 90)
        val seed = request.milestone.title.hashCode().let { if (it < 0) -it else it }
        val titles = TITLES.indices.map { TITLES[(seed + it * 7) % TITLES.size] }.filter { it.lowercase() !in used }.distinct()
        val blueprints = buildJsonArray {
            for (i in 0 until request.maxBlueprints) {
                val gate = i % 3 == 2
                add(
                    buildJsonObject {
                        put("title", titles[i % titles.size])
                        put("description", "Work on \"${request.milestone.title}\": ${request.path.goal.take(80)}. Log what you finished.")
                        put("stat", request.path.primaryStat.name)
                        put("unit", if (gate) "MINUTES" else "ITEMS")
                        put("target", if (gate) perQuest else 3)
                        put("effort", if (gate) "HARD" else "NORMAL")
                        put("estMinutes", perQuest)
                        put("mode", if (gate) "GATE" else "SELF")
                        put("tags", JsonArray(emptyList()))
                        put("rationale", "Keeps ${request.milestone.title} moving (${request.progressionSignal.name}).")
                    },
                )
            }
        }
        return OracleOutcome.Success(
            reply("week_quests.v1", buildJsonObject { put("suggestAdvance", false); put("blueprints", blueprints) }),
        )
    }

    override suspend fun report(report: ReportRequest): OracleOutcome<Unit> {
        reportCalls++
        delay(latencyMs)
        return OracleOutcome.Success(Unit)
    }

    private fun reply(promptVersion: String, payload: JsonObject): OracleReply {
        val body = JsonObject(mapOf("promptVersion" to JsonPrimitive(promptVersion)) + payload)
        return OracleReply(promptVersion, body.toString())
    }

    private companion object {
        val MILESTONES = listOf(
            Triple("Foundations", "Build the basic habit and vocabulary.", "Practice on 3 separate days."),
            Triple("First Steps", "Apply the basics to real attempts.", "Finish 5 small attempts."),
            Triple("Steady Pace", "Make progress routine.", "Hold a two-week rhythm."),
            Triple("Sharpening", "Target the weak spots.", "Fix 3 recurring mistakes."),
            Triple("The Milestone", "Prove the goal is within reach.", "Complete one full trial run."),
        )

        val TITLES = listOf(
            "Iron Routine", "Quiet Hour", "Deep Pass", "Edge Work", "Steady Climb", "Clean Reps", "Focus Block",
            "Small Victory", "Night Review", "Morning Push", "Long Haul", "Sharp Drill", "Slow Burn", "Open Gate",
            "Final Stretch", "Warm Start", "Hard Look", "Tight Loop", "Wide Net", "Last Mile",
        )
    }
}
