// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.0" apply false
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
    delete(project.layout.buildDirectory)
}