package kabuki

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction

/**
 * Wraps every node operation and can replace HOW it is performed.
 *
 * An interceptor either calls [InterceptedOperation.proceed] to run the original
 * operation, or does something else instead. This is what keeps the public API
 * free of variants: there is one [UiNode.click], and a project that needs a
 * different way of clicking installs an interceptor once instead of passing a
 * flag at every call site.
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
     * Called instead of the operation. Call [InterceptedOperation.proceed] to run
     * the rest of the chain (and finally the original operation), or do something
     * else entirely to replace it.
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

/** Operation name of [UiNode.click] - the one interceptors below react to. */
internal const val CLICK_OPERATION: String = "click"

/**
 * Performs clicks on the platform UI thread.
 *
 * Works around a desktop issue where a click sent from the test thread is lost
 * while the window is still regaining focus.
 */
public class ClickOnUiThread : KabukiInterceptor {
    override fun intercept(operation: InterceptedOperation) {
        if (operation.name != CLICK_OPERATION) {
            operation.proceed()
            return
        }
        operation.onUiThread { operation.node.performClick() }
    }
}

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
        operation.onUiThread { operation.node.performSemanticsAction(SemanticsActions.OnClick) }
    }
}
