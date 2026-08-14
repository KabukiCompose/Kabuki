package kabuki.runner.selftest.tests

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import kabuki.KabukiAssertionError
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.LEGACY_STRING_TAG
import kabuki.runner.selftest.app.SelfTestSection
import kabuki.runner.selftest.app.SelfTestTags
import kabuki.runner.selftest.app.SelfTestTint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Self-test for the UiNode operations that the other self-tests never touch.
 *
 * The focus is on operations carrying OUR logic - retry, per-node timeouts,
 * matching rules - rather than one-line delegations to compose-ui-test. That is
 * where a real defect can hide.
 */
class UiNodeOperationsSelfTest : SelfTestCase() {

    @Test
    fun textInputReplaceAndClear() = runTest(name = "typeText / replaceText / clearText") { app ->
        step("Type text into the field") {
            node(SelfTestTags.INPUT).typeText("first")
            assertEquals("first", app.input)
        }

        step("replaceText overwrites instead of appending") {
            node(SelfTestTags.INPUT).replaceText("second")
            assertEquals("second", app.input, "replaceText must overwrite, not append")
        }

        step("clearText empties the field") {
            node(SelfTestTags.INPUT).clearText()
            assertEquals("", app.input)
        }
    }

    @Test
    fun plainStringTagsStillWork() = runTest(name = "string tag") {
        step("A node addressed by a raw string tag is found") {
            node(LEGACY_STRING_TAG).assertExists()
        }
    }

    @Test
    fun withTimeoutOverridesTheConfiguredTimeout() = runTest(
        name = "withTimeout",
        config = { defaultTimeout = 10.seconds },
    ) {
        step("A per-node timeout wins over the config and fails fast") {
            val started = TimeSource.Monotonic.markNow()
            assertFailsWith<KabukiAssertionError> {
                node("selftest_does_not_exist")
                    .withTimeout(300.milliseconds)
                    .assertIsDisplayed()
            }
            val elapsed = started.elapsedNow()
            assertTrue(
                elapsed < 5.seconds,
                "withTimeout(300ms) must not wait for the configured 10s, took $elapsed",
            )
        }
    }

    @Test
    fun rawGivesAccessToTheUnderlyingInteraction() = runTest(name = "raw escape hatch") {
        step("The raw interaction is usable and returns a value") {
            node(SelfTestTags.COUNTER_BUTTON).click()

            val text = node(SelfTestTags.COUNTER_VALUE).raw { interaction ->
                interaction.assertTextEquals("Counter: 1")   // plain compose-ui-test call
                interaction.fetchSemanticsNode().config.toString()
            }
            assertTrue(text.isNotEmpty(), "raw must return the value produced by the block")
        }
    }

    @Test
    fun textStyleReadsTheStyleOfTheNode() = runTest(name = "textStyle") {
        step("The heading reports a real font size") {
            val style = node(SelfTestTags.TITLE).textStyle()
            assertTrue(
                style.fontSize.value > 0f,
                "textStyle must report the actual style, got ${style.fontSize}",
            )
        }
    }

