plugins {
    id("virga.android.application")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.datastore.preferences)

    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:wearsync"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":feature:account"))
    implementation(project(":feature:home"))
    implementation(project(":feature:workers"))
    implementation(project(":feature:builds"))
    implementation(project(":feature:pages"))
    implementation(project(":feature:domains"))
    implementation(project(":feature:r2"))
    implementation(project(":feature:d1"))
    implementation(project(":feature:kv"))
}

// Release signing comes from the environment (CI secrets or a local shell), never from the repo.
val releaseKeystoreFile: String? = System.getenv("VIRGA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    defaultConfig {
        // CI passes these from the release tag: -PvirgaVersionName=1.2.3 -PvirgaVersionCode=42
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
