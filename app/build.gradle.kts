import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

val appName = "Installer"
val baseVersionCode = 8000
val baseVersionName = "26.10.24"

fun getBuildDateTime(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmm"))

fun git(vararg args: String): String =
    try {
        providers.exec { commandLine(listOf("git") + args) }
            .standardOutput.asText.get().trim()
    } catch (_: Exception) {
        ""
    }

val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 0
val commitHash = git("rev-parse", "--short=7", "HEAD").ifEmpty { getBuildDateTime() }
val versionTxt = if (git("tag", "--contains", "HEAD").isEmpty()) ".ci" else ""

val appVersionCode = baseVersionCode + commitCount
val appVersionName = "$baseVersionName.$commitCount$versionTxt.$commitHash"

//   .\gradlew.bat assembleDebug -PaltApp   → dev.huidoudour.installer
val altApp = providers.gradleProperty("altApp").isPresent

val buildBanner = tasks.register("buildBanner") {
    description = "执行时间戳"
    val requested = gradle.startParameter.taskNames
        .map { it.substringAfterLast(':') }
        .filter { it.startsWith("assemble") || it.startsWith("bundle") || it == "build" }
        .distinct()
    doLast {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        println(">>> app-[${requested.joinToString(", ")}] @ $ts <<<")
    }
}

rootProject.allprojects.forEach { p ->
    p.tasks.configureEach {
        if (path != buildBanner.get().path) mustRunAfter(buildBanner)
    }
}

tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
    dependsOn(buildBanner)
    val taskName = name
    val versionName = appVersionName
    val versionCode = appVersionCode
    doLast {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        println(">>> app-[$taskName]: $versionName($versionCode) @ $timestamp <<<")
    }
}

val generateGitLog = tasks.register("generateGitLog") {
    description = "ChangeLog"
    val outputFile = layout.projectDirectory.file("src/main/assets/git_commits.md")
    val text = try {
        providers.exec {
            commandLine("git", "log", "--pretty=format:### %s%n%b%n")
            workingDir(rootProject.projectDir)
        }.standardOutput.asText.get()
    } catch (_: Exception) {
        "# 暂无提交记录"
    }
    doLast {
        val file = outputFile.asFile
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }
}

tasks.named("preBuild") {
    dependsOn(generateGitLog)
}

android {
    namespace = "dev.huidoudour.installer"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = if (altApp) "dev.huidoudour.installer" else "io.github.huidoudour.Installer"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        // 供应用内“检测新版本”使用：仅基础版本号（如 26.10.14），
        // 与 GitHub Release 的 tag（v26.10.14）对齐比较；VERSION_NAME 里混了提交数不能直接切。
        buildConfigField("String", "BASE_VERSION_NAME", "\"$baseVersionName\"")

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                abiFilters += setOf( "arm64-v8a" , "x86_64" )
            }
        }
    }

    val useSignKey = rootProject.hasProperty("storeFile") &&
        rootProject.hasProperty("storePassword") &&
        rootProject.hasProperty("keyAlias") &&
        rootProject.hasProperty("keyPassword")

    signingConfigs {
        if (useSignKey) {
            create("sign_key") {
                storeFile = file(rootProject.property("storeFile") as String)
                storePassword = rootProject.property("storePassword") as String
                keyAlias = rootProject.property("keyAlias") as String
                keyPassword = rootProject.property("keyPassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            optimization {
                enable = true
            }
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en-rUS",
            "ja-rJP",
            "ru-rRU",
            "zh-rCN",
            "zh-rHK",
            "zh-rTW"
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "HardcodedText",           // 允许硬编码文本(调试阶段)
            "SetTextI18n",             // 允许文本拼接
            "DefaultLocale",           // 允许默认Locale
            "SdCardPath",              // 允许硬编码路径(系统工具)
            "UseTomlInstead",          // 暂不强制使用版本目录
            "ObsoleteSdkInt",          // 允许过时的SDK版本检查
            "UnusedResources",         // 允许未使用资源(可能被动态引用)
            "Overdraw",                // 允许过度绘制
            "UselessParent",           // 允许冗余父布局
            "Autofill",                // 不强制自动填充提示
            "FragmentTagUsage",        // 允许使用fragment标签
            "GradleDependency",        // 不强制更新依赖
            "NewerVersionAvailable"    // 不强制更新到最新版本
        )
        checkOnly += setOf(
            "NotSibling",              // 必须检查布局引用错误
            "DuplicateIds",            // 必须检查重复ID
            "UnknownId"                // 必须检查未知ID引用
        )
    }
}

@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { variant ->
        val buildTypeLabel = variant.buildType.orEmpty().replaceFirstChar { it.uppercaseChar() }
        // 新包名产物加后缀，避免与默认包名同名覆盖
        val suffix = if (altApp) "-alt" else ""
        val outputName = "$appName-$appVersionName-$appVersionCode$suffix-$buildTypeLabel.apk"
        variant.outputs.forEach { output ->
            output.outputFileName.set(outputName)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.constraintlayout)
    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.security.crypto)
    implementation(libs.material.kolor)
    implementation(libs.navigation.ui)
    implementation(libs.compose.ui)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.apksig)

    debugImplementation(libs.compose.ui.tooling.preview)

    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("io.github.iamr0s:Dhizuku-API:2.6.0")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.palette:palette:1.1.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("dev.rikka.shizuku:api:13.1.5")

    implementation(libs.profileinstaller)
    compileOnly(project(":hidden-api"))
    implementation(project(":lib-mtprovider"))
    implementation(project(":lib-terminal"))
    "baselineProfile"(project(":baselineprofile"))

    val localFile = file("libs/android.aar")
    if (localFile.exists()) {
        debugImplementation(files(localFile))
        releaseImplementation("androidx.compose.ui:ui-tooling-preview:1.13.0-beta02")
    }
}
