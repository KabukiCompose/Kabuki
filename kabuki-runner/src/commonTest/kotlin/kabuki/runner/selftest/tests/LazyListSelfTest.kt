package kabuki.runner.selftest.tests

import androidx.compose.ui.semantics.getOrNull
import kabuki.KabukiAssertionError
import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.page.onScreen
import kabuki.runner.selftest.SelfTestCase
import kabuki.runner.selftest.app.LAZY_ITEM_COUNT
import kabuki.runner.selftest.app.SelfTestSection
import kabuki.runner.selftest.app.SelfTestTags
import kabuki.semantics.LazyListItemIndexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Self-test for lazy list assertions.
 *
 * [kabuki.page.LazyList.assertLengthEquals] reads the length published by production
 * code, so it has to fail when the numbers disagree. A happy-path test alone
 * would keep passing even if the assertion stopped asserting altogether - hence
 * the negative case below.
 */
class LazyListSelfTest : SelfTestCase() {

    @Test
    fun lengthComesFromThePublishedValueNotFromVisibleItems() = runTest(
        name = "assertLengthEquals",
        section = SelfTestSection.Scrolling,
        // Keeps the negative case below from waiting out the default 5 seconds.
        config = { defaultTimeout = 1.seconds },
    ) {
        step("The full length is asserted even though most items are not composed") {
            onScreen<SelfTestListScreen> {
                items.assertLengthEquals(LAZY_ITEM_COUNT)

                val visible = items.visibleItems().count()
                assertTrue(
                    visible < LAZY_ITEM_COUNT,
                    "The test is pointless if all $LAZY_ITEM_COUNT items are composed: " +
                        "counting visible ones would pass too. Visible: $visible",
                )
            }
        }

        step("A wrong length fails") {
            onScreen<SelfTestListScreen> {
                assertFailsWith<KabukiAssertionError> {
                    items.assertLengthEquals(LAZY_ITEM_COUNT - 1)
                }
            }
        }
    }

    @Test
    fun itemsAreAddressedByIndexAcrossTheWholeList() = runTest(
        name = "itemAt / itemNodeAt",
        // The list has to be ON SCREEN for an item to count as displayed: with the
        // whole app composed it sits below the fold on a phone in landscape.
        section = SelfTestSection.Scrolling,
        config = { defaultTimeout = 1.seconds },
    ) {
        step("An item far down the list is reached by index") {
            onScreen<SelfTestListScreen> {
                items.itemNodeAt(LAZY_ITEM_COUNT - 1).assertDoesNotExist()
                items.itemAt<LazyRowItem>(LAZY_ITEM_COUNT - 1) {
                    node.assertIsDisplayed()
                }
            }
        }

        step("An index past the end does not exist") {
            onScreen<SelfTestListScreen> {
                items.itemNodeAt(LAZY_ITEM_COUNT).assertDoesNotExist()
            }
        }
    }

    @Test
    fun anItemIsFoundByItsContentAndThenAddressedByIndex() = runTest(
        name = "itemWhere",
        section = SelfTestSection.Scrolling,
    ) {
        val far = LAZY_ITEM_COUNT - 3
        step("An item far below the fold is found by its text") {
            onScreen<SelfTestListScreen> {
                // Not composed until the search scrolls to it.
                items.itemNodeAt(far).assertDoesNotExist()

                assertEquals(far, items.indexOfItemWhere { withText("lazy item $far") })
            }
        }

        step("The found item is then used by index") {
            onScreen<SelfTestListScreen> {
                items.itemWhere<LazyRowItem>({ withText("lazy item $far") }) {
                    assertEquals(far, index)
                    node.assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun theUntypedFormReturnsTheItemNodeItself() = runTest(
        name = "itemNodeWhere",
        section = SelfTestSection.Scrolling,
    ) {
        val far = LAZY_ITEM_COUNT - 5
        onScreen<SelfTestListScreen> {
            // The index, not "some node is displayed": the first item is on screen
            // anyway, so a search that ignored the matcher would pass. Asserting text
            // would not work either - the item is a plain Box, and a non-merging
            // container never carries the text of its children.
            val index = items.itemNodeWhere { withText("lazy item $far") }
                .read("index") { it.fetchSemanticsNode().config.getOrNull(LazyListItemIndexKey) }

            assertEquals(far, index)
        }
    }

    @Test
    fun severalMatchesResolveToTheFirstItem() = runTest(
        name = "itemWhere with several matches",
        section = SelfTestSection.Scrolling,
    ) {
        onScreen<SelfTestListScreen> {
            // Every item matches, so several are composed at once - the search must
            // answer with the FIRST one instead of failing on the ambiguity.
            assertEquals(0, items.indexOfItemWhere { withText("lazy item", substring = true) })
        }
    }

    @Test
    fun aSearchAlsoFindsItemsAboveTheCurrentPosition() = runTest(
        name = "itemWhere after scrolling away",
        section = SelfTestSection.Scrolling,
    ) {
        onScreen<SelfTestListScreen> {
            items.scrollToIndex(LAZY_ITEM_COUNT - 1)

            // The list is at its end now, and the match is far ABOVE - a search that
            // only scrolled forward would never reach it.
            assertEquals(2, items.indexOfItemWhere { withText("lazy item 2") })
        }
    }

    @Test
    fun aContentSearchThatFindsNothingSaysWhatIsMissing() = runTest(
        name = "itemWhere misses",
        section = SelfTestSection.Scrolling,
        config = { defaultTimeout = 1.seconds },
    ) {
        val error = assertFailsWith<KabukiAssertionError> {
            onScreen<SelfTestListScreen> {
                items.indexOfItemWhere { withText("no such item") }
            }
        }

        // The likely cause is unmarked items, so the message names the modifier.
        assertTrue(
            "testListItem" in error.message.orEmpty(),
            "The failure must point at the marking: ${error.message}",
        )
    }
}

/** Page object over the lazy list of [SelfTestApp]. */
class SelfTestListScreen : Screen<SelfTestListScreen>() {
    override val root = node { withTag(SelfTestTags.SCREEN) }

    val items = lazyList(SelfTestTags.LAZY_LIST) { itemType(::LazyRowItem) }
}

class LazyRowItem(scope: ListItemScope) : ListItem(scope)
