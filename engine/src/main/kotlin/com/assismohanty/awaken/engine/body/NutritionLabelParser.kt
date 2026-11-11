package com.assismohanty.awaken.engine.body

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** One word a phone recognised from a photo, with where it sat on the picture (pixels) and how sure the reader was (0 to 100). */
data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Int) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = bottom - top
}

/**
 * What a nutrition table said, as per 100 g. [servingGrams] is the serving size when the label gave one. [perServingOnly] is true
 * when the label had no per-100 g column, so the values were scaled from a serving. [unsure] names the values the reader was
 * not confident about or that had to be repaired: the screen marks them, and the hunter checks every number against the pack.
 */
data class ParsedLabel(
    val per100: Map<Nutrient, Double>,
    val servingGrams: Double?,
    val perServingOnly: Boolean = false,
    val unsure: Set<Nutrient> = emptySet(),
) {
    /** Calories and the three macros were all found. */
    val isComplete: Boolean get() = listOf(Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT).all { it in per100 }
}

/**
 * Reads a nutrition table from recognised words and where they sit (Diet v2, "add it from the label"). Text readers lose cells,
 * split columns into separate lines and drop decimal points, so this works from positions: words are grouped into rows, the
 * "Per 100 g" and "Per serve" headings become columns, and each number belongs to the column it sits under. A number in the
 * wrong column is never used: a missing cell stays missing. It fills only what it can tie to a nutrient name, tries the likely
 * decimal slips against the label's own calories, and marks what it is unsure of. Handles kJ and kcal, salt for sodium, and
 * the share-of-daily-need column (ignored). Pure.
 */
object NutritionLabelParser {
    private val NUMBER = Regex("""^<?(\d+(?:\.\d+)?)(kcal|kj|cal|mg|mcg|µg|μg|ug|g)?$""")
    private val UNIT = Regex("""^(kcal|kj|cal|mg|mcg|µg|μg|ug|g|gm|grams?)$""")
    private val SLIPPED_G = Regex("""^\d+\.\d9$""")
    private val UNIT_HINT = Regex("""\(\s*(kcal|kj|mg|mcg|µg|μg|ug|g)\s*\)""")
    private val SERVE_WORDS = setOf("serve", "serving", "servings", "portion", "pack", "piece", "slice", "biscuit")
    private val BRACKET_GRAMS = Regex("""\(\s*(\d+(?:\.\d+)?)\s*(?:g|gm|grams?)\s*\)""")
    private val HEADING_GRAMS = Regex("""(?:serving size|serve size|per (?:serve|serving|portion|pack))\D{0,12}(\d+(?:\.\d+)?)\s*(?:g|gm|grams?)\b""")

    private const val MG_PER_G = 1_000.0
    private const val UG_PER_MG = 1_000.0
    private const val LINE_OVERLAP = 0.4
    private const val UNSURE_BELOW = 70
    private const val ALMOST = 0.01
    private const val HEADING_WORDS = 6
    private const val WHOLE_NUMBER_FROM = 10.0

    private enum class ColumnKind { PER_100, PER_SERVE }

    private data class Column(val kind: ColumnKind, val centerX: Double)

    private data class Item(val value: Double, val unit: String?, val centerX: Double, val confidence: Int, val decimals: Int, val slipped: Boolean) {
        val hadDecimal: Boolean get() = decimals > 0
    }

    private data class Row(val words: List<OcrWord>)

    /** Which nutrient a row's name is about, most specific first, or null when it is something else. */
    private fun nutrientOf(label: String): Nutrient? = when {
        "added" in label -> null
        "saturate" in label || "sat." in label || "sat fat" in label -> Nutrient.SAT_FAT
        "trans" in label -> Nutrient.TRANS_FAT
        "monounsat" in label || ("mono" in label && "fat" in label) -> Nutrient.MONO_FAT
        "polyunsat" in label || ("poly" in label && "fat" in label) -> Nutrient.POLY_FAT
        "cholesterol" in label -> Nutrient.CHOLESTEROL
        "energy" in label || "calorie" in label || "kcal" in label -> Nutrient.KCAL
        "protein" in label -> Nutrient.PROTEIN
        "carbohydrate" in label || "carbs" in label -> Nutrient.CARBS
        "sugar" in label -> Nutrient.SUGARS
        "fibre" in label || "fiber" in label -> Nutrient.FIBER
        "sodium" in label -> Nutrient.SODIUM
        "salt" in label -> Nutrient.SODIUM
        "fat" in label -> Nutrient.FAT
        else -> null
    }

