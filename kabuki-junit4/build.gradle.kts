plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

description = "Kabuki on top of an existing ComposeTestRule - for adopting it one test at a time"

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.kabukiCore)
            // api: ComposeTestRule is part of the public signatures
            api(libs.compose.uiTestJunit4)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.foundation)
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "kabuki.junit4"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
