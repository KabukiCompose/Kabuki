plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

description = "Test tags and semantics for production Compose code - the only Kabuki artifact shipped in an app"

// Which simulator the iOS tests run on. A name rather than a UDID, so it survives
// a machine that recreated its devices; overridable because the set of simulators
// on a CI image is not ours to pin.
val simulatorDevice: String = providers.gradleProperty("kabuki.ios.device").getOrElse("iPhone 17")

kotlin {
    explicitApi()

    // The only Kabuki artifact meant for production code (test tags on Modifier).
    // Targets mirror the runners that exist today - adding a target later is a
    // compatible change for consumers, removing one is not.
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    // No iosX64: compose.ui publishes neither it nor anything else for Intel
    // simulators, so the target could not resolve its own dependency.
    iosArm64()
    iosSimulatorArm64 {
        testRuns.configureEach {
            deviceId = simulatorDevice
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api: Modifier is part of the public signatures
            api(libs.compose.ui)
        }

        // The tag format is a contract between production code and tests, and it is
        // built per platform - so it is pinned here, where every target compiles it.
        commonTest.dependencies {
            implementation(kotlin("test"))
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
