import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Compile against the JDK 17 API, not the daemon's JDK. The engine ships inside the Android app,
        // so newer JDK methods (e.g. List.removeFirst from JDK 21) would crash on older devices.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    // Checks the AI Paths fixtures against /contracts (test-only; never shipped).
    testImplementation(libs.json.schema.validator)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Content assets live in :app; copy them into the engine's test resources so content is validated against the engine.
val copyContentAssets by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/assets")) {
        include("quests/**", "narrative/**")
    }
    into(layout.buildDirectory.dir("generated/contentAssets"))
}

// The AI Paths wire contracts live in /contracts (shared with /proxy); tests read them from the classpath.
val copyContracts by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        include("*.schema.json")
        into("contracts")
    }
    into(layout.buildDirectory.dir("generated/contracts"))
}

sourceSets.test {
    resources.srcDir(copyContentAssets)
    resources.srcDir(copyContracts)
}
