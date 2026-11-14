import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

// The JDK running Gradle is 27, which Kotlin does not target yet. Pin build-logic to 17, matching the modules.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("virgaAndroidApplication") {
            id = "virga.android.application"
            implementationClass = "com.tomasps.virga.buildlogic.VirgaAndroidApplicationPlugin"
        }
        register("virgaAndroidWear") {
            id = "virga.android.wear"
            implementationClass = "com.tomasps.virga.buildlogic.VirgaAndroidWearPlugin"
        }
        register("virgaAndroidLibrary") {
            id = "virga.android.library"
            implementationClass = "com.tomasps.virga.buildlogic.VirgaAndroidLibraryPlugin"
        }
    }
}
