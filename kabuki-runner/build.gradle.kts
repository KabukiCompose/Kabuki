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

// DocumentationConsistencyTest reads the docs and the migration skill. Gradle
// cannot see that on its own, so after editing a document it would call the test
// task up-to-date and never run the check - a guard that never fires.
tasks.withType<Test>().configureEach {
    inputs.files(rootProject.fileTree("docs") { include("**/*.md") })
        .withPropertyName("documentation")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("settings.gradle.kts"))
        .withPropertyName("settings")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // Neither the docs nor the skill are committed yet, so both may legitimately
    // be missing - the test skips itself then, see DocumentationConsistencyTest.
    val migrationSkill = rootProject.file(".claude/skills/migrate-to-kabuki/SKILL.md")
    if (migrationSkill.isFile) {
        inputs.file(migrationSkill)
            .withPropertyName("migrationSkill")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
