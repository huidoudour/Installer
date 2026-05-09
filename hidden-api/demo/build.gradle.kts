plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
}
