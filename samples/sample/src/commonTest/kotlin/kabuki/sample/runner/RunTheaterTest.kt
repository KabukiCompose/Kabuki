package kabuki.sample.runner

import kabuki.KabukiTestScope
import kabuki.runner.runKabukiTest
import kabuki.sample.TheaterState
import kabuki.sample.ui.TheaterApp

/**
 * Entry point of the shared UI tests - a plain common function, no expect/actual:
 * runKabukiTest picks the desktop or Android runner by the compilation target.
 *
 * Creates a fresh [TheaterState], installs the app content and runs the block
 * in a KabukiTestScope.
 */
fun runTheaterTest(
    name: String,
    holdPlaybill: Boolean = false,
    block: KabukiTestScope.(app: TheaterState) -> Unit,
) {
    runKabukiTest(name = name) {
        val app = TheaterState()
        // Tests that assert the loading state freeze it here and release it
        // themselves - otherwise they race the fake network delay and flake
        // on slow devices.
        if (holdPlaybill) {
            app.repository.holdPlaybill()
        }
        setContent { TheaterApp(app) }
        block(app)
    }
}
