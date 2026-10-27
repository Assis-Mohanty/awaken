package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import com.assismohanty.awaken.core.designsystem.lexicon.Lexicon
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [Lexicon] for text built outside composition (notifications). System mode was retired in the v2 redesign, so this
 * is always the Clean words; it stays a seam so callers don't reach for raw resources.
 */
@Singleton
class LexiconSource @Inject constructor() {

    val current: Lexicon get() = Lexicon.Clean

    fun resources(context: Context): LexResources = LexResources(context.resources, current)

    companion object {
        /** The default for code built without DI (tests). */
        val Fixed = LexiconSource()
    }
}
