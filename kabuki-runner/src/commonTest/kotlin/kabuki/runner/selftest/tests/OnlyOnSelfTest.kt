package kabuki.runner.selftest.tests

import androidx.compose.ui.unit.dp
import kabuki.HeightBreakpoints
import kabuki.HeightClass
import kabuki.Orientation
import kabuki.Os
import kabuki.Profiles
import kabuki.WidthBreakpoints
import kabuki.WidthClass
import kabuki.heightClass
import kabuki.listener.KabukiListener
import kabuki.onlyOnHeightClass
import kabuki.onlyOnOrientation
import kabuki.onlyOnOs
import kabuki.onlyOnPlatform
import kabuki.onlyOnWidthAtLeast
import kabuki.onlyOnWidthBelow
import kabuki.onlyOnWidthClass
import kabuki.runner.runKabukiTest
import kabuki.widthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Self-test for the `onlyOn*` family: run the block here, move on elsewhere.
 *
 * Common code on purpose - OS branching is exactly the thing that must behave the
 * same on a device as on the desktop. [Os.Browser] stands for "an OS this run is
 * certainly not on", which holds on every platform Kabuki currently supports.
 */
class OnlyOnSelfTest {

    @Test
    fun theBlockRunsOnAMatchingOs() = runKabukiTest(name = "onlyOnOs match") {
        var ran = false
        onlyOnOs(profile.os) { ran = true }

        assertTrue(ran, "The block must run when the OS matches")
    }

    @Test
    fun theBlockIsSkippedOnAnotherOs() = runKabukiTest(name = "onlyOnOs miss") {
        var ran = false
        onlyOnOs(Os.Browser) { ran = true }

        assertFalse(ran, "The block must not run on an OS that was not listed")
    }

    @Test
    fun anyOfSeveralListedOsesCounts() = runKabukiTest(name = "onlyOnOs several") {
        var ran = false
        onlyOnOs(Os.Browser, profile.os) { ran = true }

        assertTrue(ran, "Listing several OSes must match any of them")
    }

    @Test
    fun aSkippedBlockIsAnnounced() {
        // The whole safety net of this family. A block that did not run leaves no
        // trace in the test itself, and silence is how a test ends up green having
        // checked nothing - so the report has to say it was skipped.
        val log = LogRecorder()
        runKabukiTest(name = "onlyOnOs reports a skip", config = { listeners += log }) {
            onlyOnOs(Os.Browser) { }
        }

        val line = log.lines.singleOrNull { "onlyOnOs" in it }
        assertTrue(line != null && "skipped" in line, "The skip must be reported, got: ${log.lines}")
    }

    @Test
    fun anExecutedBlockIsAnnouncedToo() {
        val log = LogRecorder()
        runKabukiTest(name = "onlyOnOs reports a run", config = { listeners += log }) {
            onlyOnOs(profile.os) { }
        }

        val line = log.lines.singleOrNull { "onlyOnOs" in it }
        assertTrue(line != null && "running" in line, "The run must be reported, got: ${log.lines}")
    }

    @Test
    fun platformAndOrientationBranchToo() = runKabukiTest(
        name = "platform and orientation",
        profile = Profiles.Desktop.Default,
    ) {
        var platform = false
        var landscape = false
        var portrait = false
        onlyOnPlatform(profile.platform) { platform = true }
        onlyOnOrientation(Orientation.Landscape) { landscape = true }
        onlyOnOrientation(Orientation.Portrait) { portrait = true }

        assertTrue(platform, "onlyOnPlatform must match the profile's own platform")
        assertTrue(landscape, "1280x800 is landscape")
        assertFalse(portrait, "1280x800 is not portrait")
    }

    @Test
    fun widthAndHeightBucketsComeFromTheProfile() = runKabukiTest(
        name = "buckets",
        profile = Profiles.Desktop.Default,
    ) {
        assertEquals(WidthClass.Large, widthClass, "1280dp is Large")
        assertEquals(HeightClass.Medium, heightClass, "800dp is Medium")

        var large = false
        var compact = false
        onlyOnWidthClass(WidthClass.Large, WidthClass.ExtraLarge) { large = true }
        onlyOnWidthClass(WidthClass.Compact) { compact = true }

        assertTrue(large, "The block must run in the matching bucket")
        assertFalse(compact, "...and not in another one")
    }

    @Test
    fun theLandscapePhoneIsTheOneProfileWithACompactHeight() = runKabukiTest(
        name = "compact height",
        profile = Profiles.Android.PhoneLandscape,
    ) {
        // Orientation cannot express this: a tablet in landscape is landscape as
        // well, with twice the vertical room.
        assertEquals(Orientation.Landscape, profile.orientation)
        assertEquals(HeightClass.Compact, heightClass, "411dp of height is Compact")

        var ran = false
        onlyOnHeightClass(HeightClass.Compact) { ran = true }
        assertTrue(ran)
    }

    @Test
    fun breakpointsFromTheConfigDecideTheBucket() = runKabukiTest(
        name = "custom breakpoints",
        profile = Profiles.Desktop.Default,
        // The app switches layout later than Material does; 1280dp must follow it.
        config = { widthBreakpoints = WidthBreakpoints(large = 1400.dp) },
    ) {
        assertEquals(WidthClass.Expanded, widthClass, "With large=1400 the 1280dp window is Expanded")
    }

    @Test
    fun rawDpSkipsTheBucketsEntirely() = runKabukiTest(
        name = "raw dp",
        profile = Profiles.Desktop.Default,
    ) {
        var wide = false
        var narrow = false
        onlyOnWidthAtLeast(700.dp) { wide = true }
        onlyOnWidthBelow(700.dp) { narrow = true }

        assertTrue(wide, "1280dp is at least 700dp")
        assertFalse(narrow, "...and not below it")
    }

    @Test
    fun breakpointsMustGrow() {
        // A silently unordered table would classify everything into one bucket.
        val error = assertFailsWith<IllegalArgumentException> {
            WidthBreakpoints(medium = 900.dp, expanded = 840.dp)
        }
        assertTrue("must grow" in error.message.orEmpty(), "Got: ${error.message}")

        assertFailsWith<IllegalArgumentException> {
            HeightBreakpoints(medium = 900.dp, expanded = 480.dp)
        }
    }
}

/** Collects what the `onlyOn*` blocks say about themselves. */
private class LogRecorder : KabukiListener {
    val lines: MutableList<String> = mutableListOf()

    override fun onLog(message: String) {
        lines += message
    }
}
