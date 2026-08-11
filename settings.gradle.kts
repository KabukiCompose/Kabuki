rootProject.name = "Kabuki"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(
    ":kabuki-semantics",
    ":kabuki-core",
    ":kabuki-runner",
    ":kabuki-junit4",
)

include(":samples:sample")
