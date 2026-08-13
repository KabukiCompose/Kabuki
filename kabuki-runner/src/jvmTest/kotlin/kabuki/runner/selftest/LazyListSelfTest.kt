package kabuki.runner.selftest

import kabuki.KabukiAssertionError
import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.page.onScreen
import kotlin.test.Test
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
}

/** Page object over the lazy list of [SelfTestApp]. */
class SelfTestListScreen : Screen<SelfTestListScreen>() {
    override val root = node { withTag(SelfTestTags.SCREEN) }

    val items = lazyList(SelfTestTags.LAZY_LIST) { itemType(::LazyRowItem) }
}

class LazyRowItem(scope: ListItemScope) : ListItem(scope)
