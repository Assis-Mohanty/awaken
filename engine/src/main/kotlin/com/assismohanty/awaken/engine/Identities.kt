package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One "Someone who …" statement (GD §14.7); display text lives in the app's strings.xml as `identity_<id>`. */
@Serializable
data class Identity(val id: String, val stat: Stat)

/** `assets/identities.json` (GD §14.7). Identities grant nothing; they only pre-select focus stats and frame the review. */
@Serializable
data class IdentityCatalog(val version: Int, val identities: List<Identity>) {

    val byId: Map<String, Identity> get() = identities.associateBy { it.id }

    /** Problems with the bundled file; empty when valid. */
    fun validate(): List<String> = buildList {
        if (identities.map { it.id }.toSet().size != identities.size) add("identity ids must be unique")
        identities.filterNot { ID_PATTERN.matches(it.id) }.forEach { add("${it.id}: id must be lower_snake_case") }
        Stat.entries.filter { stat -> identities.none { it.stat == stat } }.forEach { add("no identity for $it") }
    }

    /**
     * Focus stats pre-selected by [picks] (ordered ids): the distinct stats in pick order, first two. A single distinct
     * stat pre-selects only that one; the hunter can still change both. Unknown ids are ignored.
     */
    fun preselect(picks: List<String>): List<Stat> =
        picks.mapNotNull { byId[it]?.stat }.distinct().take(FOCUS_STATS)

    companion object {
        /** How many identities a hunter may hold (GD §14.7). */
        const val MAX_PICKS = 3
        private const val FOCUS_STATS = 2
        private val ID_PATTERN = Regex("[a-z]+(_[a-z]+)*")
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): IdentityCatalog = json.decodeFromString(serializer(), text)
    }
}
