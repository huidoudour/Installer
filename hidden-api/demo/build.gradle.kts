plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.huidoudour.Installer.demo"
    //noinspection GradleDependency
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer.demo"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 19
        versionName = "v1.9-2606"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    debugImplementation(libs.compose.ui.tooling)
}
