package com.assismohanty.awaken.core.lock

import com.assismohanty.awaken.engine.OemFamily

/** One settings screen of a phone maker, by package and full activity class name. */
data class SettingsTarget(val packageName: String, val className: String)

/**
 * Where each phone maker hides its "autostart / background activity" screen (GD 8.2). These pages are not public API, so
 * the list is only a set of candidates to try in order; the caller falls back to AWAKEN's app-info page.
 */
object OemSettingsTargets {
    fun autostart(family: OemFamily): List<SettingsTarget> = when (family) {
        OemFamily.XIAOMI -> listOf(
            SettingsTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        OemFamily.OPPO -> listOf(
            SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            SettingsTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        OemFamily.ONEPLUS -> listOf(
            SettingsTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        OemFamily.VIVO -> listOf(
            SettingsTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            SettingsTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        OemFamily.HUAWEI -> listOf(
            SettingsTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            SettingsTarget("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        OemFamily.SAMSUNG -> listOf(
            SettingsTarget("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        OemFamily.OTHER -> emptyList()
    }
}