    @Test
    fun clickActionAssertionsDistinguishButtonsFromText() = runTest(name = "has/hasNo click action") {
        step("A button has a click action") {
            node(SelfTestTags.COUNTER_BUTTON).assertHasClickAction()
        }

        step("Plain text has none") {
            node(SelfTestTags.COUNTER_VALUE).assertHasNoClickAction()
        }

        step("And the assertions are not interchangeable") {
            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.COUNTER_VALUE)
                    .withTimeout(300.milliseconds)
                    .assertHasClickAction()
            }
        }
    }

    @Test
    fun focusAssertionsFollowTheFocusedField() = runTest(name = "focused / not focused") {
        step("Nothing is focused before the first interaction") {
            node(SelfTestTags.INPUT).assertIsNotFocused()
        }

        step("Clicking the field focuses it") {
            node(SelfTestTags.INPUT).click()
            node(SelfTestTags.INPUT).assertIsFocused()
        }
    }

    @Test
    fun enabledAssertionsDistinguishDisabledButtons() = runTest(name = "enabled / not enabled") {
        step("A normal button is enabled, a disabled one is not") {
            node(SelfTestTags.COUNTER_BUTTON).assertIsEnabled()
            node(SelfTestTags.DISABLED_BUTTON).assertIsNotEnabled()
        }

        step("The assertions are not interchangeable") {
            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.DISABLED_BUTTON)
                    .withTimeout(300.milliseconds)
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun toggleAssertionsFollowTheCheckbox() = runTest(name = "on / off") { app ->
        step("The checkbox starts off") {
            node(SelfTestTags.CHECKBOX).assertIsOff()
        }

        step("Clicking it turns it on") {
            node(SelfTestTags.CHECKBOX).click()
            node(SelfTestTags.CHECKBOX).assertIsOn()
            assertEquals(true, app.checked, "The state must follow the click")
        }
    }

    @Test
    fun selectionAssertionsFollowTheSelectableNode() = runTest(name = "selected / not selected") {
        step("The row starts unselected") {
            node(SelfTestTags.SELECTABLE).assertIsNotSelected()
        }

        step("Clicking selects it") {
            node(SelfTestTags.SELECTABLE).click()
            node(SelfTestTags.SELECTABLE).assertIsSelected()
        }
    }

    @Test
    fun contentDescriptionAndTintAreReadFromSemantics() = runTest(name = "description / tint") {
        step("The content description is matched by substring, like assertTextContains") {
            node(SelfTestTags.TINTED).assertContentDescriptionContains("favourite")
        }

        step("Exact matching is still available") {
            node(SelfTestTags.TINTED)
                .assertContentDescriptionContains("A favourite marker", substring = false)
        }

        step("Exact matching on a partial value fails") {
            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TINTED)
                    .withTimeout(300.milliseconds)
                    .assertContentDescriptionContains("favourite", substring = false)
            }
        }

        step("The tint colour published by kabuki-semantics is asserted") {
            node(SelfTestTags.TINTED).assertTintColor(SelfTestTint)
        }

        step("A wrong colour fails") {
            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.TINTED)
                    .withTimeout(300.milliseconds)
                    .assertTintColor(Color.Red)
            }
        }
    }

    @Test
    fun doubleAndLongClickAreDistinctGestures() = runTest(name = "doubleClick / longClick") { app ->
        step("A double click is recognised as such, not as two clicks") {
            node(SelfTestTags.GESTURES).doubleClick()
            node(SelfTestTags.GESTURES_LOG).assertTextEquals("double")
            assertEquals("double", app.lastGesture)
        }

        step("A long click is a separate gesture") {
            node(SelfTestTags.GESTURES).longClick()
            node(SelfTestTags.GESTURES_LOG).assertTextEquals("long")
        }
    }

    @Test
    fun scrollToBringsAnOffScreenNodeIntoView() = runTest(
        name = "scrollTo / not displayed",
        // Only the scrollable parts: on a short screen the whole app does not fit,
        // and a scrollable container living below the fold cannot be scrolled into
        // view at all.
        section = SelfTestSection.Scrolling,
    ) {
        step("The far block exists but is not displayed") {
            node(SelfTestTags.FAR_BLOCK).assertExists()
            node(SelfTestTags.FAR_BLOCK).assertIsNotDisplayed()
        }

        step("scrollTo brings it into view") {
            node(SelfTestTags.FAR_BLOCK).scrollTo()
            node(SelfTestTags.FAR_BLOCK).assertIsDisplayed()
        }
    }

    @Test
    fun scrollToIndexReachesAnItemFarDownTheLazyList() = runTest(
        name = "scrollToIndex",
        section = SelfTestSection.Scrolling,
    ) {
        step("An item near the end is not composed yet") {
            node(SelfTestTags.LAZY_LIST).assertIsDisplayed()
            nodeWithText(FAR_LAZY_ITEM, substring = false).assertDoesNotExist()
        }

        step("scrollToIndex composes and shows it") {
            node(SelfTestTags.LAZY_LIST).scrollToIndex(FAR_LAZY_INDEX)
            nodeWithText(FAR_LAZY_ITEM, substring = false).assertIsDisplayed()
        }
    }
}

/**
 * An index deep enough that the item is not composed until something scrolls to
 * it - asserting on the item itself is what makes the test fail if the scroll
 * silently does nothing.
 */
private const val FAR_LAZY_INDEX = 25
private const val FAR_LAZY_ITEM = "lazy item $FAR_LAZY_INDEX"
