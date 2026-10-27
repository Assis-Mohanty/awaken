package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import com.assismohanty.awaken.core.designsystem.lexicon.Lexicon
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The current [Lexicon] for text built outside composition (notifications, GD §14.9). Mirrors the presentation pref in
 * memory so callers stay synchronous; until the pref is first read (a few ms after process start) it answers with System
 * words, which only affects wording.
 */
@Singleton
class LexiconSource internal constructor(private val lexicon: StateFlow<Lexicon>) {

    @Inject
    constructor(uiPrefs: UiPrefsRepository, @ApplicationScope scope: CoroutineScope) :
        this(uiPrefs.prefs.map { Lexicon.of(it.presentationMode) }.stateIn(scope, SharingStarted.Eagerly, Lexicon.System))

    val current: Lexicon get() = lexicon.value

    fun resources(context: Context): LexResources = LexResources(context.resources, current)

    companion object {
        /** Always System words: the default for code built without DI (tests). */
        val Fixed = LexiconSource(MutableStateFlow(Lexicon.System))
    }
}
