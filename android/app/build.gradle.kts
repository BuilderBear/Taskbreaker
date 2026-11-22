plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.builderbear.taskbreaker"
    // compileSdk = which Android APIs the code can see. Current AndroidX libraries require 37.
    // targetSdk (below) is separate: it opts the app in to newer runtime behavior.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.builderbear.taskbreaker"
        minSdk = 26 // Android 8.0. Covers practically every phone still in use.
        targetSdk = 36
        // Bump both for every release. versionCode must go up by at least 1, or the phone won't install the update.
        versionCode = 3
        versionName = "0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Some libraries (the Anthropic SDK pulls in Jackson and OkHttp) ship duplicate license/metadata
    // files. Android can only package one copy, so we drop the duplicates.
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    // Jetpack Compose: Android's toolkit for building screens in Kotlin code.
    // The "BOM" pins all Compose libraries to versions that are tested together.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Official Anthropic SDK, used for the "Break it down" AI feature.
    implementation("com.anthropic:anthropic-java:3.6.0")
}
