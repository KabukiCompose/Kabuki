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
