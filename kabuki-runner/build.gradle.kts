import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

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

        // Puts instrumented tests in the "test" tree, so the commonTest self-tests
        // reach a device too. By default they sit in a tree of their own.
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.kabukiCore)
        }

        // Self-tests: written once, run on both platforms. The test app they drive
        // needs the UI toolkit; the DSL comes from the module itself.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.material3)
            implementation(libs.compose.foundation)
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
            // The native skiko belongs here - self-tests do render for real.
            implementation(compose.desktop.currentOs)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.testRunner)
            // Pulls Espresso up from the transitive 3.5.0 - see the catalog comment.
            implementation(libs.androidx.espressoCore)
        }
    }
}

android {
    namespace = "kabuki.runner"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A library has no targetSdk, but its instrumented APK does - and it defaults to
    // minSdk, which stops Android 16 from ever launching the test Activity.
    testOptions {
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
    }
}

androidComponents {
    // No local unit tests here - the shared self-tests run via jvmTest and
    // androidInstrumentedTest. Leaving it on gives the IDE a dead run entry.
    beforeVariants { it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false }
}

dependencies {
    // The empty ComponentActivity that runComposeUiTest launches on Android
    debugImplementation(libs.androidx.composeUiTestManifest)
}

// DocumentationConsistencyTest reads these files. Undeclared, they would leave the
// task up-to-date after every doc edit - a guard that never fires.
tasks.withType<Test>().configureEach {
    inputs.files(rootProject.fileTree("docs") { include("**/*.md") })
        .withPropertyName("documentation")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("settings.gradle.kts"))
        .withPropertyName("settings")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // Not committed yet, so it may be missing - the test skips itself then.
    val migrationSkill = rootProject.file(".claude/skills/migrate-to-kabuki/SKILL.md")
    if (migrationSkill.isFile) {
        inputs.file(migrationSkill)
            .withPropertyName("migrationSkill")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
