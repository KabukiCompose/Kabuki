package kabuki.runner.selftest.tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import kabuki.KabukiAssertionError
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.SelfTestTags
import kabuki.runner.selftest.app.SelfTestTint
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Self-test for the parts of the addressing DSL that had no test at all:
 * `UiNodeCollection.at`, `NodeMatcherBuilder.matching` and `withContentDescription`.
 * Every one of them is public API a user can reach on day one, and a silent break
 * in any of them looks like "the element is not on screen".
 *
 * The probe is the pair of panels from [SelfTestApp]: two nodes carrying the SAME
 * tag, so index access and matcher combination have something to actually resolve.
 */
@OptIn(ExperimentalTestApi::class)
class MatcherBuilderSelfTest : SelfTestCase() {

    @Test
    fun atAddressesNodesByPositionInTreeOrder() {
        runTest(name = "collection index") {
            val labels = nodeAll(SelfTestTags.PANEL_LABEL)
            labels.assertCountEquals(2)

            // Both panels are identical apart from their text, so this pins down
            // that at(index) picks a SPECIFIC node - not simply the first match.
            labels.at(0).assertTextEquals("left")
            labels.at(1).assertTextEquals("right")
            labels.first().assertTextEquals("left")
        }
    }

    @Test
    fun anIndexOutsideTheCollectionFailsNamingTheIndex() {
        runTest(name = "index out of range") {
            val error = assertFailsWith<KabukiAssertionError> {
                nodeAll(SelfTestTags.PANEL_LABEL)
                    .at(5)
                    .withTimeout(200.milliseconds)
                    .assertExists()
            }
            // "not found" and "there is no fifth one" are different mistakes, and
            // the message has to say which of the two happened.
            assertTrue(
                error.message.orEmpty().contains("[#5]"),
                "The failure must name the index that was addressed, was: ${error.message}",
            )
        }
    }

    @Test
    fun matchingNarrowsTheSearchWithARawMatcher() {
        runTest(name = "raw matcher in a builder") {
            // The tag alone matches both panels - onNode on an ambiguous matcher
            // fails - so this passes only if the raw matcher is really combined in.
            node {
                withTag(SelfTestTags.PANEL_LABEL)
                matching(hasText("right"))
            }.assertTextEquals("right")
        }
    }

    @Test
    fun withContentDescriptionFindsTheNodeByItsAccessibilityLabel() {
        runTest(name = "content description search") {
            // Landing on the right node is the assertion: the tint belongs to the
            // described element only.
            node { withContentDescription("A favourite marker") }
                .assertTintColor(SelfTestTint)
        }
    }

    @Test
    fun anEmptyMatcherBlockIsRefusedAtDeclarationTime() {
        runTest(name = "empty matcher") {
            // A matcher with no conditions would match the whole tree. Failing here
            // - while building - beats failing later with "several nodes matched".
            val error = assertFailsWith<IllegalStateException> {
                node { }
            }
            assertTrue(
                error.message.orEmpty().contains("empty"),
                "The message must say the matcher is empty, was: ${error.message}",
            )
        }
    }
}
