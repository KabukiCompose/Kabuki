package kabuki.runner.selftest.tests

import kabuki.KabukiSkipException
import kabuki.skipTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Skipping is built by reflection over whatever test framework is on the
 * classpath, since kabuki-core depends on none. Lose the class and every
 * `assumeOs` turns from a skip into a failure - silently, because the profile
 * tests only check the opposite case.
 */
class SkipMechanismSelfTest {

    @Test
    fun skippingSpeaksTheFrameworksOwnLanguage() {
        val error = runCatching { skipTest("checking the mechanism") }.exceptionOrNull()

        assertNotNull(error, "skipTest returned instead of throwing")
        assertTrue(
            error !is KabukiSkipException,
            "Fell back to Kabuki's own exception - the framework class was not found, " +
                "so every assumeOs would fail the test instead of skipping it",
        )
        // JUnit 4 on both platforms today; opentest4j is the other accepted answer.
        assertTrue(
            error::class.simpleName in setOf("AssumptionViolatedException", "TestAbortedException"),
            "Unexpected skip exception: ${error::class.simpleName}",
        )
    }

    @Test
    fun theReasonSurvivesIntoTheMessage() {
        val error = runCatching { skipTest("desktop only") }.exceptionOrNull()

        // Without it a skipped run says nothing about why it skipped.
        assertEquals(true, error?.message?.contains("desktop only"), "Reason lost: ${error?.message}")
    }
}
