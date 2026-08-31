plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "consumer.android"
    // DELIBERATELY below Kabuki's own compileSdk. An AAR can demand that its
    // consumers compile against the same level or higher, and AGP writes that
    // demand into the aar metadata by itself - this module is what notices.
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            allWarningsAsErrors.set(true)
            // Matches compileOptions above - the two must agree, and 11 is the
            // level Kabuki promises its consumers.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    // The AAR, not the jar: Android artifacts carry metadata, consumer proguard
    // rules and a constraint that the JVM ones do not, and nothing else here
    // looks at them.
    implementation("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
    androidTestImplementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
}

// assemble builds the AAR but not the instrumented APK, and the APK is the half
// that proves kabuki-runner resolves. Nothing is executed - no emulator here.
tasks.named("check") {
    dependsOn("assembleDebugAndroidTest")
}
