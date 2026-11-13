package com.tomasps.virga.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Convention for every :core:* and :feature:* module. */
class VirgaAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            extensions.configure<LibraryExtension> {
                namespace = defaultNamespace()
                configureVirgaLibrary()
                sourceSets.getByName("test").resources.srcDir(rootProject.file(ROBOLECTRIC_CONFIG_DIR))
            }
            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            configureVirgaDependencies()
        }
    }
}
