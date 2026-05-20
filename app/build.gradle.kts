plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
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
        isCoreLibraryDesugaringEnabled = true
    }
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")

    // Core library desugaring for java.time on minSdk 24
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Testing - JUnit 5 + jqwik for property-based tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("net.jqwik:jqwik:1.8.5")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
