plugins {
    id("virga.android.wear")
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.compose.viewmodel)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Declares ComponentActivity so the Robolectric flow test can host Compose in this application module.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:wearsync"))
    implementation(project(":feature:buildsdata"))
    testImplementation(testFixtures(project(":core:network")))
}

// Release signing comes from the environment (CI secrets or a local shell), never from the repo.
val releaseKeystoreFile: String? = System.getenv("VIRGA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    defaultConfig {
        // CI passes these from the release tag, the same as for the phone app. A watch APK uploaded beside the
        // phone APK in one Play release needs its own, distinct versionCode.
        (findProperty("virgaVersionName") as String?)?.let { versionName = it }
        (findProperty("virgaVersionCode") as String?)?.toIntOrNull()?.let { versionCode = it }
    }
    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("VIRGA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIRGA_KEY_ALIAS")
                keyPassword = System.getenv("VIRGA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // R8 shrinks and obfuscates release code. Keep rules live in proguard-rules.pro.
            // Signed only when the VIRGA_KEYSTORE_* environment variables are set; otherwise the output is unsigned.
            if (releaseKeystoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
