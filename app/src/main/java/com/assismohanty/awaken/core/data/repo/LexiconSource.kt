package com.assismohanty.awaken.core.data.repo

import android.content.Context
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import javax.inject.Inject
import javax.inject.Singleton

/** [LexResources] for text built outside composition (notifications); a seam so callers don't reach for raw resources. */
@Singleton
class LexiconSource @Inject constructor() {

    fun resources(context: Context): LexResources = LexResources(context.resources)

    companion object {
        /** The default for code built without DI (tests). */
        val Fixed = LexiconSource()
    }
}
