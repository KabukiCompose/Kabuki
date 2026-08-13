package kabuki.runner.selftest

import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.listener.TestResult
import kabuki.listener.TestInfo
import kabuki.listener.KabukiListener
import kabuki.page.Screen
import kabuki.page.onScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for page objects declared as `object`.
 *
 * A singleton page object is shared by every test in the JVM, so its binding to a
 * test scope must not be: the scope is kept per thread, and a binding left over
 * from a finished test is reported instead of used.
 */
class ObjectScreenSelfTest : SelfTestCase() {

    @Test
    fun anObjectScreenWorksWithoutBeingInstantiated() {
        runTest(name = "object screen") {
            // No reflection involved, unlike onScreen<T>() - which also means no
            // keep rules are needed for it under R8.
            onScreen(ObjectProbeScreen) {
                title.assertTextEquals("Kabuki SelfTest")
            }
        }
    }

    @Test
    fun anotherThreadDoesNotSeeTheBinding() {
        runTest(name = "binding is per thread") {
            onScreen(ObjectProbeScreen) { title.assertExists() }

            var failure: Throwable? = null
            val other = Thread {
                failure = runCatching { ObjectProbeScreen.title.assertExists() }.exceptionOrNull()
            }
            other.start()
            other.join()

            // Parallel tests share this object. If the binding were a plain field,
            // the other thread would happily drive THIS test's scene.
            assertTrue(
                failure is IllegalStateException,
                "Another thread must not inherit the binding, got: $failure",
            )
            assertTrue(
                "is not bound to a test scope" in failure?.message.orEmpty(),
                "The message must say what is wrong: ${failure?.message}",
            )
        }
    }

    @Test
    fun everyCallFormWorksOnTheSameObjectScreenInOneTest() {
        runTest(name = "all three forms") {
            step("reified onScreen - reads the singleton instead of calling its constructor") {
                onScreen<ObjectProbeScreen> { title.assertTextEquals("Kabuki SelfTest") }
            }
            step("onScreen with the instance") {
                onScreen(ObjectProbeScreen) { title.assertTextEquals("Kabuki SelfTest") }
            }
            step("short form - no reflection at all") {
                ObjectProbeScreen { title.assertTextEquals("Kabuki SelfTest") }
            }
        }
    }

    @Test
    fun theShortFormOutsideATestSaysWhatIsMissing() {
        // The short form takes the scope from the thread, so outside a test there
        // is nothing to take - and that must read as an explanation, not as an NPE.
        val error = assertFailsWith<IllegalStateException> {
            ObjectProbeScreen { title.assertExists() }
        }
        assertTrue(
            "needs a running Kabuki test" in error.message.orEmpty(),
            "The message must name what is missing: ${error.message}",
        )
    }

    @Test
    fun aScreenCannotBeEnteredWithAScopeFromAFinishedTest() {
        var finished: KabukiTestScope? = null
        runTest(name = "capture the scope") { finished = this }

        // Keeping the scope and reusing it later is the one way to reach a
        // finished test - and it must be refused, not acted upon.
        val error = assertFailsWith<KabukiUsageError> {
            finished!!.onScreen(ObjectProbeScreen) { title.assertExists() }
        }
        assertTrue(
            "has already finished" in error.message.orEmpty(),
            "The refusal must name the reason: ${error.message}",
        )
    }

    @Test
    fun anOperationOnAFinishedTestFailsInsteadOfHanging() {
        var finished: KabukiTestScope? = null
        runTest(name = "capture the scope for an operation") { finished = this }

        // Without the guard this does not fail - it HANGS: the scene is gone, the
        // virtual clock is stopped, and the retry loop waits for a frame forever.
        val error = assertFailsWith<KabukiUsageError> {
            finished!!.node(SelfTestTags.TITLE).assertExists()
        }
        assertTrue(
            "has already finished" in error.message.orEmpty(),
            "The refusal must name the reason: ${error.message}",
        )
    }

