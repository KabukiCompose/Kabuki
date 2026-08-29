plugins {
    // No version here: the root of this build already put Kotlin 2.2.0 on the
    // classpath, and that is the version both consumers must use.
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            // The only Kabuki artifact that ships inside an application.
            implementation("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
        }
        // The shape a real project uses. Does NOT prove it: with one target,
        // moving this to jvmTest keeps the build green (measured 2026-08-29) -
        // commonTest compiles inside jvmTest and inherits its dependencies.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
        }
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// Not checked here: metadata variants. One target means KGP skips
// compileKotlinMetadata - commonMain compiles inside the only platform. The
// second target Kabuki publishes is Android, and it brings AGP's JDK 17 floor,
// the thing the JDK 11 cell checks against. Metadata that is too new is caught
// anyway - it lives in the class files the plain JVM module reads.
// When iOS or wasm arrives, add it here and restore the dependency.
