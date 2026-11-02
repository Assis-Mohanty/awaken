package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §16.14-16.15 dungeons and programs (vectors #127-#134). */
class LongArcRulesTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `vector 127 - locked below level 10, refused without the fee, entered with it`() {
        assertThat(DungeonRules.canEnter(level = 9, gold = 500, lengthDays = 3, hasActiveDungeon = false)).isEqualTo(DungeonEntry.LOCKED)
        assertThat(DungeonRules.canEnter(level = 10, gold = 80, lengthDays = 3, hasActiveDungeon = false)).isEqualTo(DungeonEntry.NOT_ENOUGH_GOLD)
        assertThat(DungeonRules.canEnter(level = 10, gold = 500, lengthDays = 3, hasActiveDungeon = false)).isEqualTo(DungeonEntry.OK)
        assertThat(DungeonRules.fee(3)).isEqualTo(100)
        // Stage 1 is the next game-day: Mon entry → Tue, Wed, Thu.
        val first = DungeonRules.firstStageDate(monday)
        assertThat((1..3).map { DungeonRules.stageDate(first, it) }).containsExactly(monday.plusDays(1), monday.plusDays(2), monday.plusDays(3)).inOrder()
    }

    @Test
    fun `fees and rewards - 100 200 300 and one and a half times back`() {
        assertThat(DungeonRules.LENGTHS.map { DungeonRules.fee(it) }).containsExactly(100, 200, 300).inOrder()
        assertThat(DungeonRules.LENGTHS.map { DungeonRules.reward(it) }).containsExactly(150, 300, 450).inOrder()
        assertThat(DungeonRules.fee(4)).isNull()
        assertThat(DungeonRules.canEnter(10, 1_000, 4, false)).isEqualTo(DungeonEntry.BAD_LENGTH)
        assertThat(DungeonRules.canEnter(10, 1_000, 7, hasActiveDungeon = true)).isEqualTo(DungeonEntry.ALREADY_ACTIVE)
        assertThat(DungeonRules.canEnter(10, 300, 7, false)).isEqualTo(DungeonEntry.OK)
    }

    @Test
    fun `a stage is found by its date and nothing else is`() {
        val first = monday.plusDays(1)
        assertThat(DungeonRules.stageOn(first, 3, monday)).isNull()
        assertThat(DungeonRules.stageOn(first, 3, first)).isEqualTo(1)
        assertThat(DungeonRules.stageOn(first, 3, first.plusDays(2))).isEqualTo(3)
        assertThat(DungeonRules.stageOn(first, 3, first.plusDays(3))).isNull()
    }

    @Test
    fun `vectors 128 and 129 - the last stage clears it, an unfinished one fails it`() {
        assertThat(DungeonRules.verdict(stage = 1, lengthDays = 3, stageCompleted = true)).isEqualTo(DungeonVerdict.STAGE_DONE)
        assertThat(DungeonRules.verdict(stage = 3, lengthDays = 3, stageCompleted = true)).isEqualTo(DungeonVerdict.CLEARED)
        assertThat(DungeonRules.verdict(stage = 2, lengthDays = 3, stageCompleted = false)).isEqualTo(DungeonVerdict.FAILED)
        assertThat(DungeonRules.FRAGMENTS_ON_CLEAR).isEqualTo(1)
    }

    @Test
    fun `vector 130 - a Hard self stage pays 90 XP and 45 gold`() {
        val xp = XpCalculator.questXp(Difficulty.HARD, streakDays = 0, verification = VerificationSource.SELF, crit = false)
        assertThat(xp).isEqualTo(90)
        assertThat(XpCalculator.goldFor(xp)).isEqualTo(45)
    }

    private fun template(id: String, stat: Stat, vararg difficulties: Difficulty) = QuestTemplate(
        id = id, stat = stat, titles = listOf(id), description = "Do {target}", unit = "reps",
        targets = difficulties.associateWith { 10 }, verification = Verification.SELF,
    )

    private val library = listOf(
        template("a", Stat.STR, Difficulty.EASY, Difficulty.HARD),
        template("b", Stat.STR, Difficulty.HARD),
        template("c", Stat.STR, Difficulty.NORMAL),
        template("d", Stat.INT, Difficulty.HARD),
        template("e", Stat.STR, Difficulty.HARD, Difficulty.NORMAL),
    )
    private val context = HunterContext(hunterId = "h1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `stage quests are Hard, in the chosen stat, deterministic and never rerollable`() {
        val first = DirectiveGenerator.dungeonStage(library, context, Stat.STR, "d1", 1, monday.plusDays(1))!!
        val again = DirectiveGenerator.dungeonStage(library, context, Stat.STR, "d1", 1, monday.plusDays(1))!!

        assertThat(first).isEqualTo(again)
        assertThat(first.type).isEqualTo(QuestType.DUNGEON)
        assertThat(first.difficulty).isEqualTo(Difficulty.HARD)
        assertThat(first.stat).isEqualTo(Stat.STR)
        assertThat(first.templateId).isIn(listOf("a", "b", "e"))
        assertThat(DirectiveGenerator.canReroll(QuestType.DUNGEON)).isFalse()
        assertThat(QuestType.DUNGEON.isGenerated).isFalse()
        assertThat(QuestType.DUNGEON.canCrit).isTrue()
        // Different stages are different quests (ids), whatever template they roll.
        assertThat(DirectiveGenerator.dungeonStage(library, context, Stat.STR, "d1", 2, monday.plusDays(2))!!.questInstanceId).isNotEqualTo(first.questInstanceId)
    }

    @Test
    fun `stage templates respect equipment and injury filters, and no fit means no stage`() {
        val gear = library + QuestTemplate("f", Stat.WIL, titles = listOf("f"), description = "", unit = "x", targets = mapOf(Difficulty.HARD to 5), verification = Verification.SELF, equipment = listOf("kettlebell"))
        assertThat(DirectiveGenerator.dungeonStage(gear, context, Stat.WIL, "d1", 1, monday)).isNull() // kettlebell not owned
        assertThat(DirectiveGenerator.dungeonStage(gear, context.copy(ownedEquipment = setOf("kettlebell")), Stat.WIL, "d1", 1, monday)).isNotNull()
        assertThat(DirectiveGenerator.dungeonStage(library, context, Stat.CHA, "d1", 1, monday)).isNull()
        val injured = listOf(template("x", Stat.STR, Difficulty.HARD).copy(tags = listOf("impact")))
        assertThat(DirectiveGenerator.dungeonStage(injured, context.copy(injuryTags = setOf("impact")), Stat.STR, "d1", 1, monday)).isNull()
    }

    @Test
    fun `the catalog has the three originally named programs and their lengths`() {
        assertThat(ProgramRules.CATALOG.map { it.key to it.days }).containsExactly("steady_30" to 30, "groundwork_66" to 66, "ironclad_75" to 75).inOrder()
        assertThat(ProgramRules.CATALOG.map { it.rules.size }).containsExactly(3, 4, 4).inOrder()
        assertThat(ProgramRules.spec("ironclad_75")!!.needsPhoneDownNight).isTrue()
        assertThat(ProgramRules.spec("steady_30")!!.needsPhoneDownNight).isFalse()
        assertThat(ProgramRules.spec("nope")).isNull()
        // No diet, weight, fasting or substance rule anywhere.
        assertThat(ProgramRules.CATALOG.flatMap { it.rules }.map { it.stat }.toSet()).doesNotContain(Stat.CHA)
    }

    @Test
    fun `health rules fall back to plain ones when Health is off`() {
        val walk = ProgramRules.spec("steady_30")!!.rules.first()
        assertThat(ProgramRules.effective(walk, healthOn = true).verification).isEqualTo(Verification.HEALTH_STEPS)
        val plain = ProgramRules.effective(walk, healthOn = false)
        assertThat(plain.verification).isEqualTo(Verification.SELF)
        assertThat(plain.target to plain.unit).isEqualTo(30 to "min")
        val read = ProgramRules.spec("steady_30")!!.rules[1]
        assertThat(ProgramRules.effective(read, healthOn = false)).isEqualTo(read)
        val workout = ProgramRules.spec("ironclad_75")!!.rules.first()
        assertThat(ProgramRules.effective(workout, healthOn = false).verification).isEqualTo(Verification.SELF)
    }

    @Test
    fun `vector 131 - restart makes the answer day day 1, continue goes on with the day marked`() {
        val spec = ProgramRules.spec("steady_30")!!
        // Started Monday; day 5 (Friday) is broken, and it is answered on Saturday.
        val friday = monday.plusDays(4)
        assertThat(ProgramRules.dayNumber(monday, friday)).isEqualTo(5)
        assertThat(ProgramRules.dayVerdict(allRulesDone = false, restOrRecovery = false)).isEqualTo(ProgramDay.BROKEN)
        val saturday = monday.plusDays(5)
        assertThat(ProgramRules.dayNumber(saturday, saturday)).isEqualTo(1) // restart
        assertThat(ProgramRules.dayNumber(monday, saturday)).isEqualTo(6) // continue
        assertThat(ProgramRules.lastDate(monday, spec)).isEqualTo(monday.plusDays(29))
        assertThat(ProgramRules.isLastDay(monday, spec, monday.plusDays(29))).isTrue()
        assertThat(ProgramRules.isLastDay(monday, spec, monday.plusDays(28))).isFalse()
    }

    @Test
    fun `vector 132 - no broken day completes it, any broken day only finishes it`() {
        assertThat(ProgramRules.endStatus(brokenDays = 0)).isEqualTo(ProgramStatus.COMPLETED)
        assertThat(ProgramRules.endStatus(brokenDays = 1)).isEqualTo(ProgramStatus.FINISHED)
        val none = AchievementSnapshot(completedPrograms = setOf("steady_30"))
        assertThat(AchievementEvaluator.evaluate(none)).contains("program_steady_30")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot())).doesNotContain("program_steady_30")
        assertThat(AchievementEvaluator.evaluate(none.copy(alreadyUnlocked = setOf("program_steady_30")))).doesNotContain("program_steady_30")
    }

    @Test
    fun `vector 133 - an unanswered break is a continue`() {
        assertThat(ProgramRules.DEFAULT_BREAK).isEqualTo(ProgramBreakChoice.CONTINUE)
    }

    @Test
    fun `vector 134 - a rest day is not broken and the counter still advances`() {
        assertThat(ProgramRules.dayVerdict(allRulesDone = false, restOrRecovery = true)).isEqualTo(ProgramDay.REST)
        assertThat(ProgramRules.dayVerdict(allRulesDone = true, restOrRecovery = true)).isEqualTo(ProgramDay.KEPT)
        assertThat(ProgramRules.dayNumber(monday, monday.plusDays(3))).isEqualTo(4)
    }

    @Test
    fun `a program starts only with the slots, no campaign and no other program`() {
        val spec = ProgramRules.spec("groundwork_66")!!
        assertThat(ProgramRules.canStart(spec, freeHabitSlots = 4, campaignActive = false, programActive = false)).isEqualTo(ProgramStart.OK)
        assertThat(ProgramRules.canStart(spec, 3, false, false)).isEqualTo(ProgramStart.NOT_ENOUGH_SLOTS)
        assertThat(ProgramRules.canStart(spec, 8, campaignActive = true, programActive = false)).isEqualTo(ProgramStart.CAMPAIGN_ACTIVE)
        assertThat(ProgramRules.canStart(spec, 8, false, programActive = true)).isEqualTo(ProgramStart.PROGRAM_ACTIVE)
    }

    @Test
    fun `dungeon achievements pay gold once`() {
        val byId = AchievementEvaluator.byId
        assertThat(byId.getValue("dungeon_first").gold).isEqualTo(20)
        assertThat(byId.getValue("dungeon_7").gold).isEqualTo(100)
        assertThat(byId.getValue("dungeon_7").titleId).isEqualTo("delver")
        assertThat(listOf("program_steady_30", "program_groundwork_66", "program_ironclad_75").map { byId.getValue(it).gold }).containsExactly(150, 200, 200).inOrder()
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(dungeonsCleared = 1))).contains("dungeon_first")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(dungeonsCleared = 1))).doesNotContain("dungeon_7")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(dungeonsCleared = 2, sevenDayDungeonCleared = true))).contains("dungeon_7")
    }
}
