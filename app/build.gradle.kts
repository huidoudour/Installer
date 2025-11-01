plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.huidoudour.Installer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
        minSdk = 28  // Android 9
        targetSdk = 34  //Android 14
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    
    // Lint 配置 - 与 app-debug 模块保持一致
    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "HardcodedText",
            "SetTextI18n",
            "DefaultLocale",
            "SdCardPath",
            "UseTomlInstead",
            "ObsoleteSdkInt",
            "UnusedResources",
            "Overdraw",
            "UselessParent",
            "Autofill",
            "FragmentTagUsage",
            "GradleDependency",
            "NewerVersionAvailable",
            "Aligned16KB"
        )
        checkOnly += setOf(
            "NotSibling",
            "DuplicateIds",
            "UnknownId"
        )
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    
    // 添加 Fragment 和 Navigation 组件
    implementation("androidx.fragment:fragment:1.6.1")
    implementation("androidx.navigation:navigation-fragment:2.7.5")
    implementation("androidx.navigation:navigation-ui:2.7.5")
    
    // 添加 Activity 组件
    implementation("androidx.activity:activity:1.8.0")
    
    // 添加 Core 组件
    implementation("androidx.core:core:1.12.0")

    // Shizuku依赖
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 统一 Kotlin 版本
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
}