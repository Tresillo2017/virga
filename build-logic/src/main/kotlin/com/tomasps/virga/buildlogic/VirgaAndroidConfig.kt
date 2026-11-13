package com.tomasps.virga.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType

/** Shared Android constants for every Virga module. Keep in sync with docs/architecture.md. */
internal object VirgaAndroidConfig {
    const val NAMESPACE_ROOT = "com.tomasps.virga"
    const val COMPILE_SDK = 37
    const val MIN_SDK = 28
    const val TARGET_SDK = 36
    const val JVM_TARGET = 17
    val JAVA_VERSION: JavaVersion = JavaVersion.toVersion(JVM_TARGET)
}

internal fun Project.versionCatalog(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Namespace for a module path, e.g. ":core:common" -> "com.tomasps.virga.core.common". */
internal fun Project.defaultNamespace(): String =
    VirgaAndroidConfig.NAMESPACE_ROOT + path.replace(":", ".")

internal fun LibraryExtension.configureVirgaLibrary() {
    compileSdk = VirgaAndroidConfig.COMPILE_SDK
    defaultConfig.minSdk = VirgaAndroidConfig.MIN_SDK
    compileOptions {
        sourceCompatibility = VirgaAndroidConfig.JAVA_VERSION
        targetCompatibility = VirgaAndroidConfig.JAVA_VERSION
    }
    buildFeatures.compose = true
    testOptions.unitTests.isIncludeAndroidResources = true
}

internal fun ApplicationExtension.configureVirgaApplication() {
    compileSdk = VirgaAndroidConfig.COMPILE_SDK
    defaultConfig.minSdk = VirgaAndroidConfig.MIN_SDK
    compileOptions {
        sourceCompatibility = VirgaAndroidConfig.JAVA_VERSION
        targetCompatibility = VirgaAndroidConfig.JAVA_VERSION
    }
    buildFeatures.compose = true
    testOptions.unitTests.isIncludeAndroidResources = true
}

/** Module that holds the shared UI-flow test harness. It is never given the harness on its own classpath. */
internal const val TESTING_MODULE_PATH = ":core:testing"

/** Directory with the one robolectric.properties every module's unit tests read (pins the Robolectric SDK level). */
internal const val ROBOLECTRIC_CONFIG_DIR = "config/robolectric"

/**
 * Test dependencies available to every module, plus the Compose BOM so Compose artifacts need no versions.
 * Every module except [TESTING_MODULE_PATH] also gets the UI-flow harness, so any feature can write a screen test.
 */
internal fun Project.configureVirgaDependencies() {
    val catalog = versionCatalog()
    val handler = dependencies
    handler.add("implementation", handler.platform(catalog.findLibrary("androidx-compose-bom").get()))
    handler.add("implementation", catalog.findLibrary("androidx-compose-runtime").get())
    handler.add("testImplementation", catalog.findLibrary("junit").get())
    handler.add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
    handler.add("testImplementation", catalog.findLibrary("turbine").get())
    handler.add("testImplementation", catalog.findLibrary("ktor-client-mock").get())
    handler.add("testImplementation", catalog.findLibrary("robolectric").get())
    handler.add("testImplementation", catalog.findLibrary("androidx-compose-ui-test-junit4").get())
    handler.add("testImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
    handler.add("testImplementation", catalog.findLibrary("androidx-test-espresso-core").get())
    if (path != TESTING_MODULE_PATH) {
        handler.add("testImplementation", handler.project(mapOf("path" to TESTING_MODULE_PATH)))
    }
    tasks.withType(Test::class.java).configureEach {
        // Robolectric reflects into JDK internals that the JDK 27 module system closes by default.
        jvmArgs(ROBOLECTRIC_JDK_OPENS.map { "--add-opens=$it=ALL-UNNAMED" })
    }
}

/** JDK packages Robolectric needs open to unnamed modules at runtime. */
internal val ROBOLECTRIC_JDK_OPENS = listOf("java.base/jdk.internal.access")
