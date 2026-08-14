package kabuki.runner.selftest.app

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kabuki.semantics.testListItem
import kabuki.semantics.testListLength
import kabuki.semantics.testTag
import kabuki.semantics.testTintColor
import kotlinx.coroutines.delay

/**
 * Tags of the self-test screen, declared the way the library recommends: an enum,
 * not string constants. Renaming an entry is then a compile error rather than a
 * test that quietly stops finding anything.
 */
enum class SelfTestTags {
    SCREEN,
    TITLE,
    COUNTER_BUTTON,
    COUNTER_VALUE,
    LOAD_BUTTON,
    STATUS,
    DELAYED_BLOCK,
    INPUT,

    // Entries below exist to cover UiNode operations that the main screen
    // cannot express: states, gestures, tint, scrolling.
    DISABLED_BUTTON,
    CHECKBOX,
    SELECTABLE,

    /** Carries both a tint colour and a content description. */
    TINTED,
    GESTURES,
    GESTURES_LOG,
    FAR_BLOCK,
    LAZY_LIST,
    LAZY_ITEM,

    /** Clickable node whose text lives in a tagged child - the merged/unmerged probe. */
    TREE_BUTTON,

    /** Tag on the Text INSIDE [TREE_BUTTON]: present in the unmerged tree only. */
    TREE_BUTTON_LABEL,

    /** Text field WITH a label - the merged view of it mixes the label into the text. */
    LABELED_INPUT,

    /** Two panels with the SAME inner tags - the component scoping probe. */
    PANEL,
    PANEL_LABEL,
    PANEL_GROUP,
    PANEL_BUTTON,

    /** A list inside each panel - same tags in both, so scoping has to tell them apart. */
    PANEL_LIST,
    PANEL_ITEM,
}

/** Text of [SelfTestTags.TREE_BUTTON_LABEL] - unique on the screen, searched for by content. */
const val TREE_LABEL_TEXT: String = "Nested label"

/** Label of [SelfTestTags.LABELED_INPUT] - deliberately never typed into the field. */
const val LABELED_INPUT_LABEL: String = "Email address"

/**
 * One element is tagged with a plain string on purpose: the string-based API is
 * public too (for tags that are not yours to change - a third-party screen, a
 * generated tag), so it needs a test of its own.
 */
const val LEGACY_STRING_TAG: String = "selftest_legacy_string_tag"

/** Tint applied to [SelfTestTags.TINTED] - asserted by the self-test. */
val SelfTestTint: Color = Color(0xFF3F51B5)

/**
 * State is hoisted above the composable: in Visible mode the same instance is
 * shared between the headless scene and the real window, so the window mirrors
 * everything the test does to the scene.
 */
class SelfTestAppState {
    var counter by mutableStateOf(0)
    var status by mutableStateOf("Idle")
    var delayedBlockVisible by mutableStateOf(false)
    var input by mutableStateOf("")
    var labeledInput by mutableStateOf("")

    var checked by mutableStateOf(false)
    var selected by mutableStateOf(false)

    /** Last gesture recognised by [SelfTestTags.GESTURES]: none / double / long. */
    var lastGesture by mutableStateOf("none")

    /** Simulates loading on a real background thread - retry has to wait in real time. */
    fun startLoading() {
        status = "Loading..."
        runInBackground(delayMillis = 500) {
            status = "Done"
        }
    }
}

/**
 * Which part of the self-test screen a test needs.
 *
 * The whole screen is taller than a phone in landscape, so anything below the fold
 * exists in the tree but is never displayed - and a test asserting visibility then
 * passes on a desktop scene and fails on a device. A test that cares about the
 * scrollable parts asks for [Scrolling] and gets a screen that fits anywhere.
 */
enum class SelfTestSection {
    /** Everything. Fine for elements near the top of the screen. */
    All,

    /** Only the scrollable column and the lazy list. */
    Scrolling,
}

