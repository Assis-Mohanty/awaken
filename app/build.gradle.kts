import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing is read from keystore.properties (git-ignored). Absent file => unsigned release build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Path Oracle endpoints and keys come from local.properties (git-ignored), never from source. Missing values leave AI
// Paths unconfigured: debug builds fall back to FakePathOracle, release builds report the Oracle as unavailable.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun localProperty(name: String): String = localProperties.getProperty(name, "").trim()

android {
    lint {
        // targetSdk is pinned by CLAUDE.md (36); junit5 6.x is a major bump we have not adopted.
        informational += setOf("OldTargetApi", "NewerVersionAvailable")
    }

    namespace = "com.assismohanty.awaken"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.assismohanty.awaken"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Sideloaded debug APKs fail Play Integrity, so debug builds talk to staging with the debug key (ARCH §3.7).
            buildConfigField("String", "ORACLE_BASE_URL", localProperty("oracle.stagingUrl").asBuildConfigString())
            buildConfigField("String", "ORACLE_DEBUG_KEY", localProperty("oracle.debugKey").asBuildConfigString())
            buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", (localProperty("oracle.cloudProjectNumber").ifEmpty { "0" }) + "L")
        }
        release {
            buildConfigField("String", "ORACLE_BASE_URL", localProperty("oracle.productionUrl").asBuildConfigString())
            // Never ship a debug key (Phase 16 re-checks the release BuildConfig).
            buildConfigField("String", "ORACLE_DEBUG_KEY", "\"\"")
            buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", (localProperty("oracle.cloudProjectNumber").ifEmpty { "0" }) + "L")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // MigrationTestHelper (Robolectric) reads the exported Room schemas from the variant assets: debug only, never release.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.glance.appwidget)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    // AI Paths: the only network code in the app lives in core/data/ai (CLAUDE.md invariant 8).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.play.integrity)
    testImplementation(libs.ktor.client.mock)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.work.testing)
    // Compose UI tests run on Robolectric so the per-phase gate covers them without a device.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
