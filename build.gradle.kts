import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.detekt) apply false
}

// Read here: the `libs` accessor is not available inside allprojects { }.
val jvmTargetVersion = libs.versions.jvmTarget.get()
val kotlinLanguageVersion = KotlinVersion.fromVersion(libs.versions.kotlinLanguage.get())
val kotlinCoreLibrariesVersion = libs.versions.kotlinCoreLibraries.get()
val repoUrl = "https://github.com/KabukiCompose/Kabuki"

subprojects {
    apply(plugin = "dev.detekt")

    // BLOCKS the build on any finding, down to a single over-long line.
    // Thresholds live in config/detekt/detekt.yml, each with its reason.
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }

    // Compose has its own ways to get things wrong - modifier order, state
    // hoisting, a composable that returns a value - and detekt knows none of them
    // by itself.
    dependencies.add("detektPlugins", rootProject.libs.composeRules.detekt)
}

allprojects {
    // Namespace verified through the KabukiCompose GitHub org - no domain needed.
    group = "io.github.kabukicompose"
    version = "0.1.0-SNAPSHOT"

    // Keyed by project PATH, not name: same-named modules in different groups
    // would otherwise share one build directory.
    layout.buildDirectory = File(rootProject.projectDir, "build/" + path.removePrefix(":").replace(":", "-"))

    // A jar pulled out of context still says what it is, and license scanners
    // do not have to rely on the POM.
    tasks.withType<Jar>().configureEach {
        metaInf.from(rootProject.file("LICENSE"))
    }

    // Desktop tests can mirror the scene into a real window. Off unless asked for -
    // they cost most of the suite's run time:
    //   ./gradlew jvmTest -Pkabuki.window
    tasks.withType<Test>().configureEach {
        systemProperty("kabuki.window", providers.gradleProperty("kabuki.window").isPresent)
    }

    // KotlinCompilationTask, not KotlinCompile: the latter covers only JVM and
    // Android, so JS and Native targets would slip through.
    // -PallowWarnings turns this off when a toolchain update floods the build.
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(!providers.gradleProperty("allowWarnings").isPresent)

            // languageVersion decides the metadata version of the artifacts, so
            // building with a newer compiler silently locks out consumers still
            // on an older Kotlin. apiVersion follows it to keep us off stdlib
            // that those consumers do not have yet. Applied to the samples too -
            // they are the closest thing we have to a consumer.
            languageVersion.set(kotlinLanguageVersion)
            apiVersion.set(kotlinLanguageVersion)
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            // Bytecode level, decoupled from the JDK running the build - otherwise
            // artifacts inherit it and consumers on an older JDK get
            // UnsupportedClassVersionError. -Xjdk-release additionally forbids
            // calling JDK APIs newer than the target, which jvmTarget alone allows.
            jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
            freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")

            // Real Java default methods instead of a DefaultImpls class: that class
            // is public binary API, and in Java it forces an implementor of an SPI
            // like KabukiListener to override every method. Needs API 24+, our minSdk.
            freeCompilerArgs.add("-jvm-default=no-compatibility")
        }
    }

    // No --release here: AGP forbids it, and there are no Java sources anyway.
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = jvmTargetVersion
        targetCompatibility = jvmTargetVersion
    }

    // The other half of languageVersion: the compiler stamps our metadata, this
    // decides which stdlib we drag into the consumer's graph. Both must name the
    // same Kotlin, or the pair is only half a promise.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinProjectExtension> {
            coreLibrariesVersion = kotlinCoreLibrariesVersion
        }
    }

    // Reactive, so modules that never publish (samples) stay untouched.
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom { kabukiMetadata(project, repoUrl) }
            }
        }
    }
}

apiValidation {
    // Public API is tracked for library modules only
    ignoredProjects += listOf("sample")
}

/**
 * POM fields Maven Central requires - a bundle without them is rejected, and
 * plain maven-publish fills in none of them. The description comes from
 * `project.description`, set by each module.
 */
fun MavenPom.kabukiMetadata(project: Project, repoUrl: String) {
    name.set(project.name)
    description.set(project.provider { project.description ?: "UI tests for Compose Multiplatform" })
    url.set(repoUrl)

    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("ArtyomZhukov")
            name.set("Artem Zhukov")
            email.set("zhukovartemvl@gmail.com")
        }
    }
    scm {
        url.set(repoUrl)
        connection.set("scm:git:$repoUrl.git")
        developerConnection.set("scm:git:ssh://git@github.com/KabukiCompose/Kabuki.git")
    }
}
