// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // KSP line matches Kotlin 1.9.24. Do not upgrade Kotlin/AGP/Gradle.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
