// Tells Gradle (the Android build tool) where to download plugins and libraries from,
// and which sub-projects make up this build. We only have one: the app.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Taskbreaker"
include(":app")
