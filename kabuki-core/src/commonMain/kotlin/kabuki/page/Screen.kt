package kabuki.page

import kabuki.KabukiTestScope
import kabuki.KabukiUsageError
import kabuki.internal.CurrentTestScope

/**
 * Page object of a logical screen. Declares its elements as properties:
 *
 * ```kotlin
 * class LoginScreen : Screen<LoginScreen>() {
 *     override val root = node { withTag(LoginTags.SCREEN) }
 *     val loginButton = node { withTag(LoginTags.LOGIN_BUTTON) }
 * }
 * ```
 *
 * `onScreen<LoginScreen> { }` creates the instance and binds it to the test, so a
 * screen needs a public no-arg constructor.
 */
public abstract class Screen<T : Screen<T>> : NodeHost() {

    /**
     * The screen's root - `onScreen` waits for it to be displayed. Optional.
     *
     * It does NOT scope the screen's other nodes: dialogs, dropdowns and popups are
     * drawn outside the screen's subtree yet belong to the same page object. Tag
     * names keep screens apart instead (`PlaybillTags.CARD` is not
     * `TicketsTags.CARD`).
     */
    public open val root: UiNode? = null

    /**
     * Enters the screen: `PlaybillScreen { card("chushingura").click() }`.
     *
     * Same as `onScreen(PlaybillScreen) { }`: binds the screen to the test running
     * on this thread and waits for [root]. Needs neither reflection nor a keep rule,
     * which is what makes an `object` screen work. On an instance already entered in
     * this test it is just a scoped block.
     */
    public operator fun invoke(block: T.() -> Unit) {
        val current = CurrentTestScope.get() ?: throw KabukiUsageError(
            "${this::class.simpleName} { } needs a running Kabuki test on this thread - " +
                "use it inside runKabukiTest { }, or enter the screen explicitly with " +
                "onScreen(${this::class.simpleName}) { }.",
        )
        if (bindingOrNull() !== current) {
            enter(current)
        }
        @Suppress("UNCHECKED_CAST")
        (this as T).block()
    }

    /** Binds the screen to [scope] and waits for [root]. Shared with `onScreen`. */
    internal fun enter(scope: KabukiTestScope) {
        if (scope.isFinished) {
            throw KabukiUsageError(
                "${this::class.simpleName} cannot be entered: the test it belongs to has already " +
                    "finished. Enter the screen inside the test that uses it.",
            )
        }
        bind(scope)
        scope.log("onScreen: ${this::class.simpleName}")
        root?.assertIsDisplayed()
    }
}

/**
 * Reusable UI fragment nested into screens or other components. Declared with
 * `val numpad = component(::NumpadComponent)` and used as `numpad { ... }`.
 *
 * ```kotlin
 * class NavBarComponent : Component<NavBarComponent>() {
 *     override val root = node(NavTags.NAV_BAR)
 *     val playbillTab = node(NavTags.TAB_PLAYBILL)   // searched INSIDE the nav bar
 * }
 * ```
 */
public abstract class Component<T : Component<T>> : NodeHost() {

    /**
     * The component's root - required, because it is what scopes the component:
     * every other node declared here is searched inside it, so two identical
     * components on one screen do not address each other's elements. Unlike a
     * screen root it is not awaited.
     */
    public abstract val root: UiNode

    final override fun scopingRoot(): UiNode {
        return root
    }

    /**
     * Scoped block over the component: `numpad { enterNumber("42") }`.
     *
     * An `object` component works on its own too - `NavBar { playbillTab.click() }` -
     * binding itself to the test on this thread. It never waits for its root: a
     * component is part of a screen that is already there.
     */
    public operator fun invoke(block: T.() -> Unit) {
        val current = CurrentTestScope.get()
        when {
            // Entered from inside its screen, or used on its own under a test.
            current != null && bindingOrNull() !== current -> bind(current)
            // Neither: refuse instead of running a block that would silently
            // do nothing.
            current == null && bindingOrNull() == null -> throw KabukiUsageError(
                "${this::class.simpleName} { } needs a running Kabuki test on this thread, " +
                    "or a screen that has already been entered.",
            )
        }
        @Suppress("UNCHECKED_CAST")
        (this as T).block()
    }
}
