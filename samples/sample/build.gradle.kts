import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// R8 check: the shared tests against a MINIFIED release build. A flag, so the
// everyday run stays on debug.
//   ./gradlew :samples:sample:connectedAndroidTest -Pkabuki.minifiedTests
val minifiedTests = providers.gradleProperty("kabuki.minifiedTests").isPresent

kotlin {

    androidTarget {
        // Instrumented tests live in the "test" source set tree together with
        // jvmTest, so the shared tests in commonTest run on a device too.
        // Without this they stay in a separate "instrumentedTest" tree and
        // cannot share a parent source set with jvmTest.
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // The only Kabuki artifact linked into production code: enum test tags
            implementation(projects.kabukiSemantics)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activityCompose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        // Shared UI tests: page objects, scenarios and the tests themselves are
        // written ONCE in commonTest and run on desktop (jvmTest) and on a
        // device/emulator (androidInstrumentedTest). Only the entry point is
        // platform-specific (expect runTheaterTest / actual per runner).
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Declared HERE, not per platform: the shared tests use the DSL and
            // the runner directly, and KMP dependencies flow down, never up -
            // putting this in jvmTest only would leave the IDE unable to
            // resolve commonTest even though Gradle compiles it fine.
            // api(kabuki-core) inside the runner brings the DSL along.
            implementation(projects.kabukiRunner)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.testRunner)
            // No Espresso line on purpose: the sample is a consumer, and the proof
            // that kabuki-core's constraint lifts the version by itself.
        }
    }
}

android {
    namespace = "kabuki.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.github.kabukicompose.sample"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = minifiedTests
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The test apk is minified too and takes its own rules - the app's
            // proguardFiles do not apply to it.
            testProguardFiles("proguard-test-rules.pro")
            // The debug key: the point is to run R8 output on a device, not to ship it.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Which variant the instrumented tests are built against.
    testBuildType = if (minifiedTests) "release" else "debug"
}

androidComponents {
    // The sample has no local unit tests - all tests live in commonTest and
    // run via jvmTest + androidInstrumentedTest. Disabling the unitTest
    // component also removes the dead "run as unit test" entry the IDE offers
    // on shared test classes (it fails with "Class not found" otherwise).
    beforeVariants { it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false }
}

dependencies {
    // Provides the empty ComponentActivity that runComposeUiTest launches on Android
    debugImplementation(libs.androidx.composeUiTestManifest)
    if (minifiedTests) {
        // The same activity for the minified run - without it there is nothing to
        // launch and every test reports "No compose hierarchies found".
        releaseImplementation(libs.androidx.composeUiTestManifest)
    }
}

compose.desktop {
    application {
        mainClass = "kabuki.sample.MainKt"
    }
}
