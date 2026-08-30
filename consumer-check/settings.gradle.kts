// A build of its OWN, deliberately not part of the main one: it must resolve
// Kabuki the way a stranger does - from a repository, with its own Kotlin.
rootProject.name = "kabuki-consumer-check"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

// A second consumer, of the shape most people actually use: a Kotlin
// Multiplatform project that declares Kabuki in commonTest.
include(":kmp-consumer")
