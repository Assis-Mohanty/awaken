package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Real Tesseract 5 output on rendered nutrition tables (src/test/resources/ocr: word, position, confidence), including the slips it
 * made: lost cells, columns split into separate lines, decimal points dropped, and g read as a nine. Plus tables laid out by hand.
 */
class NutritionLabelParserTest {
    private fun fixture(name: String): List<OcrWord> =
        requireNotNull(javaClass.getResourceAsStream("/ocr/$name.tsv")) { "missing $name" }.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { OcrWord(it[0], it[1].toInt(), it[2].toInt(), it[3].toInt(), it[4].toInt(), it[5].toInt()) } }

    /** A table laid out by hand: each row is a list of cells; every cell is one word, placed in its column. */
    private fun table(vararg rows: List<String>, columnX: List<Int> = listOf(10, 140, 300, 460, 620, 780)): List<OcrWord> =
        rows.flatMapIndexed { r, cells ->
            cells.mapIndexedNotNull { c, text -> if (text.isEmpty()) null else OcrWord(text, columnX[c], 20 + r * 40, columnX[c] + 70, 44 + r * 40, 95) }
        }

    private fun parse(words: List<OcrWord>) = requireNotNull(NutritionLabelParser.parse(words)) { "nothing found" }

    private fun ParsedLabel.at(n: Nutrient) = per100.getValue(n)

    @Test
    fun aMissingPer100CellIsLeftEmptyNeverFilledFromTheServingColumn() {
        // Real output: the reader kept the per-serve numbers (6.3 fat, 120 sodium, 6.0 sugars) but lost their per-100 g neighbours.
        val label = parse(fixture("label-a"))

        assertThat(label.servingGrams).isEqualTo(30.0)
        assertThat(label.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(7.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(64.0)
        assertThat(label.per100.keys).containsNoneOf(Nutrient.FAT, Nutrient.SODIUM, Nutrient.SUGARS)
        assertThat(label.per100.values).doesNotContain(6.3)
        assertThat(label.per100.values).doesNotContain(120.0)
        assertThat(label.isComplete).isFalse()
    }

    @Test
    fun droppedDecimalPointsAndAGReadAsANineAreRepairedAgainstTheCalories() {
        // Real output: "7.0 g" came out as 70g, "64.0 g" as 64.09, "21.0 g" as 210g, "3.0 g" as 30g, and kcal read from "2008 kJ / 480 kcal".
        val label = parse(fixture("label-b"))

        assertThat(label.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(7.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(64.0)
        assertThat(label.at(Nutrient.FAT)).isEqualTo(21.0)
        assertThat(label.at(Nutrient.FIBER)).isEqualTo(3.0)
        assertThat(label.at(Nutrient.SUGARS)).isEqualTo(20.0)
        assertThat(label.at(Nutrient.SAT_FAT)).isEqualTo(10.0)
        assertThat(label.isComplete).isTrue()
        // What was repaired is marked, and so is the salt figure that lost its decimal point (10 g for 1.0 g), so the hunter checks them.
        assertThat(label.unsure).containsAtLeast(Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.SODIUM)
    }

    @Test
    fun twoColumnsAreToldApartByPositionEvenWhenTheServingColumnComesFirst() {
        // Real output: the second column came back as separate lines with no nutrient name beside them.
        val label = parse(fixture("label-c"))

        assertThat(label.servingGrams).isEqualTo(25.0)
        assertThat(label.perServingOnly).isFalse()
        assertThat(label.at(Nutrient.KCAL)).isEqualTo(520.0)
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(12.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(60.0)
        assertThat(label.at(Nutrient.FAT)).isEqualTo(26.0)
        assertThat(label.per100.values).doesNotContain(130.0)
        assertThat(label.per100.values).doesNotContain(3.0)
    }

    @Test
    fun theSmallLanguageModelThatShipsReadsTheTiltedLabelWithEveryDecimalAndNothingUnsure() {
        // Real output of tessdata_fast, the 4 MB model the app carries: "7.0g", "64.0g", "21.0g", "3.0g", "1.0g" all intact.
        val label = parse(fixture("label-b-fast"))

        assertThat(label.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(7.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(64.0)
        assertThat(label.at(Nutrient.FAT)).isEqualTo(21.0)
        assertThat(label.at(Nutrient.FIBER)).isEqualTo(3.0)
        assertThat(label.at(Nutrient.SODIUM)).isWithin(0.01).of(400.0)
        assertThat(label.isComplete).isTrue()
    }

    @Test
    fun theSmallModelOnTheTwoColumnLabelRepairsItsOneSlipAndStillSkipsTheMissingCells() {
        // Real output: fat per 100 g came out as "26.09" (the g read as a nine).
        val c = parse(fixture("label-c-fast"))
        assertThat(c.at(Nutrient.KCAL)).isEqualTo(520.0)
        assertThat(c.at(Nutrient.PROTEIN)).isEqualTo(12.0)
        assertThat(c.at(Nutrient.CARBS)).isEqualTo(60.0)
        assertThat(c.at(Nutrient.FAT)).isEqualTo(26.0)
        assertThat(c.at(Nutrient.SODIUM)).isEqualTo(720.0)

        // Real output: several per-100 g cells were not read; none is filled from the serving column.
        val a = parse(fixture("label-a-fast"))
        assertThat(a.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(a.per100.values).containsNoneOf(6.3, 120.0, 3.0, 0.9)
    }

    @Test
    fun aCleanTwoColumnTableGivesEveryPer100GramValue() {
        val label = parse(cleanTable())

        assertThat(label.isComplete).isTrue()
        assertThat(label.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(7.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(64.0)
        assertThat(label.at(Nutrient.FAT)).isEqualTo(21.0)
        assertThat(label.at(Nutrient.SUGARS)).isEqualTo(20.0) // total sugars, not the added-sugar line
        assertThat(label.at(Nutrient.SAT_FAT)).isEqualTo(10.0)
        assertThat(label.at(Nutrient.SODIUM)).isEqualTo(400.0)
        assertThat(label.unsure).isEmpty()
        assertThat(label.servingGrams).isEqualTo(30.0)
    }

    /** "Per 100 g" under the first value column, "Per serve (30 g)" under the second; numbers carry decimals. */
    private fun cleanTable(): List<OcrWord> {
        val x = listOf(10, 380, 560)
        fun row(r: Int, name: String, a: String, b: String): List<OcrWord> =
            listOf(OcrWord(name, x[0], 20 + r * 40, x[0] + 200, 44 + r * 40, 95), OcrWord(a, x[1], 20 + r * 40, x[1] + 60, 44 + r * 40, 95), OcrWord(b, x[2], 20 + r * 40, x[2] + 60, 44 + r * 40, 95))
        return listOf(
            OcrWord("Per", x[1], 20, x[1] + 30, 44, 95), OcrWord("100", x[1] + 34, 20, x[1] + 70, 44, 95), OcrWord("g", x[1] + 74, 20, x[1] + 90, 44, 95),
            OcrWord("Per", x[2], 20, x[2] + 30, 44, 95), OcrWord("serve", x[2] + 34, 20, x[2] + 90, 44, 95), OcrWord("(30", x[2] + 94, 20, x[2] + 130, 44, 95), OcrWord("g)", x[2] + 134, 20, x[2] + 150, 44, 95),
        ) + row(1, "Energy (kcal)", "480", "144") + row(2, "Protein (g)", "7.0", "2.1") + row(3, "Carbohydrate (g)", "64.0", "19.2") +
            row(4, "Total Sugars (g)", "20.0", "6.0") + row(5, "Added Sugars (g)", "18.0", "5.4") + row(6, "Total Fat (g)", "21.0", "6.3") +
            row(7, "Saturated Fat (g)", "10.0", "3.0") + row(8, "Sodium (mg)", "400", "120")
    }

    @Test
    fun aLabelWithOnlyAServingColumnIsScaledToPer100GramsAndSaysSo() {
        val words = listOf(
            OcrWord("Per", 300, 10, 340, 34, 95), OcrWord("serving", 346, 10, 420, 34, 95),
            OcrWord("Serving", 10, 50, 90, 74, 95), OcrWord("size", 96, 50, 140, 74, 95), OcrWord("1", 146, 50, 156, 74, 95), OcrWord("cup", 162, 50, 205, 74, 95), OcrWord("(240", 211, 50, 260, 74, 95), OcrWord("g)", 266, 50, 290, 74, 95),
            OcrWord("Calories", 10, 90, 100, 114, 95), OcrWord("120", 330, 90, 380, 114, 95),
            OcrWord("Protein", 10, 130, 100, 154, 95), OcrWord("6", 330, 130, 345, 154, 95), OcrWord("g", 350, 130, 365, 154, 95),
            OcrWord("Total", 10, 170, 60, 194, 95), OcrWord("Fat", 66, 170, 100, 194, 95), OcrWord("3", 330, 170, 345, 194, 95), OcrWord("g", 350, 170, 365, 194, 95),
            OcrWord("Total", 10, 210, 60, 234, 95), OcrWord("Carbohydrate", 66, 210, 190, 234, 95), OcrWord("24", 330, 210, 360, 234, 95), OcrWord("g", 365, 210, 380, 234, 95),
            OcrWord("Sodium", 10, 250, 90, 274, 95), OcrWord("160", 330, 250, 370, 274, 95), OcrWord("mg", 376, 250, 410, 274, 95),
        )
        val label = parse(words)

        assertThat(label.perServingOnly).isTrue()
        assertThat(label.servingGrams).isEqualTo(240.0)
        assertThat(label.at(Nutrient.KCAL)).isWithin(0.01).of(50.0) // 120 per 240 g
        assertThat(label.at(Nutrient.PROTEIN)).isWithin(0.01).of(2.5)
        assertThat(label.at(Nutrient.SODIUM)).isWithin(0.01).of(66.67)
    }

    @Test
    fun noColumnHeadingsMeansTheSingleColumnIsTakenAsPer100Grams() {
        val label = parse(
            table(
                listOf("Energy", "480", "kcal"), listOf("Protein", "7.0", "g"), listOf("Carbohydrate", "64.0", "g"), listOf("Fat", "21.0", "g"), listOf("Salt", "1.0", "g"),
            ),
        )
        assertThat(label.at(Nutrient.KCAL)).isEqualTo(480.0)
        assertThat(label.at(Nutrient.SODIUM)).isWithin(0.01).of(400.0) // 1.0 g salt = 400 mg sodium
        assertThat(label.isComplete).isTrue()
    }

    @Test
    fun percentOfDailyNeedIsNeverTakenAsAnAmount() {
        val label = parse(
            table(
                listOf("Energy", "480", "24%"), listOf("Protein", "7.0g", "14%"), listOf("Carbohydrate", "64.0g", "21%"), listOf("Fat", "21.0g", "32%"),
            ),
        )
        assertThat(label.at(Nutrient.PROTEIN)).isEqualTo(7.0)
        assertThat(label.at(Nutrient.CARBS)).isEqualTo(64.0)
        assertThat(label.at(Nutrient.FAT)).isEqualTo(21.0)
    }

    @Test
    fun wordsThatAreNotANutritionTableGiveNothing() {
        assertThat(NutritionLabelParser.parse(emptyList())).isNull()
        assertThat(NutritionLabelParser.parse(table(listOf("Best", "before", "12", "months"), listOf("MRP", "Rs", "20")))).isNull()
    }

    @Test
    fun lowConfidenceWordsAreMarkedUnsure() {
        val words = table(listOf("Energy", "480", "kcal"), listOf("Protein", "7.0", "g"), listOf("Carbohydrate", "64.0", "g"), listOf("Fat", "21.0", "g"))
            .map { if (it.text == "7.0") it.copy(confidence = 40) else it }
        assertThat(parse(words).unsure).containsExactly(Nutrient.PROTEIN)
    }
}
