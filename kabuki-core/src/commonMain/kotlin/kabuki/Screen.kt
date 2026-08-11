package kabuki


/**
 * Page object of a logical screen. Declares [root] and its elements as properties:
 *
 * ```kotlin
 * class LoginScreen : Screen<LoginScreen>() {
 *     override val root = node { withTag(LoginTags.SCREEN) }
 *     val loginButton = node { withTag(LoginTags.LOGIN_BUTTON) }
 * }
 * ```
 *
 * Screens require a public no-arg constructor - `onScreen<LoginScreen> { }`
 * creates the instance and binds it to the current test scope.
 */
public abstract class Screen<T : Screen<T>> : NodeHost() {

    /** The screen's root node - `onScreen` waits for it to be displayed. */
    public abstract val root: UiNode

    /** Scoped block over an already obtained screen: `screen { loginButton { click() } }`. */
    public operator fun invoke(block: T.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        (this as T).block()
    }
}

/**
 * Reusable UI fragment nested into screens or other components. Declared with
 * `val numpad = component(::NumpadComponent)` and used as `numpad { ... }`.
 */
public abstract class Component<T : Component<T>> : NodeHost() {

    /** The component's root node. Unlike a screen root, it is not awaited automatically. */
    public abstract val root: UiNode

    /** Scoped block over the component: `numpad { enterNumber("42") }`. */
    public operator fun invoke(block: T.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        (this as T).block()
    }
}
