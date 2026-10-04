package kabuki.runner.selftest.tests

import kabuki.Orientation
import kabuki.Os
import kabuki.Profiles
import kabuki.HeightClass
import kabuki.WidthClass
import kabuki.detectOs
import kabuki.heightClass
import kabuki.listener.KabukiListener
import kabuki.listener.OperationInfo
import kabuki.listener.StepInfo
import kabuki.listener.TestInfo
import kabuki.listener.TestResult
import kabuki.os
import kabuki.widthClass
import kabuki.runner.WindowMode
import kabuki.runner.runDesktopTest
import kabuki.runner.selftest.app.SelfTestApp
import kabuki.runner.selftest.app.SelfTestAppState
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-tests for profiles, OS forks, polling and the listener SPI.
 *
 * The `onlyOn*` family has its own suite in OnlyOnSelfTest - it is common code
 * and must run on a device too.
 */
class ProfileSelfTest {

    @Test
    fun profileDrivesSceneSizeAndClasses() = runDesktopTest(
        name = "Profile: SmallHd scene",
        profile = Profiles.Desktop.SmallHd,
        window = WindowMode.Headless,
    ) {
        assertEquals(detectOs(), profile.os)
        assertEquals(Orientation.Landscape, profile.orientation)
        // 1366x768: Large by width since the desktop buckets were split out of
        // Expanded, Medium by height.
        assertEquals(WidthClass.Large, widthClass)
        assertEquals(HeightClass.Medium, heightClass)

        // The headless scene actually gets the profile size: the app can read it
        val state = SelfTestAppState()
        setContent { SelfTestApp(state) }
        node(SelfTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun osForkRunsTheMatchingBranch() = runDesktopTest(
        name = "os() fork",
        window = WindowMode.Headless,
    ) {
        var branch: Os? = null
        os(
            windows = { branch = Os.Windows },
            linux = { branch = Os.Linux },
            macos = { branch = Os.MacOs },
        )
        assertEquals(detectOs(), branch)
    }

    @Test
    fun osForkWithoutABranchForTheCurrentOsFails() = runDesktopTest(
        name = "os() fork without a branch",
        window = WindowMode.Headless,
    ) {
        // The dangerous failure mode is the quiet one: a fork that silently does
        // nothing skips the checks inside it and leaves the test green.
        val error = assertFailsWith<IllegalStateException> {
            os(web = { error("desktop is never Browser") })
        }

        assertTrue(
            "has no branch" in error.message.orEmpty(),
            "The fork must say which OS it has no branch for: ${error.message}",
        )
    }

    @Test
    fun pollingIntervalIsRespected() = runDesktopTest(
        name = "Polling interval",
        window = WindowMode.Headless,
        config = { pollingInterval = 25.milliseconds },
    ) {
        val state = SelfTestAppState()
        setContent { SelfTestApp(state) }

        // Appears after delay(1.5s) on the virtual clock - retry with a polling
        // pause still waits it out
        node(SelfTestTags.DELAYED_BLOCK).assertIsDisplayed()
    }

    @Test
    fun listenersReceiveLifecycleEvents() {
        val recorder = RecordingListener()
        runDesktopTest(
            name = "Listener SPI",
            window = WindowMode.Headless,
            config = {
                listeners.clear()
                listeners += recorder
            },
        ) {
            val state = SelfTestAppState()
            setContent { SelfTestApp(state) }
            step("Click the counter") {
                node(SelfTestTags.COUNTER_BUTTON).click()
                node(SelfTestTags.COUNTER_VALUE).assertTextContains("Counter: 1")
            }
            log("custom message")
        }

        assertEquals(listOf("Listener SPI"), recorder.startedTests)
        assertEquals(listOf("1"), recorder.startedSteps.map { it.label })
        assertTrue(recorder.operations.any { it.operation.startsWith("click") })
        assertTrue(recorder.operations.any { it.operation.startsWith("assertText") })
        assertTrue("custom message" in recorder.messages)
        assertEquals(listOf("Listener SPI"), recorder.finishedTests)
    }
}

private class RecordingListener : KabukiListener {
    val startedTests = mutableListOf<String>()
    val finishedTests = mutableListOf<String>()
    val startedSteps = mutableListOf<StepInfo>()
    val operations = mutableListOf<OperationInfo>()
    val messages = mutableListOf<String>()

    override fun onTestStart(test: TestInfo) {
        startedTests += test.name
    }

    override fun onStepStart(step: StepInfo) {
        startedSteps += step
    }

    override fun onOperationStart(operation: OperationInfo) {
        operations += operation
    }

    override fun onLog(message: String) {
        messages += message
    }

    override fun onTestFinish(test: TestInfo, result: TestResult) {
        finishedTests += test.name
    }
}