    fun parse(words: List<OcrWord>): ParsedLabel? {
        val cleaned = words.filter { it.text.isNotBlank() }.map { it.copy(text = cleanToken(it.text)) }
        if (cleaned.isEmpty()) return null
        val rows = rowsOf(cleaned)

        val columns = rows.flatMap { columnsIn(it) }.distinctBy { it.kind }
        val servingGrams = servingGramsFrom(rows)
        val perServingOnly = columns.none { it.kind == ColumnKind.PER_100 } && columns.any { it.kind == ColumnKind.PER_SERVE } && servingGrams != null

        val found = linkedMapOf<Nutrient, Double>()
        val sure = linkedMapOf<Nutrient, Int>()
        val tokens = linkedMapOf<Nutrient, Item>()
        var salt: Pair<Double, Item>? = null
        for (row in rows) {
            val label = labelOf(row)
            if (label.isBlank() || "per" in label.split(' ')) continue
            val nutrient = nutrientOf(label) ?: continue
            val items = itemsOf(row)
            if (items.isEmpty()) continue
            val hint = UNIT_HINT.find(label)?.groupValues?.get(1)
            val isSalt = nutrient == Nutrient.SODIUM && "salt" in label && "sodium" !in label
            val item = (if (nutrient == Nutrient.KCAL) energyItem(items, columns, hint) else columnItem(items, columns, perServingOnly)) ?: continue
            val value = if (nutrient == Nutrient.KCAL) item.value else toStoredUnit(nutrient, item, hint, isSalt)
            val scaled = if (perServingOnly) value * Nutrients.PER_GRAMS / requireNotNull(servingGrams) else value
            if (isSalt) {
                if (salt == null) salt = scaled to item
            } else if (nutrient !in found) {
                found[nutrient] = scaled
                sure[nutrient] = item.confidence
                if (nutrient != Nutrient.KCAL) tokens[nutrient] = item // calories are never printed with decimals, so they say nothing about the label
            }
        }
        val saltFallback = salt
        if (Nutrient.SODIUM !in found && saltFallback != null) {
            found[Nutrient.SODIUM] = saltFallback.first
            sure[Nutrient.SODIUM] = saltFallback.second.confidence
        }
        if (found.isEmpty()) return null

        val repaired = repairSlips(found, tokens, perServingOnly)
        val values = repaired.values.toMutableMap()
        val changed = repaired.changed.toMutableSet()
        // A part can not outweigh its whole: sugars over carbs, or saturated fat over fat, is a lost decimal point.
        for ((part, whole) in listOf(Nutrient.SUGARS to Nutrient.CARBS, Nutrient.SAT_FAT to Nutrient.FAT)) {
            val v = values[part] ?: continue
            val w = values[whole] ?: continue
            if (v > w + LabelPlausibility.PART_TOLERANCE && tokens[part]?.hadDecimal == false && v >= WHOLE_NUMBER_FROM) {
                values[part] = v / WHOLE_NUMBER_FROM
                changed += part
            }
        }
        val unsure = buildSet {
            sure.forEach { (n, confidence) -> if (confidence < UNSURE_BELOW) add(n) }
            addAll(changed)
            // On a label that prints decimals, a whole number of ten or more is probably "1.0" with its point lost.
            val printsDecimals = tokens.values.count { it.hadDecimal } * 2 >= tokens.size
            if (printsDecimals) {
                tokens.forEach { (n, t) -> if (!t.hadDecimal && t.value >= WHOLE_NUMBER_FROM && n.unit == NutrientUnit.G && n !in changed) add(n) }
                if (saltFallback != null && !saltFallback.second.hadDecimal && saltFallback.second.value >= WHOLE_NUMBER_FROM && Nutrient.SODIUM !in tokens) add(Nutrient.SODIUM)
            }
        }
        return ParsedLabel(values, servingGrams, perServingOnly, unsure)
    }

    // ---- words to rows and columns

    /**
     * Words that share a text line form a row. Overlap in height decides it, not distance between centres, because a hand-held
     * photo is a little tilted and the numbers on the right then sit lower than their names on the left.
     */
    private fun rowsOf(words: List<OcrWord>): List<Row> {
        val rows = mutableListOf<MutableList<OcrWord>>()
        for (word in words.sortedBy { it.centerY }) {
            val row = rows.lastOrNull { r -> r.any { sameLine(it, word) } }
            if (row != null) row += word else rows += mutableListOf(word)
        }
        return rows.map { Row(it.sortedBy { w -> w.left }) }
    }

