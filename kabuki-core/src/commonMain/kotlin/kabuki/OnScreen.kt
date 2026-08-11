package kabuki

import kotlin.reflect.KClass

/**
 * Enters a screen scope: creates the screen, binds it to the current test scope,
 * waits for [Screen.root] to be displayed (with retry) and runs the block.
 *
 * ```kotlin
 * onScreen<LoginScreen> {
 *     loginButton { click() }
 * }
 * ```
 *
 * The screen class must have a public no-arg constructor. For screens with
 * parameters use the overload taking an instance: `onScreen(LoginScreen(...)) { }`.
 */
public inline fun <reified T : Screen<T>> KabukiTestScope.onScreen(noinline block: T.() -> Unit = {}): T {
    val screen = instantiateScreen(T::class)
    return onScreen(screen, block)
}

/** [onScreen] overload for a manually constructed screen instance. */
public fun <T : Screen<T>> KabukiTestScope.onScreen(screen: T, block: T.() -> Unit = {}): T {
    screen.bind(this)
    log("onScreen: ${screen::class.simpleName}")
    screen.root.assertIsDisplayed()
    screen.invoke(block)
    return screen
}

/**
 * Creates a screen instance by its class. Internal machinery for the reified
 * [onScreen] overload - not intended to be called directly.
 *
 * On JVM and Android the screen is instantiated via its public no-arg
 * constructor. A target without runtime reflection provides its own actual.
 */
public expect fun <T : Screen<T>> instantiateScreen(kClass: KClass<T>): T
