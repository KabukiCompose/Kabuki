package kabuki.runner.selftest.tests

import kabuki.KabukiUsageError
import kabuki.runner.runKabukiTest
import kabuki.runner.selftest.app.SelfTestTags
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the empty scene: no `setContent`, so nothing is composed.
 *
 * Android only, and not for convenience: a desktop scene exists from the moment
 * the runner creates it - measured, the same test on the JVM fails with an
 * ordinary "node not found". Built on [runKabukiTest] rather than the shared base
 * class, which always installs the app.
 *
 * Covers the slow path, where Kabuki did not fill the scene and waits the timeout
 * out before ruling. The fast path needs a misconfigured APK to reproduce.
 */
class MissingHierarchySelfTest {

    @Test
    fun absenceProvesNothingInAnEmptyScene() {
        val error = assertFailsWith<KabukiUsageError> {
            runKabukiTest(
                name = "Absence in an empty scene",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                node(SelfTestTags.COUNTER_BUTTON).assertDoesNotExist()
            }
        }

        assertTrue(
            "Nothing is composed on screen" in error.message.orEmpty(),
            "Unexpected message: ${error.message}",
        )
        // The point of the message: the two build-script causes Compose never names.
        assertTrue(
            "ui-test-manifest" in error.message.orEmpty() && "targetSdk" in error.message.orEmpty(),
            "The message adds nothing to what Compose already said: ${error.message}",
        )
    }

    /** Also green through the retry verdict - see [aCountOfZeroIsNoProofEither]. */
    @Test
    fun notBeingDisplayedIsNoProofEither() {
        val error = assertFailsWith<KabukiUsageError> {
            runKabukiTest(
                name = "Not displayed in an empty scene",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                node(SelfTestTags.COUNTER_BUTTON).assertIsNotDisplayed()
            }
        }

        assertTrue(
            "Nothing is composed on screen" in error.message.orEmpty(),
            "Unexpected message: ${error.message}",
        )
    }

    /**
     * Green through the retry verdict, not through a check of its own: `count()`
     * demands a root and throws before anything is compared. Both this test and
     * the one above guard the behaviour, whoever provides it - only
     * `assertDoesNotExist` needed a check of ours, and that one is mutation-proven.
     */
    @Test
    fun aCountOfZeroIsNoProofEither() {
        val error = assertFailsWith<KabukiUsageError> {
            runKabukiTest(
                name = "Zero count in an empty scene",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                nodeAll(SelfTestTags.COUNTER_BUTTON).assertCountEquals(0)
            }
        }

        assertTrue(
            "Nothing is composed on screen" in error.message.orEmpty(),
            "Unexpected message: ${error.message}",
        )
    }

    @Test
    fun aPositiveAssertionFailsTheSameWay() {
        val error = assertFailsWith<KabukiUsageError> {
            runKabukiTest(
                name = "Presence in an empty scene",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                node(SelfTestTags.COUNTER_BUTTON).assertExists()
            }
        }

        // One type and one message whichever way the test asked.
        assertTrue(
            "Nothing is composed on screen" in error.message.orEmpty(),
            "Unexpected message: ${error.message}",
        )
    }
}
