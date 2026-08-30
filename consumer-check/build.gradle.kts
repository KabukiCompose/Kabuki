plugins {
    // The oldest Kotlin Kabuki claims to support. Our artifacts are built with a
    // newer compiler but carry metadata 2.2 and stdlib 2.2 - forget
    // coreLibrariesVersion at home and half the users cannot read us.
    kotlin("jvm") version "2.4.10"
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

// The one module that ships inside an application. A runner, ui-test or JUnit
// leaking through it lands in someone's APK, and nothing else here would notice.
val productionGraph: Configuration by configurations.creating

dependencies {
    productionGraph("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
}

tasks.register("checkProductionGraph") {
    val resolved = productionGraph.incoming.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.map { it.id.componentIdentifier.displayName }
    }
    doLast {
        val forbidden = resolved.get().filter { name ->
            name.contains("ui-test") || name.contains("junit") || name.contains("kabuki-runner")
        }
        check(forbidden.isEmpty()) {
            "kabuki-semantics drags test-only artifacts into production: $forbidden"
        }
    }
}

tasks.named("check") {
    dependsOn("checkProductionGraph")
}
