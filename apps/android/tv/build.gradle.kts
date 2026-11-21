plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Android TV app: its own APK with D-pad screens, sharing :core with the phone app.
android {
    namespace = "com.letsmoe.otter.tv"
    compileSdk = 37

    defaultConfig {
        // Separate from the phone app (com.letsmoe.otter), so both can be sideloaded independently.
        applicationId = "com.letsmoe.otter.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("OTTER_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("OTTER_VERSION_NAME").orElse("0.0.0").get()
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            // The x86_64 backend is only for emulators.
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    // libotter.so is the Go backend executable, not a library: it must be
    // extracted to nativeLibraryDir to be runnable.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.phosphor.icons)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
