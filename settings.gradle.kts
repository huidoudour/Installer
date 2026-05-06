pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Installer"
include(":app")
include(":hidden-api")
include(":hidden-api:demo")
