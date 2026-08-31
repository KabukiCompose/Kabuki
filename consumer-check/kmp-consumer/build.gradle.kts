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
        // moving this to jvmTest keeps the build green - commonTest compiles
        // inside jvmTest and inherits its dependencies.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
        }
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// One target on purpose: adding Android here would drag AGP in, and AGP refuses
// the JDK 11 this build also runs on. The AARs are checked by :android-consumer
// instead. The cost is that metadata variants stay unchecked - with a single
// target KGP skips compileKotlinMetadata. Add a target here when iOS or wasm
// arrives; that is what makes the metadata compile real.
