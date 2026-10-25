plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// CI builds number themselves from the workflow run so every release is an upgrade;
// local builds keep a fixed low number.
val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val releaseKeystore = System.getenv("SIGNING_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
val releasePassword = System.getenv("SIGNING_PASSWORD")

android {
    namespace = "com.wakecoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wakecoach"
        minSdk = 26
        targetSdk = 35
        versionCode = if (ciRun != null) 100 + ciRun else 2
        versionName = if (ciRun != null) "0.4.$ciRun" else "0.4.0"
    }

    signingConfigs {
        if (releaseKeystore != null && releasePassword != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releasePassword
                keyAlias = "wakecoach"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the release key (local builds) fall back to the debug key so the APK still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Lint findings are reported, not fatal: a missed alarm is worse than a lint warning.
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
