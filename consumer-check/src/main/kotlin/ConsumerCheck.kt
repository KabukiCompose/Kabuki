package consumer

import androidx.compose.ui.Modifier
import kabuki.ClickViaSemanticsAction
import kabuki.KabukiConfig
import kabuki.Tree
import kabuki.TreeStrategy
import kabuki.junit4.KabukiInterop
import kabuki.page.Screen
import kabuki.runner.KabukiTestCase
import kabuki.semantics.testListItem
import kabuki.semantics.testTag
import kotlin.time.Duration.Companion.seconds

/**
 * Touches the public surface of every published module.
 *
 * Nothing here runs - compiling IS the test. A consumer on Kotlin 2.2 either
 * reads our metadata and our stdlib or does not, and that answer is what this
 * build exists to get.
 *
 * Composables are absent on purpose: a plain JVM consumer has no Compose
 * compiler plugin, and the question here is metadata compatibility, not whether
 * a screen renders.
 */
private enum class ConsumerTags { SCREEN, ITEM }

@Suppress("UnusedPrivateMember", "unused")
private fun productionCodeUsesSemantics(): Modifier {
    return Modifier
        .testTag(ConsumerTags.SCREEN)
        .testTag(ConsumerTags.ITEM, "row", 1)
        .testListItem(index = 0)
}

@Suppress("UnusedPrivateMember", "unused")
private fun testCodeUsesTheDsl(): KabukiConfig {
    return KabukiConfig().apply {
        defaultTimeout = 5.seconds
        stallWarningAfter = 30.seconds
        stallReporter = { message -> println(message) }
        treeStrategy = TreeStrategy.AlwaysUnmerged
        interceptors += ClickViaSemanticsAction()
    }
}

private class ConsumerScreen : Screen<ConsumerScreen>() {
    override val root = node(ConsumerTags.SCREEN)
    val item = node(ConsumerTags.ITEM, "row", 1)
}

@Suppress("UnusedPrivateMember", "unused")
private fun pageObjectsCompile(): Pair<Tree, ConsumerScreen> {
    return Tree.Unmerged to ConsumerScreen()
}

/** The two entry points a real project extends: the base class and the interop mixin. */
private abstract class ConsumerBaseTest : KabukiTestCase<Unit>()

@Suppress("UnusedPrivateMember", "unused")
private fun interopTypeIsVisible(interop: KabukiInterop): Any {
    return interop.kabukiScope
}
