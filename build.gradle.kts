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

// 统一构建输出：所有模块的构建产物集中到根项目 build/<模块名> 下，
// 避免各模块目录内各自散落 build 目录（rootProject 的默认值即为根 build，无需重设）
subprojects {
    layout.buildDirectory.value(
        rootProject.layout.buildDirectory.get().dir(project.name)
    )
}