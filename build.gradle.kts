plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Pins Kotlin Gradle Plugin 2.4.21 on the root classpath; AGP 9.5.1 otherwise drags in 2.2.10.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kover)
}

// Aggregate Kover coverage across every module: ./gradlew koverHtmlReport koverXmlReport
// Only leaf projects are modules; ":core" and ":feature" are grouping paths with no build file.
dependencies {
    subprojects.filter { it.childProjects.isEmpty() }.forEach { add("kover", it) }
}
