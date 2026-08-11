package kabuki

/**
 * Reusable scenario: a sequence of steps executed within the test scope.
 *
 * ```kotlin
 * fun LoginScenario(token: String) = Scenario {
 *     step("Sign in") { node(Tags.LOGIN_BUTTON).click() }
 * }
 * ```
 */
public fun interface Scenario {
    /** The body of the scenario, run against the current test scope. */
    public fun KabukiTestScope.execute()
}

/**
 * Runs a scenario inline: its steps are numbered as a continuation of the
 * current test, not as a separate one.
 */
public fun KabukiTestScope.scenario(scenario: Scenario) {
    with(scenario) { execute() }
}
