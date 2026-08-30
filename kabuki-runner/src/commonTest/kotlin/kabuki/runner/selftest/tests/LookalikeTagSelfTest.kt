package kabuki.runner.selftest.tests

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kabuki.KabukiAssertionError
import kabuki.runner.runKabukiTest
import kabuki.semantics.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The tag enum a minified build would rename. */
private enum class GhostTags { CARD }

/**
 * Self-test for the lookalike-tag hint: the same entry on screen under another
 * class. Either the test took the tag from the wrong screen - entry names repeat
 * across enums - or R8 renamed the class half of `EnumSimpleName.ENTRY`.
 *
 * The renamed tag is written by hand: running R8 to produce one would cost minutes
 * per assertion and prove the same thing.
 */
class LookalikeTagSelfTest {

    @Test
    fun aLookalikeTagIsCalledOut() {
        val error = assertFailsWith<KabukiAssertionError> {
            runKabukiTest(
                name = "Renamed tag enum",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                setContent {
                    BasicText(text = "card", modifier = Modifier.testTag("z3.CARD"))
                }
                node(GhostTags.CARD).assertExists()
            }
        }

        val message = error.message.orEmpty()
        assertTrue("z3.CARD" in message, "The lookalike tag is not named: $message")
        assertTrue("-keepnames class **GhostTags" in message, "The rule is not spelled out: $message")
    }

    @Test
    fun aTagThatIsOnScreenGetsNoHint() {
        val error = assertFailsWith<KabukiAssertionError> {
            runKabukiTest(
                name = "Tag present, operation fails anyway",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                setContent {
                    // The wanted tag IS here: the assertion below fails on the text.
                    BasicText(text = "card", modifier = Modifier.testTag(GhostTags.CARD))
                    BasicText(text = "other", modifier = Modifier.testTag("z3.CARD"))
                }
                node(GhostTags.CARD).assertTextContains("nothing like it")
            }
        }

        assertTrue(
            "another class" !in error.message.orEmpty(),
            "The tag was on screen - the failure is not about its name: ${error.message}",
        )
    }

    @Test
    fun aCrowdOfLookalikesIsCutShort() {
        val error = assertFailsWith<KabukiAssertionError> {
            runKabukiTest(
                name = "Many lookalikes",
                config = {
                    defaultTimeout = 300.milliseconds
                    // The dump below the hint names every tag on screen, which would
                    // count as hint content and hide the cap.
                    dumpSemanticsTreeOnFailure = false
                },
            ) {
                setContent {
                    repeat(times = 8) { index ->
                        BasicText(text = "card $index", modifier = Modifier.testTag("z$index.CARD"))
                    }
                }
                node(GhostTags.CARD).assertExists()
            }
        }

        // A hint that lists everything on screen stops being a hint.
        val listed = (0..7).count { index -> "z$index.CARD" in error.message.orEmpty() }
        assertEquals(5, listed, "Expected the list to stop at five: ${error.message}")
    }

    @Test
    fun aReadableClassNameIsNotBlamedOnR8() {
        val error = assertFailsWith<KabukiAssertionError> {
            runKabukiTest(
                name = "Another screen on display",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                setContent {
                    BasicText(text = "card", modifier = Modifier.testTag("PlaybillTags.CARD"))
                }
                node(GhostTags.CARD).assertExists()
            }
        }

        // The common case by far: entry names repeat across screens, so the screen
        // on display is simply not the expected one - a failed navigation looks
        // exactly like this.
        val message = error.message.orEmpty()
        assertTrue("PlaybillTags.CARD" in message, "The tag on screen is not named: $message")
        assertTrue("-keepnames" !in message, "A readable enum name must not be read as R8's: $message")
        assertTrue("another screen" in message, "The likely cause is not offered: $message")
    }

    @Test
    fun anUnrelatedTagGetsNoHint() {
        val error = assertFailsWith<KabukiAssertionError> {
            runKabukiTest(
                name = "Unrelated tag",
                config = { defaultTimeout = 300.milliseconds },
            ) {
                setContent {
                    BasicText(text = "other", modifier = Modifier.testTag("z3.OTHER"))
                }
                node(GhostTags.CARD).assertExists()
            }
        }

        assertTrue(
            "-keepnames" !in error.message.orEmpty(),
            "A tag with a different entry is not a lookalike at all",
        )
    }
}
