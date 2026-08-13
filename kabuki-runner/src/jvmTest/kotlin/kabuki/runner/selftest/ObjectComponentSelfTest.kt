package kabuki.runner.selftest

import kabuki.page.Component
import kabuki.page.Screen
import kabuki.page.onScreen
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for components declared as `object`.
 *
 * A singleton component is shared by every page object that declares it, so the
 * question is what it searches inside of - and whether that stays correct when
 * two different owners declare the same singleton.
 */
class ObjectComponentSelfTest : SelfTestCase() {

    @Test
    fun anObjectComponentWorksInsideSeveralScreens() {
        runTest(name = "object component, two screens") {
            // A screen does not scope its nodes (dialogs live outside its subtree),
            // so both screens give the component the same - empty - context.
            onScreen(FirstScreen()) { treeButton.label.assertTextEquals(TREE_LABEL_TEXT) }
            onScreen(SecondScreen()) { treeButton.label.assertTextEquals(TREE_LABEL_TEXT) }
        }
    }

    @Test
    fun anObjectComponentCanBeUsedWithoutEnteringAScreen() {
        runTest(name = "component on its own") {
            // A component is a part of a screen that is already on display, so the
            // short form binds it to the running test and does NOT wait for a root.
            TreeButtonBlock { label.assertTextEquals(TREE_LABEL_TEXT) }
        }
    }

    @Test
    fun enteringTheSameScreenTwiceDoesNotLookLikeTwoOwners() {
        runTest(name = "same screen entered twice") {
            // Each entry builds a NEW screen instance, so the singleton group ends
            // up with two owners - but they describe the SAME container, and that
            // must not read as an ambiguity.
            onScreen(SinglePanelScreen()) { panel.group.button.assertTextContains("Tap left") }
            onScreen(SinglePanelScreen()) { panel.group.button.assertTextContains("Tap left") }
        }
    }

    @Test
    fun aComponentOutsideATestSaysWhatIsMissing() {
        // Used to go quiet: the block ran, every operation inside it failed later,
        // and a block WITHOUT operations passed while doing nothing.
        val error = assertFailsWith<IllegalStateException> {
            TreeButtonBlock { label.assertExists() }
        }
        assertTrue(
            "needs a running Kabuki test" in error.message.orEmpty(),
            "The refusal must name what is missing: ${error.message}",
        )
    }

    @Test
    fun anObjectComponentInsideTwoScopingComponentsIsRejected() {
        val error = assertFailsWith<IllegalStateException> {
            runTest(name = "object component, two scoping owners") {
                // Both panels declare the SAME singleton group, and each of them
                // scopes. One object cannot belong to two containers at once, so
                // the library says so instead of silently taking the last owner -
                // which used to make the left panel search inside the right one.
                onScreen(PanelsWithSharedGroupScreen()) {
                    left.group.button.assertTextContains("Tap left")
                }
            }
        }
        assertTrue(
            "declare it as a class instead of an object" in error.message.orEmpty(),
            "The failure must name the way out: ${error.message}",
        )
    }
}

private class SinglePanelScreen : Screen<SinglePanelScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val panel = component { PanelWithSharedGroup("left") }
}

private object TreeButtonBlock : Component<TreeButtonBlock>() {
    override val root = node(SelfTestTags.TREE_BUTTON)
    val label = node(SelfTestTags.TREE_BUTTON_LABEL)
}

private class FirstScreen : Screen<FirstScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val treeButton = component { TreeButtonBlock }
}

private class SecondScreen : Screen<SecondScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val treeButton = component { TreeButtonBlock }
}

private object SharedGroupBlock : Component<SharedGroupBlock>() {
    override val root = node(SelfTestTags.PANEL_GROUP)
    val button = node(SelfTestTags.PANEL_BUTTON)
}

private class PanelWithSharedGroup(side: String) : Component<PanelWithSharedGroup>() {
    override val root = node(SelfTestTags.PANEL, side)
    val group = component { SharedGroupBlock }
}

private class PanelsWithSharedGroupScreen : Screen<PanelsWithSharedGroupScreen>() {
    override val root = node(SelfTestTags.SCREEN)
    val left = component { PanelWithSharedGroup("left") }
    val right = component { PanelWithSharedGroup("right") }
}
