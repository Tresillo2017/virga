plugins {
    id("virga.android.library")
}

// Test-only module: the UI-flow harness that feature tests use. Its main source set is never shipped in the app.
dependencies {
    api(platform(libs.androidx.compose.bom))
    api(project(":core:common"))
    api(project(":core:network"))
    api(project(":core:auth"))
    api(project(":core:navigation"))
    api(project(":core:ui"))
    api(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.robolectric)

    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.foundation)
    testImplementation(libs.kotlinx.serialization.json)
}
