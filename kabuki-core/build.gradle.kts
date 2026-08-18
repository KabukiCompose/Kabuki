plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

description = "Node API, scoped DSL and retry for Compose Multiplatform UI tests"

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // api: ui-test types (SemanticsMatcher, ComposeUiTest) are part of public signatures
            api(libs.compose.uiTest)
            // api: the enum tagName convention is shared with production code
            api(projects.kabukiSemantics)
            // api: Composable, Color and TextStyle appear in public signatures.
            // Declared explicitly instead of relying on what semantics happens
            // to expose transitively.
            api(libs.compose.runtime)
            api(libs.compose.ui)
            // A timer off the test thread, for the stall watchdog. Compose pulls
            // coroutines in anyway - declared so the build does not rest on that.
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

// Up to 3.6.1 Espresso reaches InputManager only through the static getInstance,
// which Android 16 removed: waiting for idle builds a UiController, that builds an
// event injector, and the injector dies on the missing method. Checked in the
// bytecode of every release - 3.7.0 is the first with a getSystemService fallback.
//
// That old Espresso is OURS: it rides in on androidx.compose.ui:ui-test-android,
// which the api dependency above hands to every consumer. A constraint rather than
// a dependency - Kabuki uses no Espresso itself, it only names the broken versions.
dependencies {
    constraints {
        add("androidMainApi", libs.androidx.espressoCore.get().toString()) {
            because("up to 3.6.1 Espresso only knows InputManager.getInstance, removed in Android 16")
        }
    }
}

android {
    namespace = "kabuki.core"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
