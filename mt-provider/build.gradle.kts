plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "cn.mt2.datafilesprovider"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.L-JINBIN"
                artifactId = "mt-data-files-provider"
                version = "1.0.0"
            }
        }
    }
}
