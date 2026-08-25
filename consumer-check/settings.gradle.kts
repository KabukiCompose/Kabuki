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
