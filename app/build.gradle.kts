plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.varunkb.learnmateai"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.varunkb.learnmateai"
        // ExecuTorch's Android library needs a recent minSdk; 28 covers every phone that can run a 1B model anyway.
        minSdk = 28
        targetSdk = 37

        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The ExecuTorch native libraries are packaged for 64-bit Arm phones only, which keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // Shrunk and optimised build for the submission APK. Signed with the debug key so that
            // it installs directly on a test phone; use your own keystore for a store release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room exports its schema as JSON so future migrations can be reviewed and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Room (offline database). Kotlin extensions are part of room-runtime since Room 2.7.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // On-device Llama runtime (ExecuTorch). Pulls in fbjni and soloader transitively.
    implementation(libs.executorch.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
