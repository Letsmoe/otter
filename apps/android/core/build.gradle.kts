plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.apollo)
}

// Shared by the phone app (:app) and the TV app (:tv): the GraphQL client, the embedded backend
// with its plugin bundles, profiles and the stores that follow the backend. No screens.
android {
    namespace = "com.letsmoe.otter.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // Not 47832 (Capacitor app): both may run at once.
        buildConfigField("int", "BACKEND_PORT", "47833")
        // Stream-only port on the Wi-Fi interface, read by Chromecasts.
        buildConfigField("int", "BACKEND_LAN_PORT", "47834")
        // Loopback only: the phone's encoder that draws captions and hints into the TV's picture.
        buildConfigField("int", "OVERLAY_PORT", "47835")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    api(libs.apollo.runtime)
    api(libs.posthog.android)
    api(libs.androidx.core)
    api(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
}
