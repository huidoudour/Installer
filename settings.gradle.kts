pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Installer"
include(":app")
include(":hidden-api")
include(":mt-provider")
include(":baselineprofile")
include(":lib-terminal")
