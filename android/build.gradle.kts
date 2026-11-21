// Top-level build file. It only declares plugin versions; the real settings live in app/build.gradle.kts.
// Android Gradle Plugin 9 has Kotlin support built in, so no separate Kotlin Android plugin is needed.
plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
