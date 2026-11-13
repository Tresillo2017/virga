plugins {
    id("virga.android.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
