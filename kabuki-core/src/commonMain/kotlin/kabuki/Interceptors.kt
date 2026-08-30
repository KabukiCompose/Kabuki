package kabuki

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import kabuki.internal.runOnUiThread

/**
 * Wraps every node operation and can replace HOW it is performed.
 *
 * An interceptor either calls [InterceptedOperation.proceed] to run the original
 * operation, or does something else instead. That keeps the public API free of
 * variants: there is one [kabuki.page.UiNode.click], and a project needing another
 * way to click installs an interceptor once instead of passing a flag everywhere.
 *
 * ```kotlin
 * runKabukiTest(config = { interceptors += ClickViaSemanticsAction() }) { ... }
 * ```
 *
 * Interceptors run in registration order; each one decides whether to pass the
 * operation down the chain.
 */
public fun interface KabukiInterceptor {
    /**
     * Called instead of the operation. [InterceptedOperation.proceed] runs the rest
     * of the chain and then the original; skip it to replace the operation.
     */
    public fun intercept(operation: InterceptedOperation)
}

/**
 * The operation being intercepted. Lives INSIDE the retry loop, so a replacement
 * is retried exactly like the original would be.
 *
 * Names follow the DSL: `"click"`, `"typeText('abc')"`, `"assertIsDisplayed"`.
 */
public class InterceptedOperation internal constructor(
    /** Operation name, e.g. `"click"`. */
    public val name: String,
    /** Node description, e.g. `"tag 'PlaybillTags.SCREEN'"`. */
    public val nodeDescription: String,
    private val nodeProvider: () -> SemanticsNodeInteraction,
    private val proceedAction: () -> Unit,
) {
    /**
     * The resolved node. Resolved on access, i.e. on every retry attempt -
     * so it is safe to touch even if the node is not there yet.
     */
    public val node: SemanticsNodeInteraction
        get() {
            return nodeProvider()
        }

    /** Runs the original operation (through the rest of the chain). */
    public fun proceed() {
        proceedAction()
    }

    /** Runs [block] on the platform UI thread (Swing EDT on desktop). */
    public fun <T> onUiThread(block: () -> T): T {
        return runOnUiThread(block)
    }
}

/** Operation name of [kabuki.page.UiNode.click] - the one interceptors below react to. */
internal const val CLICK_OPERATION: String = "click"

/**
 * Invokes the OnClick semantics action instead of injecting a pointer event.
 *
 * Reliable right after a dialog opens or closes, when hit testing may still
 * resolve against the previous layout and a pointer click lands nowhere.
 */
@OptIn(ExperimentalTestApi::class)
public class ClickViaSemanticsAction : KabukiInterceptor {
    override fun intercept(operation: InterceptedOperation) {
        if (operation.name != CLICK_OPERATION) {
            operation.proceed()
            return
        }
        // Called straight from the test thread. Wrapping it in onUiThread works on
        // desktop but is forbidden on Android, where the test framework refuses
        // every action taken from the main thread.
        operation.node.performSemanticsAction(SemanticsActions.OnClick)
    }
}
