package com.assismohanty.awaken.core.designsystem.lexicon

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R

/**
 * How the app talks (GD §14.9): plain Clean words, or the System voice. The app ships Clean only since the v2 redesign
 * dropped the System mode toggle; SYSTEM remains so the word pairs stay checked by tests until the strings are retired.
 */
enum class PresentationMode { CLEAN, SYSTEM }

/** Game terms with a Clean and a System name (DESIGN_SYSTEM §8.3). */
enum class Term(@param:StringRes val clean: Int, @param:StringRes val system: Int) {
    HUNTER(R.string.term_hunter_clean, R.string.term_hunter_system),
    GATE(R.string.term_gate_clean, R.string.term_gate_system),
    GATES(R.string.term_gates_clean, R.string.term_gates_system),
    GATE_COLLAPSED(R.string.term_gate_collapsed_clean, R.string.term_gate_collapsed_system),
    BREACH(R.string.term_breach_clean, R.string.term_breach_system),
    DIRECTIVE(R.string.term_directive_clean, R.string.term_directive_system),
    DIRECTIVE_CLEARED(R.string.term_directive_cleared_clean, R.string.term_directive_cleared_system),
    PENALTY_ZONE(R.string.term_penalty_zone_clean, R.string.term_penalty_zone_system),
    PENALTY_QUEST(R.string.term_penalty_quest_clean, R.string.term_penalty_quest_system),
    RANK_TRIAL(R.string.term_rank_trial_clean, R.string.term_rank_trial_system),
    AWAKENING(R.string.term_awakening_clean, R.string.term_awakening_system),
    VAULT(R.string.term_vault_clean, R.string.term_vault_system),
    STATUS(R.string.term_status_clean, R.string.term_status_system),
    STREAK_SHIELD(R.string.term_streak_shield_clean, R.string.term_streak_shield_system),
    RECALIBRATED(R.string.term_recalibrated_clean, R.string.term_recalibrated_system),
    ORACLE(R.string.term_oracle_clean, R.string.term_oracle_system),
}

/**
 * Picks the words for a [PresentationMode]. Every user-visible string goes through [resolve]: in Clean mode a string with
 * a Clean twin (`<name>_clean`, see [LexiconPairs]) is swapped for it; everything else is shared.
 */
@Immutable
class Lexicon private constructor(val mode: PresentationMode) {
    @StringRes
    fun resolve(@StringRes id: Int): Int = if (mode == PresentationMode.CLEAN) LexiconPairs.strings[id] ?: id else id

    @PluralsRes
    fun resolvePlural(@PluralsRes id: Int): Int = if (mode == PresentationMode.CLEAN) LexiconPairs.plurals[id] ?: id else id

    @StringRes
    fun term(term: Term): Int = if (mode == PresentationMode.CLEAN) term.clean else term.system

    companion object {
        val System = Lexicon(PresentationMode.SYSTEM)
        val Clean = Lexicon(PresentationMode.CLEAN)

        fun of(mode: PresentationMode): Lexicon = if (mode == PresentationMode.CLEAN) Clean else System
    }
}

val LocalLexicon = staticCompositionLocalOf { Lexicon.Clean }

/** `stringResource` through the Lexicon. Feature code uses this, never `stringResource`. */
@Composable
@ReadOnlyComposable
fun lexString(@StringRes id: Int): String = stringResource(LocalLexicon.current.resolve(id))

@Composable
@ReadOnlyComposable
fun lexString(@StringRes id: Int, vararg formatArgs: Any): String = stringResource(LocalLexicon.current.resolve(id), *formatArgs)

/** `pluralStringResource` through the Lexicon. */
@Composable
@ReadOnlyComposable
fun lexPlural(@PluralsRes id: Int, count: Int): String = pluralStringResource(LocalLexicon.current.resolvePlural(id), count)

@Composable
@ReadOnlyComposable
fun lexPlural(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(LocalLexicon.current.resolvePlural(id), count, *formatArgs)

@Composable
@ReadOnlyComposable
fun termString(term: Term): String = stringResource(LocalLexicon.current.term(term))

/** [Resources] lookups through a [Lexicon], for code that builds text outside composition (toasts, notifications). */
class LexResources(private val resources: Resources, val lexicon: Lexicon) {
    val configuration: Configuration get() = resources.configuration

    fun getString(@StringRes id: Int): String = resources.getString(lexicon.resolve(id))

    fun getString(@StringRes id: Int, vararg formatArgs: Any): String = resources.getString(lexicon.resolve(id), *formatArgs)

    fun getQuantityString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
        resources.getQuantityString(lexicon.resolvePlural(id), count, *formatArgs)
}

@Composable
fun rememberLexResources(): LexResources {
    val resources = LocalResources.current
    val lexicon = LocalLexicon.current
    return remember(resources, lexicon) { LexResources(resources, lexicon) }
}
