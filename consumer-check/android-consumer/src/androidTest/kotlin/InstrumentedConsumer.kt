package consumer.android

import kabuki.page.Screen
import kabuki.page.onScreen
import kabuki.runner.KabukiTestCase
import kabuki.runner.runKabukiTest

/**
 * The test side of an Android consumer. Nothing runs - compiling is the test:
 * it answers whether kabuki-runner's AAR resolves and its API is reachable from
 * an androidTest source set, which is where every Android project puts it.
 */
private class AndroidScreen : Screen<AndroidScreen>() {
    override val root = node(AndroidTags.SCREEN)
    val row = node(AndroidTags.ROW, "row", 1)
}

@Suppress("unused")
private fun instrumentedTestCompiles(): () -> Unit {
    return {
        runKabukiTest(name = "android consumer") {
            onScreen<AndroidScreen> {
                root.assertIsDisplayed()
                row.assertIsDisplayed()
            }
        }
    }
}

/** The base class a real project extends. */
private abstract class AndroidConsumerTest : KabukiTestCase<Unit>()
