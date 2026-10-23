plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.apollo)
}

android {
    namespace = "dev.neoworks.otter"
    compileSdk = 37

    defaultConfig {
        // Separate from the Capacitor app (dev.neoworks.otter.player) so both install side by side.
        applicationId = "dev.neoworks.otter.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Not 47832 (Capacitor app): both may run at once.
        buildConfigField("int", "BACKEND_PORT", "47833")
        // Stream-only port on the Wi-Fi interface, read by Chromecasts.
        buildConfigField("int", "BACKEND_LAN_PORT", "47834")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded personal builds: the debug key lets them install over debug builds.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        // Media3 classes touch android.* statics that the unit test stub jar leaves empty.
        unitTests.isReturnDefaultValues = true
    }

    // libotter.so is the Go backend executable, not a library: it must be
    // extracted to nativeLibraryDir to be runnable.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Profile avatars are shared with the Capacitor app (static/avatars/*.webp → assets/avatars).
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory("../../mobile/static")
    }
}

apollo {
    service("otter") {
        packageName.set("dev.neoworks.otter.graphql")
        schemaFiles.from(file("../../backend/internal/api/graphql/schema/otter.graphql"))
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.cast)
    implementation(libs.appcompat)
    implementation(libs.newpipe.extractor)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.apollo.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.haze)
    implementation(libs.palette)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)
    implementation(libs.phosphor.icons)
    testImplementation(libs.junit)
}
