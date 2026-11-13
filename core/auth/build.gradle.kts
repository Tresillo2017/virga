plugins {
    id("virga.android.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
    testImplementation(testFixtures(project(":core:network")))
}
