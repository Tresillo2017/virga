plugins {
    id("virga.android.library")
}

android {
    testFixtures {
        enable = true
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.ktor.client.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.koin.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.mock)
}

dependencies {
    testFixturesImplementation(libs.ktor.client.mock)
    // The Compose compiler plugin also runs on the testFixtures variant and needs the runtime on its classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime)
}
