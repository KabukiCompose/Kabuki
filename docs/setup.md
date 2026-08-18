# Setup

Nothing is published to Maven Central yet, so Kabuki is installed locally:

```bash
git clone https://github.com/KabukiCompose/Kabuki
cd Kabuki
./gradlew publishToMavenLocal
```

That puts every module below into `~/.m2` at version `0.1.0-SNAPSHOT`.

## Requirements

| | |
|---|---|
| Kotlin | 2.2 or newer |
| Compose Multiplatform | built against 1.11; older versions are untested |
| Bytecode | Java 11, so any JDK from 11 up can read the artifacts |
| Android | minSdk 24 |

## Artifacts

| artifact | source set | what for |
|---|---|---|
| `io.github.kabukicompose:kabuki-semantics` | `commonMain` | test tags, the only part linked into the app |
| `io.github.kabukicompose:kabuki-runner` | `commonTest` | runners and `runKabukiTest`; brings the DSL along |
| `io.github.kabukicompose:kabuki-junit4` | test source set | Kabuki over an existing `ComposeTestRule` |
| `io.github.kabukicompose:kabuki-core` | test source set | the DSL alone, for a runner of your own |

`kabuki-runner` depends on `kabuki-core` as `api`, so the DSL needs no separate
line.

## A multiplatform project

Add `mavenLocal()` wherever your repositories are declared - in most projects
that is `dependencyResolutionManagement` in `settings.gradle.kts`, not the
module. Then:

```kotlin
kotlin {
    androidTarget {
        // Puts the instrumented tests in the same source set tree as jvmTest,
        // which is what lets commonTest run on a device. See the pitfalls below.
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kabukicompose:kabuki-semantics:0.1.0-SNAPSHOT")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.kabukicompose:kabuki-runner:0.1.0-SNAPSHOT")
        }
        androidInstrumentedTest.dependencies {
            implementation("androidx.test:runner:1.7.0")
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Not the same thing as defaultConfig.targetSdk, and skipping it costs a day.
    // Use the value your app targets.
    testOptions {
        targetSdk = 37
    }
}

dependencies {
    // The empty activity runComposeUiTest launches on Android
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.1")
}
```

The imports for the Android target block:

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
```

A desktop-only project needs neither the `androidTarget` block nor anything
under `android { }`.

## Running

```bash
./gradlew jvmTest              # desktop, headless, no emulator
./gradlew connectedAndroidTest  # device or emulator
```

A desktop test can also mirror the scene into a real window, which is worth
watching while writing one test and unbearable across a suite. The switch is a
system property:

```bash
./gradlew jvmTest -Dkabuki.window=false   # never open a window
./gradlew jvmTest -Dkabuki.window=true    # always open one
```

Without it: headless on CI (`CI` / `GITLAB_CI` in the environment), visible
locally. A test that asks for `WindowMode.Visible` explicitly is unaffected.

## An existing Android project

`kabuki-junit4` runs Kabuki on top of a `ComposeTestRule` that somebody else
owns, so new tests can sit next to the old ones:

```kotlin
class LegacyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stillWorks() {
        composeRule.setContent { App() }

