plugins {
    id("com.android.application")
}

android {
    namespace = "com.allan.tileblast"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.allan.tileblast"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore automatically
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // TODO: Configure release signing for Play Store distribution
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.core:core:1.12.0")
}
