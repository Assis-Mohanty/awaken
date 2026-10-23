package com.assismohanty.awaken.feature.health

import java.util.Locale

/** Display names for the apps most likely to write to Health Connect. Anything else shows its package's last word. */
private val KNOWN_SOURCES = mapOf(
    "com.google.android.apps.fitness" to "Google Fit",
    "com.google.android.apps.healthdata" to "Health Connect",
    "com.fitbit.FitbitMobile" to "Fitbit",
    "com.strava" to "Strava",
    "com.sec.android.app.shealth" to "Samsung Health",
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
    "com.ouraring.oura" to "Oura",
    "com.zhiliaoapp.zepp" to "Zepp",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.withings.wiscale2" to "Withings",
    "com.peloton.callisto" to "Peloton",
    "com.nike.plusgps" to "Nike Run Club",
    "com.myfitnesspal.android" to "MyFitnessPal",
    "com.runtastic.android" to "Adidas Running",
    "com.google.android.apps.wearables.maestro.companion" to "Pixel Buds",
    "com.google.android.gms" to "Google",
)

/** "Strava" for com.strava, "Fitbit" for com.fitbit.FitbitMobile; unknown packages fall back to their last segment. */
fun healthSourceName(origin: String): String =
    KNOWN_SOURCES[origin] ?: origin.substringAfterLast('.').replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "strength_training" → "Strength training"; null when the source gave no usable label. */
fun prettyWorkoutLabel(label: String?): String? =
    label?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
