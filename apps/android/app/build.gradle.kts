plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.apollo)
}

android {
    namespace = "com.letsmoe.otter"
    compileSdk = 37

    defaultConfig {
        // Separate from the Capacitor app (com.letsmoe.otter.player) so both install side by side.
        applicationId = "com.letsmoe.otter"
        minSdk = 26
        targetSdk = 36
        // CI derives the versionCode from the latest release, the same for its release and debug
        // builds, so users can switch between both channels (Android only refuses lower codes).
        versionCode = providers.environmentVariable("OTTER_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("OTTER_VERSION_NAME").orElse("0.0.0").get()
        // The CI run that built this APK; the updater compares it with the build number in APK names.
        buildConfigField("int", "BUILD_NUMBER", providers.environmentVariable("OTTER_BUILD_NUMBER").orElse("0").get())
        // Not 47832 (Capacitor app): both may run at once.
        buildConfigField("int", "BACKEND_PORT", "47833")
        // Stream-only port on the Wi-Fi interface, read by Chromecasts.
        buildConfigField("int", "BACKEND_LAN_PORT", "47834")
        // GitHub repository whose releases the in-app updater installs.
        buildConfigField("String", "UPDATE_REPOSITORY", "\"Letsmoe/otter\"")
    }

    // CI signs with one fixed key so released APKs install over each other; local builds keep the debug key.
    val releaseKeystore = providers.environmentVariable("OTTER_KEYSTORE_FILE").orNull
    if (releaseKeystore != null) {
        signingConfigs {
            create("otter") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("OTTER_KEYSTORE_PASSWORD").get()
                keyAlias = "otter"
                keyPassword = providers.environmentVariable("OTTER_KEYSTORE_PASSWORD").get()
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        if (releaseKeystore != null) {
            debug {
                signingConfig = signingConfigs.getByName("otter")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded personal builds: the debug key lets them install over debug builds.
            signingConfig = signingConfigs.getByName("debug")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("otter")
            }
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
        packageName.set("com.letsmoe.otter.graphql")
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
    implementation(libs.posthog.android)
    implementation(libs.appcompat)
    implementation(libs.newpipe.extractor)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.apollo.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.haze)
    implementation(libs.palette)
    implementation(libs.work.runtime)
    implementation(libs.haze.blur)
    implementation(libs.phosphor.icons)
    testImplementation(libs.junit)
}
