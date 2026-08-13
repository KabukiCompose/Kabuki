package kabuki.runner.selftest

import kabuki.KabukiAssertionError
import kabuki.page.Component
import kabuki.page.Screen
import kabuki.page.onScreen
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Self-test for component scoping: a component searches its nodes INSIDE its own
 * root, so two identical components on one screen address their own elements.
 *
 * The probe is two panels that are identical inside - same label, group and button
 * tags - distinguished only by a parameter on the panel's own tag.
 */
class ComponentScopingSelfTest : SelfTestCase() {

    @Test
    fun identicalComponentsAddressTheirOwnElements() {
        runTest(name = "two identical components") {
            onScreen(PanelsScreen()) {
                left.label.assertTextEquals("left")
                right.label.assertTextEquals("right")
            }
        }
    }

    @Test
    fun theSameTagsReallyAreAmbiguousWithoutScoping() {
        runTest(name = "ambiguity is real") {
            // Guards the test above from passing for the wrong reason: if the inner
            // tags were unique, scoping would be untested and this count would be 1.
            nodeAll(SelfTestTags.PANEL_LABEL).assertCountEquals(2)

            assertFailsWith<KabukiAssertionError> {
                node(SelfTestTags.PANEL_LABEL).withTimeout(200.milliseconds).assertExists()
            }
        }
    }

    @Test
    fun scopingComposesThroughNestedComponents() {
        runTest(name = "nested components") {
            onScreen(PanelsScreen()) {
                // button -> its group -> its panel: three levels, and the button tag
                // exists four times on screen (two panels x two nestings).
                left.group.button.assertTextContains("Tap left")
                right.group.button.assertTextContains("Tap right")
            }
        }
    }

    @Test
    fun collectionsAreScopedToo() {
        runTest(name = "nodeAll inside a component") {
            onScreen(PanelsScreen()) {
                // The same tag exists twice on screen - once per panel.
                left.labels.assertCountEquals(1)
                right.labels.assertCountEquals(1)
            }
            nodeAll(SelfTestTags.PANEL_LABEL).assertCountEquals(2)
        }
    }

    @Test
    fun lazyListsAreScopedToo() {
        runTest(name = "lazyList inside a component") {
            onScreen(PanelsScreen()) {
                // Both panels hold a list with the same tag AND items with the same
                // index, so an unscoped list matcher resolves to two nodes at once.
                left.list.itemNodeAt(0).assertTextContains("left item 0")
                right.list.itemNodeAt(0).assertTextContains("right item 0")
            }
        }
    }

    @Test
    fun aCopiedNodeKeepsItsScope() {
        runTest(name = "withTimeout and merged keep the scope") {
            onScreen(PanelsScreen()) {
                // withTimeout and .merged rebuild the node - if the copy lost its
                // host, the label would be searched globally and match twice.
                left.label.withTimeout(1.seconds).assertTextEquals("left")
                right.label.merged.assertExists()
            }
        }
    }

    @Test
    fun aComponentRootIsNotScopedByItself() {
        runTest(name = "root does not scope itself") {
            onScreen(PanelsScreen()) {
                // Would need an ancestor matching itself, and resolve to nothing.
                left.root.assertExists()
            }
        }
    }

    @Test
    fun theOrderOfDeclarationsDoesNotMatter() {
        runTest(name = "node declared before root") {
            // The scope is resolved at operation time, not at declaration time -
            // otherwise this label would be searched globally, match twice and fail.
            onScreen(ReversedOrderScreen()) {
                panel.label.assertTextEquals("right")
            }
        }
    }

    @Test
    fun aScreenWithoutARootIsEnteredWithoutWaiting() {
        runTest(name = "screen without root") {
            onScreen(NoRootScreen()) {
                title.assertTextEquals("Kabuki SelfTest")
            }
        }
    }
}

private class PanelsScreen : Screen<PanelsScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val left = component { PanelComponent("left") }
    val right = component { PanelComponent("right") }
}

private class PanelComponent(side: String) : Component<PanelComponent>() {
    override val root = node(SelfTestTags.PANEL, side)
    val label = node(SelfTestTags.PANEL_LABEL)
    val labels = nodeAll(SelfTestTags.PANEL_LABEL)
    val list = lazyList(SelfTestTags.PANEL_LIST)
    val group = component(::PanelGroupComponent)
}

private class PanelGroupComponent : Component<PanelGroupComponent>() {
    override val root = node(SelfTestTags.PANEL_GROUP)
    val button = node(SelfTestTags.PANEL_BUTTON)
}

/** Declares the scoped node BEFORE the root it is scoped by - deliberately. */
private class ReversedOrderComponent : Component<ReversedOrderComponent>() {
    val label = node(SelfTestTags.PANEL_LABEL)
    override val root = node(SelfTestTags.PANEL, "right")
}

private class ReversedOrderScreen : Screen<ReversedOrderScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val panel = component(::ReversedOrderComponent)
}

/** No root at all - allowed: the first operation waits through its own retry. */
private class NoRootScreen : Screen<NoRootScreen>() {
    val title = node(SelfTestTags.TITLE)
}
