pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Virga"

include(":app")
include(":core:common")
include(":core:network")
include(":core:auth")
include(":core:navigation")
include(":core:ui")
include(":core:testing")
include(":feature:account")
include(":feature:home")
include(":feature:workers")
include(":feature:builds")
include(":feature:pages")
include(":feature:domains")
include(":feature:r2")
include(":feature:d1")
include(":feature:kv")
