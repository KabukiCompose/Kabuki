plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

description = "Desktop and Android runners for Kabuki: one test, both platforms"

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.kabukiCore)
        }

        jvmMain.dependencies {
            // Window/application for the visible window. MUST stay `common`:
            // `desktop.currentOs` resolves on the BUILD machine and bakes the
            // builder's OS-specific skiko (e.g. desktop-jvm-windows-x64) into
            // the POM every consumer downloads.
            implementation(libs.compose.desktop)
            implementation(libs.kotlinx.coroutinesCore)
        }

        jvmTest.dependencies {
            // Self-tests: library features exercised on a minimal in-module app
            // (virtual clock, background threads, error messages, visible window).
            // The native skiko belongs here - self-tests do render for real.
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
        }
    }
}

android {
    namespace = "kabuki.runner"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
