plugins {
    // The oldest Kotlin Kabuki claims to support. The artifacts are built with a
    // newer compiler but carry metadata 2.2 and depend on stdlib 2.2, and this
    // build is the only thing that checks the claim: raise the compiler at home,
    // forget coreLibrariesVersion, and half the users stop being able to read us.
    kotlin("jvm") version "2.2.0"
}

dependencies {
    implementation("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
    implementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
    implementation("io.github.kabukicompose:kabuki-junit4:0.1.0-SNAPSHOT")
}

kotlin {
    compilerOptions {
        // Fail on anything the consumer's compiler cannot make sense of.
        allWarningsAsErrors.set(true)
    }
}
