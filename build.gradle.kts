// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
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

// 使用新的API注册clean任务
tasks.register<Delete>("clean") {
    description = "Deletes the build directory."
    delete(project.layout.buildDirectory)
}