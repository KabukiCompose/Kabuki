plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

description = "Test tags and semantics for production Compose code - the only Kabuki artifact shipped in an app"

kotlin {
    explicitApi()

    // The only Kabuki artifact meant for production code (test tags on Modifier).
    // Targets mirror the runners that exist today - adding a target later is a
    // compatible change for consumers, removing one is not.
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // api: Modifier is part of the public signatures
            api(libs.compose.ui)
        }
    }
}

android {
    namespace = "kabuki.semantics"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