    @Test
    fun theCleanupRunsEvenWhenTheFinishListenerThrows() {
        // The runner reports the end once more when the block returns, but that call
        // is ignored - the outcome has already been announced. So nothing escapes
        // runTest here, and the broken listener explodes exactly once.
        runTest(
            name = "strict listener explodes on finish",
            config = {
                strictListeners = true
                listeners += ExplodingFinishListener()
            },
        ) {
            onScreen(ObjectProbeScreen) { title.assertExists() }

            // Reporting the end of the test from here, the way a runner does. With
            // strictListeners the broken listener throws out of it - and the cleanup
            // must still have happened, hence the finally.
            val info = TestInfo(name = "manual finish", profile = profile)
            assertFailsWith<IllegalStateException> { notifyTestFinish(info, TestResult.Passed) }

            // The scene is still alive here, so a missing cleanup would let this
            // operation succeed - that is what makes the check safe to run.
            val refused = assertFailsWith<KabukiUsageError> {
                ObjectProbeScreen.title.assertExists()
            }
            assertTrue(
                "is not bound to a test scope" in refused.message.orEmpty(),
                "The cleanup must have released the page object: ${refused.message}",
            )
        }
    }

    @Test
    fun theEndOfATestIsReportedExactlyOnce() {
        val counter = CountingFinishListener()

        runTest(name = "double finish", config = { listeners += counter }) {
            val info = TestInfo(name = "double finish", profile = profile)
            // A runner may report the end twice - ours does exactly that when a
            // listener throws. A reporter must still see ONE finished test.
            notifyTestFinish(info, TestResult.Passed)
            notifyTestFinish(info, TestResult.Passed)
        }

        assertEquals(1, counter.finishes, "The end of a test must reach listeners once")
    }

    @Test
    fun aSingletonIsNeverDuplicatedThroughItsPrivateConstructor() {
        // Stands for an `object` whose INSTANCE field is gone (minified away): the
        // only remaining way in is the private constructor, and taking it would
        // silently produce a second instance of a singleton.
        val error = assertFailsWith<IllegalArgumentException> {
            runTest(name = "private constructor") { onScreen<PrivateConstructorScreen> { } }
        }
        assertTrue(
            "INSTANCE" in error.message.orEmpty(),
            "The refusal must point at the missing INSTANCE field: ${error.message}",
        )
    }

    @Test
    fun theBindingIsDroppedWhenTheTestEnds() {
        runTest(name = "first test enters the object screen") {
            onScreen(ObjectProbeScreen) { title.assertExists() }
        }

        val error = assertFailsWith<IllegalStateException> {
            runTest(name = "second test forgets to enter it") {
                // Same thread, same singleton, but the previous test released it.
                // Without that release the singleton would still hold the finished
                // test's scene - and acting on it hangs rather than fails.
                ObjectProbeScreen.title.assertExists()
            }
        }
        assertTrue(
            "is not bound to a test scope" in error.message.orEmpty(),
            "The message must say what to do instead: ${error.message}",
        )
    }
}

/** Counts how many times the end of the test is announced. */
private class CountingFinishListener : KabukiListener {
    var finishes: Int = 0
        private set

    override fun onTestFinish(test: TestInfo, result: TestResult) {
        finishes++
    }
}

/** Breaks on the finish event - the moment the cleanup must survive. */
private class ExplodingFinishListener : KabukiListener {
    override fun onTestFinish(test: TestInfo, result: TestResult) {
        error("listener exploded on finish")
    }
}

/**
 * No root on purpose: entering it never touches the scene, so this test stays safe
 * to run even when the guard it checks is broken.
 */
private object RootlessObjectScreen : Screen<RootlessObjectScreen>() {
    val title = node(SelfTestTags.TITLE)
}

/** A singleton without an INSTANCE field - what R8 can leave behind. */
private class PrivateConstructorScreen private constructor() : Screen<PrivateConstructorScreen>() {
    val title = node(SelfTestTags.TITLE)
}

/**
 * Deliberately private: a page object declared next to its test is package-private
 * in the bytecode, so this also covers the library reaching a screen it is not
 * technically allowed to see.
 */
private object ObjectProbeScreen : Screen<ObjectProbeScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val title = node(SelfTestTags.TITLE)
}
