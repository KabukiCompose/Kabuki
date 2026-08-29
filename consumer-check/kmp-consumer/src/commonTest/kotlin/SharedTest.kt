package consumer.kmp

import kabuki.KabukiTestScope
import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.page.onScreen
import kabuki.runner.runKabukiTest

/**
 * A shared test, written once for every platform. Nothing runs here - the source
 * set only has to compile, which answers the KMP consumer's question: can a
 * Kotlin 2.2 build resolve and use our artifacts. Metadata variants stay
 * unchecked, see build.gradle.kts.
 */
private class KmpScreen : Screen<KmpScreen>() {
    override val root = node(KmpTags.SCREEN)
    val items = lazyList(KmpTags.LIST) { itemType(::KmpItem) }
}

private class KmpItem(scope: ListItemScope) : ListItem(scope) {
    val title = node(KmpTags.SCREEN)
}

@Suppress("unused")
private fun sharedTestCompiles(): () -> Unit {
    return {
        runKabukiTest(name = "consumer") {
            onScreen<KmpScreen> {
                items.assertLengthEquals(2)
                root.assertIsDisplayed()
            }
        }
    }
}

@Suppress("unused")
private fun scopeTypeIsVisible(scope: KabukiTestScope): Any = scope.config
