package com.assismohanty.awaken.reelcounter.core.study

import java.text.Normalizer

/** What Study mode does about the video in front. */
enum class StudyDecision { ALLOW, BLOCK, UNKNOWN }

/**
 * Study mode matching (docs/protect/PLAN.md 4.2). Pure and exact: NFKC, lower-case, drop a leading "@", keep letters and
 * digits only, then equality. So "@3blue1brown" matches "3Blue1Brown". A handle that differs from the display name does not match.
 */
object ChannelMatch {
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC).lowercase().trim().removePrefix("@").filter { it.isLetterOrDigit() }

    /**
     * [shown] is the channel name read from the screen, or null when it could not be read. Unreadable fails open (UNKNOWN):
     * YouTube changes its layout often, and failing closed would block every video, study ones too, until a new build.
     */
    fun decide(shown: String?, list: Set<String>): StudyDecision {
        val key = shown?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return StudyDecision.UNKNOWN
        return if (list.any { normalize(it) == key }) StudyDecision.ALLOW else StudyDecision.BLOCK
    }
}