    private fun sameLine(a: OcrWord, b: OcrWord): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap >= LINE_OVERLAP * minOf(a.height, b.height).coerceAtLeast(1)
    }

    /** The "Per 100 g" and "Per serve" headings in a row, each with where it sits. */
    private fun columnsIn(row: Row): List<Column> {
        val w = row.words
        val result = mutableListOf<Column>()
        for ((i, word) in w.withIndex()) {
            val isPer = word.text == "per" || (word.text.startsWith("per") && word.text.drop(3).let { it.isNotEmpty() && (it[0].isDigit() || it in SERVE_WORDS) })
            if (!isPer) continue
            val span = listOf(word) + w.subList(i + 1, minOf(i + HEADING_WORDS, w.size)).takeWhile { it.text != "per" && !it.text.startsWith("per") }
            val text = span.joinToString("") { it.text }
            val kind = when {
                "per100" in text -> ColumnKind.PER_100
                SERVE_WORDS.any { "per$it" in text } -> ColumnKind.PER_SERVE
                else -> null
            } ?: continue
            result += Column(kind, (span.first().left + span.last().right) / 2.0)
        }
        return result
    }

    private fun servingGramsFrom(rows: List<Row>): Double? {
        for (row in rows) {
            val text = row.words.joinToString(" ") { it.text }
            val isServingRow = "serving size" in text || "serve size" in text || SERVE_WORDS.any { "per $it" in text || "per$it" in text }
            if (!isServingRow) continue
            // Grams in brackets after the household measure ("1 cup (240 g)") or straight after the heading ("per serve (30 g)").
            BRACKET_GRAMS.find(text)?.let { return it.groupValues[1].toDouble() }
            HEADING_GRAMS.find(text)?.let { return it.groupValues[1].toDouble() }
        }
        return null
    }

    // ---- a row's name and numbers

    private fun isNumeric(text: String): Boolean = NUMBER.matches(text)

    private fun labelOf(row: Row): String =
        row.words.map { it.text }.filter { !isNumeric(it) && !UNIT.matches(it) && '%' !in it && it != "/" && it != "-" }.joinToString(" ").trim()

    private fun itemsOf(row: Row): List<Item> {
        val w = row.words
        val items = mutableListOf<Item>()
        var i = 0
        while (i < w.size) {
            val m = NUMBER.matchEntire(w[i].text)
            if (m == null) { i++; continue }
            var unit = m.groupValues[2].ifEmpty { null }
            val start = w[i].left
            var right = w[i].right
            var conf = w[i].confidence
            // The unit may be its own word: "480" "kcal".
            val next = w.getOrNull(i + 1)
            if (unit == null && next != null && UNIT.matches(next.text) && next.left - w[i].right <= max(w[i].height, next.height)) {
                unit = if (next.text == "gm" || next.text.startsWith("gram")) "g" else next.text
                right = next.right
                conf = minOf(conf, next.confidence)
                i++
            }
            val number = m.groupValues[1]
            items += Item(number.toDouble(), unit, (start + right) / 2.0, conf, number.substringAfter('.', "").length, unit == null && SLIPPED_G.matches(number))
            i++
        }
        return items
    }

    /** The item under the per-100 g column (or the only column); a missing cell is null, never another column's number. */
    private fun columnItem(items: List<Item>, columns: List<Column>, perServingOnly: Boolean): Item? {
        if (columns.isEmpty()) return items.firstOrNull()
        val want = if (perServingOnly) ColumnKind.PER_SERVE else ColumnKind.PER_100
        val target = columns.firstOrNull { it.kind == want } ?: return items.firstOrNull()
        return items.firstOrNull { nearest(it, columns) == target }
    }

    private fun nearest(item: Item, columns: List<Column>): Column = columns.minBy { abs(it.centerX - item.centerX) }

    /** Calories: a kcal figure in the wanted column if there is one, else kilojoules converted. */
    private fun energyItem(items: List<Item>, columns: List<Column>, hint: String?): Item? {
        val inColumn = if (columns.isEmpty()) {
            items
        } else {
            val want = if (columns.any { it.kind == ColumnKind.PER_100 }) ColumnKind.PER_100 else ColumnKind.PER_SERVE
            val target = columns.firstOrNull { it.kind == want } ?: return items.firstOrNull()
            items.filter { nearest(it, columns) == target }
        }
        inColumn.firstOrNull { it.unit == "kcal" || it.unit == "cal" }?.let { return it }
        inColumn.firstOrNull { it.unit == "kj" }?.let { return it.copy(value = LabelMath.kcalFromKj(it.value)) }
        val plain = inColumn.firstOrNull { it.unit == null } ?: return null
        return if (hint == "kj") plain.copy(value = LabelMath.kcalFromKj(plain.value)) else plain
    }

    /** The amount in the unit AWAKEN stores (grams, or milligrams for minerals; salt becomes sodium). */
    private fun toStoredUnit(nutrient: Nutrient, item: Item, hint: String?, isSalt: Boolean): Double {
        val unit = item.unit ?: hint ?: when {
            isSalt -> "g"
            nutrient.unit == NutrientUnit.MG -> "mg"
            nutrient.unit == NutrientUnit.UG -> "mcg"
            else -> "g"
        }
        val grams = when (unit) {
            "mg" -> item.value / MG_PER_G
            "mcg", "µg", "μg", "ug" -> item.value / (MG_PER_G * UG_PER_MG)
            else -> item.value
        }
        return when {
            isSalt -> LabelMath.sodiumMgFromSalt(grams)
            nutrient.unit == NutrientUnit.MG -> grams * MG_PER_G
            nutrient.unit == NutrientUnit.UG -> grams * MG_PER_G * UG_PER_MG
            else -> grams
        }
    }

    // ---- repairing the usual slips

    private data class Repaired(val values: Map<Nutrient, Double>, val changed: Set<Nutrient>)

    /**
     * Text readers drop decimal points ("7.0 g" as "70") and read the unit g as a nine ("64.0 g" as "64.09"). When the calories do
     * not match the macros as read, each macro is tried as read and with the slip undone, and the combination that matches the
     * label's own calories is kept. Nothing changes when the numbers already agree.
     */
    private fun repairSlips(original: Map<Nutrient, Double>, tokens: Map<Nutrient, Item>, perServingOnly: Boolean): Repaired {
        // On a label that prints one decimal everywhere, a second decimal ending in 9 is the unit g misread, not a real digit.
        val oneDecimalLabel = tokens.values.count { it.decimals == 1 } >= 2
        val slipFixed = if (oneDecimalLabel) tokens.filter { (_, t) -> t.slipped }.keys.filter { it in original } else emptyList()
        val found = original + slipFixed.associateWith { floor(original.getValue(it) * 10.0) / 10.0 }
        val kcal = found[Nutrient.KCAL]
        val macros = listOf(Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT)
        if (kcal == null || macros.any { it !in found } || perServingOnly) return Repaired(found, slipFixed.toSet())

        fun agrees(values: Map<Nutrient, Double>): Boolean =
            Atwater.check(Nutrients(values), ownRow = false) == AtwaterResult.OK && macros.sumOf { values.getValue(it) } <= LabelPlausibility.MACRO_SUM_LIMIT
        if (agrees(found)) return Repaired(found, slipFixed.toSet())

        fun options(n: Nutrient): List<Double> {
            val v = found.getValue(n)
            val t = tokens[n]
            return buildList {
                add(v)
                if (t != null && !t.hadDecimal && v >= 10.0) add(v / 10.0)
                if (t != null && t.slipped) add(floor(v * 10.0) / 10.0)
            }.distinct()
        }
        val tunable = macros + listOfNotNull(Nutrient.FIBER.takeIf { it in found })
        var combos = listOf(emptyMap<Nutrient, Double>())
        for (n in tunable) combos = combos.flatMap { combo -> options(n).map { combo + (n to it) } }
        var best: Map<Nutrient, Double>? = null
        var bestError = Double.MAX_VALUE
        for (combo in combos) {
            val candidate = found + combo
            if (!agrees(candidate)) continue
            val estimate = Atwater.estimateKcal(Nutrients(candidate)) ?: continue
            val error = abs(kcal - estimate)
            if (error < bestError - ALMOST) { best = candidate; bestError = error }
        }
        val fixed = best ?: return Repaired(found, slipFixed.toSet())
        return Repaired(fixed, tunable.filter { abs(fixed.getValue(it) - original.getValue(it)) > ALMOST }.toSet() + slipFixed)
    }

    /** Lowercases and repairs the usual recognition slips inside a number: decimal commas, a letter O standing in for a zero. */
    private fun cleanToken(raw: String): String {
        var s = raw.lowercase().trim()
        if (s.any { it.isDigit() }) {
            s = s.replace(Regex("""(?<=\d),(?=\d)"""), ".")
            s = s.replace(Regex("""(?<=\d)o(?=\d|$|[a-z%])"""), "0").replace(Regex("""^o(?=\d)"""), "0")
        }
        return s
    }
}
