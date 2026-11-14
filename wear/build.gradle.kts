plugins {
    id("virga.android.wear")
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.play.services.wearable)

    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:wearsync"))
    implementation(project(":feature:buildsdata"))
    testImplementation(testFixtures(project(":core:network")))
}
