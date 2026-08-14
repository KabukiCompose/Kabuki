package kabuki.runner.selftest.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kabuki.KabukiAssertionError
import kabuki.page.Screen
import kabuki.page.UiNode
import kabuki.page.onScreen
import kabuki.runner.WindowMode
import kabuki.runner.runDesktopTest
import kabuki.semantics.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

enum class GridTags {
    GRID,

    /** + row, + column */
    CELL,
    SELECTED,
}

// Deliberately not square: a swapped (row, column) pair then addresses a cell
// that cannot exist, which is what the diagnostics tests rely on.
private const val GRID_ROWS = 3
private const val GRID_COLUMNS = 2

class GridState {
    var selected by mutableStateOf("none")
}

/**
 * Self-tests for parametrized tags: one tag serves a whole family of elements,
 * parameters address a single one, and a miss explains itself.
 */
class TagParamsSelfTest {

    @Composable
    private fun Grid(state: GridState) {
        MaterialTheme {
            Surface {
                Column(modifier = Modifier.testTag(GridTags.GRID)) {
                    Text(text = "Selected: ${state.selected}", modifier = Modifier.testTag(GridTags.SELECTED))
                    for (row in 1..GRID_ROWS) {
                        Row {
                            for (column in 1..GRID_COLUMNS) {
                                Text(
                                    text = "$row:$column",
                                    modifier = Modifier
                                        .testTag(GridTags.CELL, row, column)
                                        .clickable { state.selected = "$row:$column" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun paramsAddressOneElementAndTagAddressesTheFamily() = runDesktopTest(
        name = "Tag params: one element vs the whole family",
        window = WindowMode.Headless,
    ) {
        val state = GridState()
        setContent { Grid(state) }

        step("The bare tag matches every cell of the family") {
            nodeAll(GridTags.CELL).assertCountEquals(GRID_ROWS * GRID_COLUMNS)
        }

        step("Parameters address exactly one cell") {
            node(GridTags.CELL, 2, 1).click()
            node(GridTags.SELECTED).assertTextEquals("Selected: 2:1")
        }

        step("Numeric types do not matter - values are compared as text") {
            node(GridTags.CELL, 3L, "2").click()
            node(GridTags.SELECTED).assertTextEquals("Selected: 3:2")
        }
    }

    @Test
    fun pageObjectPathCarriesDiagnosticsToo() = runDesktopTest(
        name = "Tag params: diagnostics through a page object",
        window = WindowMode.Headless,
        config = { defaultTimeout = 1.seconds },
    ) {
        setContent { Grid(GridState()) }

        // The page object path goes through the matcher builder, not through
        // scope.node(...) - diagnostics must survive it
        val error = assertFailsWith<KabukiAssertionError> {
            onScreen<GridScreen> {
                cell(row = 1, column = 3).assertIsDisplayed()
            }
        }
        log("Error message:\n${error.message}")

        val message = error.message.orEmpty()
        assertTrue("Nodes with tag 'GridTags.CELL' present on screen" in message)
        assertTrue("arguments may be swapped" in message)
    }

    @Test
    fun swappedArgumentsProduceAnExplainingError() = runDesktopTest(
        name = "Tag params: swapped arguments hint",
        window = WindowMode.Headless,
        config = { defaultTimeout = 1.seconds },
    ) {
        val state = GridState()
        setContent { Grid(state) }

        // Ask for row 1, column 3. The grid has only GRID_COLUMNS columns, so that
        // cell cannot exist - while (3, 1) does. Exactly the shape of a swapped
        // pair, which the compiler cannot catch: both arguments are Ints.
        val error = assertFailsWith<KabukiAssertionError> {
            node(GridTags.CELL, 1, GRID_COLUMNS + 1).assertIsDisplayed()
        }
        log("Error message:\n${error.message}")

        val message = error.message.orEmpty()
        assertTrue("Nodes with tag 'GridTags.CELL' present on screen" in message)
        assertTrue("arguments may be swapped" in message)
        assertEquals("none", state.selected)
    }
}

/** Page object over the grid - the path most users take. */
class GridScreen : Screen<GridScreen>() {

    override val root = node(GridTags.GRID)

    val selected = node(GridTags.SELECTED)

    fun cell(row: Int, column: Int): UiNode {
        return node(GridTags.CELL, row, column)
    }
}
