package kabuki.page

import kabuki.KabukiTestScope
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

/**
 * [onScreen] overload for a manually constructed screen instance, and the form to
 * use for an `object` screen: `onScreen(PlaybillScreen) { }`.
 *
 * A screen without a root is entered without waiting - the first operation in the
 * block does the waiting through its own retry.
 */
public fun <T : Screen<T>> KabukiTestScope.onScreen(screen: T, block: T.() -> Unit = {}): T {
    screen.enter(this)
    screen.invoke(block)
    return screen
}

/**
 * Creates a screen instance by its class. Internal machinery for the reified
 * [onScreen] overload - not intended to be called directly.
 *
 * On JVM and Android an `object` screen is taken as it is and a class is created
 * through its public no-arg constructor. A target without runtime reflection
 * provides its own actual.
 */
public expect fun <T : Screen<T>> instantiateScreen(kClass: KClass<T>): T
