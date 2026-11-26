// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://maven.rikka.cn/") }
    }
}

subprojects {
    layout.buildDirectory.value(
        rootProject.layout.buildDirectory.get().dir(project.name)
    )
}