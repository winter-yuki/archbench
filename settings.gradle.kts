rootProject.name = "archbench"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }

    plugins {
        id("org.jetbrains.compose") version "1.2.2"
    }
}

include(":lib")
include(":archs", ":archs:testing", ":archs:async", ":archs:blocking", ":archs:nonblocking")
include(":runner", ":app")