@Composable
fun SelfTestApp(state: SelfTestAppState, section: SelfTestSection = SelfTestSection.All) {
    // A block appearing after a delay on the test's virtual clock - retry has to advance time
    LaunchedEffect(Unit) {
        delay(1_500)
        state.delayedBlockVisible = true
    }

    val showAll = section == SelfTestSection.All
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).testTag(SelfTestTags.SCREEN),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showAll) {
                    MainControls(state)
                }
                ScrollableArea()
                if (showAll) {
                    TreeProbe(state)
                    ScopingProbe()
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MainControls(state: SelfTestAppState) {
    Text(
        text = "Kabuki SelfTest",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(SelfTestTags.TITLE),
    )

    Button(
        onClick = { state.counter++ },
        modifier = Modifier.testTag(SelfTestTags.COUNTER_BUTTON),
    ) {
        Text("Counter +1")
    }
    Text(
        text = "Counter: ${state.counter}",
        modifier = Modifier.testTag(SelfTestTags.COUNTER_VALUE),
    )

    Button(
        onClick = { state.startLoading() },
        modifier = Modifier.testTag(SelfTestTags.LOAD_BUTTON),
    ) {
        Text("Load")
    }
    Text(
        text = state.status,
        modifier = Modifier.testTag(SelfTestTags.STATUS),
    )

    if (state.delayedBlockVisible) {
        Text(
            text = "Delayed block appeared",
            modifier = Modifier.testTag(SelfTestTags.DELAYED_BLOCK),
        )
    }

    TextField(
        value = state.input,
        onValueChange = { state.input = it },
        modifier = Modifier.testTag(SelfTestTags.INPUT),
    )

    StatesAndGestures(state)
}

/** Node states and gestures: enabled, checked, selected, described, tinted, double/long click. */
@Composable
private fun StatesAndGestures(state: SelfTestAppState) {
    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.testTag(SelfTestTags.DISABLED_BUTTON),
    ) {
        Text("Disabled")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.checked,
            onCheckedChange = { state.checked = it },
            modifier = Modifier.testTag(SelfTestTags.CHECKBOX),
        )
        Text(
            text = "Selectable",
            modifier = Modifier
                .selectable(
                    selected = state.selected,
                    onClick = { state.selected = !state.selected },
                )
                .testTag(SelfTestTags.SELECTABLE),
        )
    }

    // One node carries both the tint and the content description - two separate
    // assertions, one element. A second testTag would overwrite the first.
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(SelfTestTint)
            .testTintColor(SelfTestTint)
            .semantics { contentDescription = "A favourite marker" }
            .testTag(SelfTestTags.TINTED),
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                onClick = { state.lastGesture = "click" },
                onDoubleClick = { state.lastGesture = "double" },
                onLongClick = { state.lastGesture = "long" },
            )
            .testTag(SelfTestTags.GESTURES),
    )
    Text(
        text = state.lastGesture,
        modifier = Modifier.testTag(SelfTestTags.GESTURES_LOG),
    )

    // The string-tagged element - see LEGACY_STRING_TAG.
    Text(text = "Legacy", modifier = Modifier.testTag(LEGACY_STRING_TAG))
}

/**
 * The one shape where the two semantics trees disagree: a clickable node whose
 * text sits in a TAGGED child. In the merged tree the child is gone and its text
 * belongs to the button; in the unmerged tree the child keeps both its tag and
 * its text, and the button has no text at all.
 */
@Composable
private fun TreeProbe(state: SelfTestAppState) {
    Button(
        onClick = { state.counter++ },
        modifier = Modifier.testTag(SelfTestTags.TREE_BUTTON),
    ) {
        Text(
            text = TREE_LABEL_TEXT,
            modifier = Modifier.testTag(SelfTestTags.TREE_BUTTON_LABEL),
        )
    }

    // Left empty on purpose: an assertion about the VALUE must not pass on the
    // label, which the merged view of this field does contain.
    TextField(
        value = state.labeledInput,
        onValueChange = { state.labeledInput = it },
        label = { Text(LABELED_INPUT_LABEL) },
        modifier = Modifier.testTag(SelfTestTags.LABELED_INPUT),
    )
}

/**
 * Two panels that are identical inside: same label tag, same group tag, same
 * button tag. Only the panel's own tag parameter tells them apart, so anything
 * addressed without scoping matches twice and cannot be resolved at all.
 */
@Composable
private fun ScopingProbe() {
    Row {
        Panel(side = "left")
        Panel(side = "right")
    }
}

@Composable
private fun Panel(side: String) {
    Column(modifier = Modifier.testTag(SelfTestTags.PANEL, side)) {
        Text(text = side, modifier = Modifier.testTag(SelfTestTags.PANEL_LABEL))
        Column(modifier = Modifier.testTag(SelfTestTags.PANEL_GROUP)) {
            Button(
                onClick = {},
                modifier = Modifier.testTag(SelfTestTags.PANEL_BUTTON),
            ) {
                Text("Tap $side")
            }
        }
        LazyColumn(
            modifier = Modifier
                .height(40.dp)
                .testTag(SelfTestTags.PANEL_LIST),
        ) {
            items(2) { index ->
                Text(
                    text = "$side item $index",
                    modifier = Modifier
                        .testListItem(index)
                        .testTag(SelfTestTags.PANEL_ITEM),
                )
            }
        }
    }
}

/**
 * Two scrollable containers: a plain one (for scrollTo and for a node that
 * exists but is off-screen) and a lazy list with published item indices
 * (for scrollToIndex).
 */
@Composable
private fun ScrollableArea() {
    Column(
        modifier = Modifier
            .height(80.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        repeat(12) { index ->
            Text(text = "filler $index", modifier = Modifier.height(30.dp))
        }
        // Far below the visible area: exists in the tree, but not displayed
        // until something scrolls to it.
        Text(text = "Far block", modifier = Modifier.testTag(SelfTestTags.FAR_BLOCK))
    }

    LazyColumn(
        modifier = Modifier
            .height(80.dp)
            .testListLength(LAZY_ITEM_COUNT)
            .testTag(SelfTestTags.LAZY_LIST),
    ) {
        items(LAZY_ITEM_COUNT) { index ->
            Text(
                text = "lazy item $index",
                modifier = Modifier
                    .height(30.dp)
                    .testListItem(index)
                    .testTag(SelfTestTags.LAZY_ITEM),
            )
        }
    }
}

/**
 * Deliberately far more items than fit on screen: the length self-test relies on
 * most of them never being composed.
 */
const val LAZY_ITEM_COUNT = 30
