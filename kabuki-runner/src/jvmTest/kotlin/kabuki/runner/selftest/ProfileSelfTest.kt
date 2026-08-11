package kabuki.runner.selftest

import kabuki.KabukiListener
import kabuki.OperationInfo
import kabuki.Orientation
import kabuki.Os
import kabuki.Profiles
import kabuki.SizeClass
import kabuki.StepInfo
import kabuki.TestInfo
import kabuki.assumeOs
import kabuki.detectOs
import kabuki.os
import kabuki.runner.WindowMode
import kabuki.runner.runDesktopTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-tests for profiles, OS forks, assumptions, polling and the listener SPI.
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
        assertEquals(SizeClass.Expanded, profile.sizeClass.width)
        assertEquals(SizeClass.Medium, profile.sizeClass.height)

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
    fun assumeOsSkipsOnForeignOs() = runDesktopTest(
        name = "assumeOs skip",
        window = WindowMode.Headless,
    ) {
        // The current OS is never Browser on desktop - the test must be SKIPPED,
        // not failed (verified by skipped="1" in the JUnit report)
        assumeOs(Os.Browser)
        error("unreachable: assumeOs must have skipped the test")
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

    override fun onTestFinish(test: TestInfo, result: kabuki.TestResult) {
        finishedTests += test.name
    }
}
