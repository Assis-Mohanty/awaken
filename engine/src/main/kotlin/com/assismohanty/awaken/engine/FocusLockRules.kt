package com.assismohanty.awaken.engine

import java.time.Instant

/** What Focus Lock does when an app comes to the front (GD 8.2). */
enum class BlockDecision { ALLOW, BLOCK }

/**
 * The running focus session as Focus Lock sees it.
 * @param endsAt the planned end as known now (it moves during a pause); null for a stopwatch, which has no end
 */
data class LockGate(val running: Boolean, val paused: Boolean, val endsAt: Instant?)

/**
 * The whole "should this app be covered right now?" decision, kept pure so it can be tested without a phone. Nothing
 * is covered unless a session is running (not paused, not past its end). Phone, messages, emergency, Settings, the
 * keyboard, the launcher and AWAKEN itself are never covered. Every other app is, unless the hunter allowed it.
 */
object FocusLockRules {
    /** Packages that are never covered on any phone. The app adds this phone's launcher, keyboard, dialer and SMS app at runtime. */
    val NEVER_BLOCK: Set<String> = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /**
     * Well-known UPI and bank apps. Focus Lock never watches or covers them, so an enabled accessibility service can not
     * disturb a payment. The app adds every UPI handler and the default tap-to-pay app found on the phone at runtime.
     */
    val PAYMENT_APPS: Set<String> = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp",
        "com.sbi.lyp.onlinesbi",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
    )

    /** Anything whose name says it is a dialer, in-call screen, emergency or telecom app is left alone too. */
    private val ALWAYS_SAFE_NAME = Regex("""(^|\.)(dialer|incallui|emergency|telecom|sos)(\.|$)""")

    fun neverBlocked(packageName: String, alwaysAllowed: Set<String> = emptySet()): Boolean =
        packageName in NEVER_BLOCK || packageName in PAYMENT_APPS || packageName in alwaysAllowed || ALWAYS_SAFE_NAME.containsMatchIn(packageName)

    /**
     * The packages the accessibility service listens to while a session runs: [candidates] minus every payment app, plus
     * [ownPackage]. Never empty, because an empty list would mean "every app" to Android.
     */
    fun watchScope(candidates: Set<String>, payment: Set<String>, ownPackage: String): Set<String> =
        (candidates - payment - PAYMENT_APPS) + ownPackage

    /**
     * @param alwaysAllowed this phone's launcher, keyboard, dialer and SMS app, and AWAKEN's own package
     * @param userAllowed apps the hunter chose to leave uncovered
     */
    fun decide(
        packageName: String?,
        gate: LockGate,
        alwaysAllowed: Set<String>,
        userAllowed: Set<String>,
        now: Instant,
    ): BlockDecision = when {
        !gate.running || gate.paused -> BlockDecision.ALLOW
        gate.endsAt != null && !now.isBefore(gate.endsAt) -> BlockDecision.ALLOW
        packageName.isNullOrBlank() -> BlockDecision.ALLOW
        neverBlocked(packageName, alwaysAllowed) -> BlockDecision.ALLOW
        packageName in userAllowed -> BlockDecision.ALLOW
        else -> BlockDecision.BLOCK
    }

    /**
     * The fallback when Focus Lock was not running (GD 8.2, D2): true if any app opened during the stretch away is
     * Distracting and not one that is always allowed.
     */
    fun distractedDuring(openedPackages: Set<String>, distracting: Set<String>, alwaysAllowed: Set<String>): Boolean =
        openedPackages.any { it in distracting && !neverBlocked(it, alwaysAllowed) }
}
