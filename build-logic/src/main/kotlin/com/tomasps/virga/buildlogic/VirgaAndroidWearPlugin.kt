package com.tomasps.virga.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for the :wear module. Identical to the phone application (same application id and namespace, which the
 * Data Layer requires so the watch app receives the phone app's items), except for the Wear OS 4 minimum SDK (API 33).
 */
class VirgaAndroidWearPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(VirgaAndroidApplicationPlugin::class.java)
            extensions.configure<ApplicationExtension> {
                defaultConfig.minSdk = WEAR_MIN_SDK
            }
        }
    }

    private companion object {
        const val WEAR_MIN_SDK = 33
    }
}