        composeRule.kabuki(name = "Legacy screen") {
            node("legacy_button").click()
            node("legacy_counter").assertTextContains("Clicks: 1")
        }
    }
}
```

String tags work here on purpose: a project mid-migration has its production
code tagged the old way. Enum tags from `kabuki-semantics` work in the same test.

There are three forms, and they are not equivalent:

| form | test boundaries |
|---|---|
| `composeRule.kabuki(name) { }` | reported: start, finish, result |
| `KabukiRule` as a `@get:Rule` | reported, and every test in the class gets it |
| `KabukiInterop` with `composeRule.kabukiScope()` | not reported - the scope has no idea where the test ends |

A bare scope also never releases its page objects: they stay bound until the next
scope on that thread replaces them. Object page objects entered by the short form
(`PlaybillScreen { }`) are the ones to watch - between tests that form can still
reach the previous scope. `KabukiRule` and the block form both end their scope and
have neither problem.

The mixin is the flattest to read (`onScreen<T>` right next to the legacy code)
and the only one that cannot report the test itself. Pick it when the existing
base class already owns the reporting.

`KabukiRule` also takes the test name from JUnit and releases page objects at the
end. Declare it INSIDE the Compose rule - `@get:Rule(order = 0)` for the Compose
one, `order = 1` for Kabuki - so the scene is still alive when the finish event
fires and a listener taking a screenshot on failure finds something there.

## Pitfalls

All of these were paid for in real days of debugging. The ones Kabuki could take
off your hands, it does - those are marked below; the rest live in the build
script, where a library has no reach.

### `Unresolved reference` in commonTest while Gradle builds fine

Declare `kabuki-runner` in `commonTest`, never in `jvmTest` or
`androidInstrumentedTest`. KMP dependencies flow down, never up, so a
platform-level declaration leaves `commonTest` without the API. Gradle does not
notice - there is no metadata compilation for `commonTest`, the shared code is
only ever compiled as part of a platform. The IDE is right, and it is not a
stale cache.

### The shared tests do not run on the device

Without `instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)`
the instrumented tests land in a separate `instrumentedTest` tree that shares no
parent with `commonTest`. The run succeeds and simply contains none of the
shared tests.

### Nothing is composed on screen (Kabuki says so)

Two unrelated causes produce Compose's `No compose hierarchies found in the app`.

1. `ui-test-manifest` is missing - there is no activity to launch.
2. The instrumented APK's `targetSdk` equals `minSdk`. A library module has no
   `targetSdk` of its own, but its test APK does, and it defaults to `minSdk`.
   Android 16 keeps such an app in compatibility mode and refuses to bring the
   test activity up. Look for `TargetSdkVersion=` in logcat, then set
   `android { testOptions { targetSdk = ... } }`.

Measured on API 36: 60 of 93 tests failing and a 17-minute run, against 93/93 in
3 minutes once the `targetSdk` was set.

**Kabuki names both causes for you** and, once the test has called `setContent`,
stops immediately instead of spending the whole timeout waiting for a hierarchy
that was never built.

### A green run that verified nothing (Kabuki catches it)

When the hierarchy is missing, every test that asserts the ABSENCE of something
passes - nothing is there, so nothing was found. With a completely broken
environment 32 of 93 tests were green.

No assertion agrees that something is absent from a scene that composed nothing:
`assertDoesNotExist` checks the hierarchy before it passes, and the rest -
`assertIsNotDisplayed`, `assertCountEquals(0)` - fail on their own and are turned
into the same verdict. The general lesson still stands for CI: the pass ratio
alone is a poor signal, and a sudden change in how many tests actually asserted
anything says more.

### Espresso on a modern device (Kabuki handles it)

Espresso arrives transitively at 3.5.0 - through `ui-test`, which Kabuki itself
hands you - and calls by reflection a method Android 16 removed. Every test then
dies inside `Espresso.onIdle` within seconds, with
`NoSuchMethodException: android.hardware.input.InputManager.getInstance`.

`kabuki-core` carries a dependency constraint that lifts Espresso to 3.7.0, so
this needs no line in your build. 3.7.0 is where it was fixed - every release up
to and including 3.6.1 reaches `InputManager` only through the removed static
method - so pinning anything older yourself brings the failure back.

### Tests against a minified build

Kabuki builds a tag as `EnumSimpleName.ENTRY`, and R8 renames the enum, so a
minified app publishes `a.SCREEN` while the test still looks for
`PlaybillTags.SCREEN`. Keep the names of your tag enums:

```proguard
-keepnames class com.myapp.**Tags
```

One more rule belongs here if your tests use `assumeOs` / `assumeSizeClass`:
skipping is built by reflection over the test framework's own exception, and a
renamed class turns every skip into a failure. `kabuki-core` ships the keep rules
for it, so this is a thing to know rather than to write.

Kabuki does not impose the tag rule on everyone: which enums exist is an
application-level decision, and a blanket `-keepnames` would bloat every app
that never tests a minified build. It does recognise the symptom - when the same
entry is on screen under another class name, the failure says so and quotes the
rule. Page object rules are already shipped in `kabuki-core`'s consumer rules.

### The IDE offers "run as unit test" and it fails

Shared test classes are offered as local unit tests, where they cannot run.
Optional, and it removes the dead entry:

```kotlin
import com.android.build.api.variant.HostTestBuilder

androidComponents {
    beforeVariants { it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false }
}
```

## Reference configuration

Everything above is taken from `samples/sample/build.gradle.kts`, which runs the
same tests on desktop and on a device.

The shortest test there is
`samples/sample/src/commonTest/kotlin/kabuki/sample/tests/PlainE2eTest.kt`:
three lines, no page objects, two asynchronous screens and not a single
`waitUntil`.
