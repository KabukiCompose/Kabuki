// A build of its OWN, deliberately not part of the main one: it must resolve
// Kabuki the way a stranger does - from a repository, with its own Kotlin.
// Plugins resolve separately from dependencies, and AGP lives in google().
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

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

// A third one, for the artifacts nothing else here touches: the AARs. Only on a
// JDK that AGP accepts - the matrix also runs this build on 11 to prove our
// bytecode is readable there, and AGP refuses to start below 17.
if (JavaVersion.current() >= JavaVersion.VERSION_17) {
    include(":android-consumer")
}
