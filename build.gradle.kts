import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.binaryCompatibilityValidator)
}

// Read here: the `libs` accessor is not available inside allprojects { }.
val jvmTargetVersion = libs.versions.jvmTarget.get()
val repoUrl = "https://github.com/KabukiCompose/Kabuki"

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

    // KotlinCompilationTask, not KotlinCompile: the latter covers only JVM and
    // Android, so JS and Native targets would slip through.
    // -PallowWarnings turns this off when a toolchain update floods the build.
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(!providers.gradleProperty("allowWarnings").isPresent)
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
