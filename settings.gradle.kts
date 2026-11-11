pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (offline text reading for the nutrition-label photo) is published only on JitPack. This repository is
        // allowed to supply that one group and nothing else, so no other dependency can come from it.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "AWAKEN"
include(":app")
include(":engine")
include(":reelcounter-core")
include(":reelcounter-android")
