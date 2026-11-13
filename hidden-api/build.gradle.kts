plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.huidoudour.hidden_api"
    //noinspection GradleDependency
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

//dependencies { }
