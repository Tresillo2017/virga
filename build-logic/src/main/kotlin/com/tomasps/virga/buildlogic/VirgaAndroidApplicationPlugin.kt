package com.tomasps.virga.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Convention for the :app module. */
class VirgaAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            extensions.configure<ApplicationExtension> {
                namespace = VirgaAndroidConfig.NAMESPACE_ROOT
                configureVirgaApplication()
                sourceSets.getByName("test").resources.srcDir(rootProject.file(ROBOLECTRIC_CONFIG_DIR))
                defaultConfig {
                    applicationId = VirgaAndroidConfig.NAMESPACE_ROOT
                    targetSdk = VirgaAndroidConfig.TARGET_SDK
                    versionCode = 1
                    versionName = "0.1.0"
                }
            }
            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            configureVirgaDependencies()
        }
    }
}
