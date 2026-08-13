package kabuki.sample.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kabuki.sample.TheaterScreen
import kabuki.sample.TheaterState
import kabuki.semantics.testTag
import kotlinx.coroutines.launch

enum class NavTags {
    NAV_BAR,
    TAB_PLAYBILL,
    TAB_TICKETS,
    BACK_BUTTON,
}

/**
 * App root: window-width adaptivity, tab + in-depth navigation, snackbars.
 * Playbill column count: <600dp - 1, <840dp - 2, otherwise 3 (Material 3 thresholds).
 */
@Composable
fun TheaterApp(state: TheaterState) {
    MaterialTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        BoxWithConstraints {
            val expanded = maxWidth >= 840.dp
            val playbillColumns = when {
                maxWidth < 600.dp -> 1
                maxWidth < 840.dp -> 2
                else -> 3
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(modifier = Modifier.testTag(NavTags.NAV_BAR)) {
                        NavigationBarItem(
                            selected = state.screen !is TheaterScreen.Tickets,
                            onClick = { state.screen = TheaterScreen.Playbill },
                            icon = { Text("🎭") },
                            label = { Text("Playbill") },
                            modifier = Modifier.testTag(NavTags.TAB_PLAYBILL),
                        )
                        NavigationBarItem(
                            selected = state.screen is TheaterScreen.Tickets,
                            onClick = { state.screen = TheaterScreen.Tickets },
                            icon = { Text("🎫") },
                            label = { Text("My tickets (${state.tickets.size})") },
                            modifier = Modifier.testTag(NavTags.TAB_TICKETS),
                        )
                    }
                },
            ) { padding ->
                Surface(modifier = Modifier.padding(padding)) {
                    when (val screen = state.screen) {
                        is TheaterScreen.Playbill -> PlaybillScreen(
                            state = state,
                            columns = playbillColumns,
                            onOpenPerformance = { performance ->
                                state.screen = TheaterScreen.PerformanceDetails(performance.id)
                            },
                        )

                        is TheaterScreen.PerformanceDetails -> PerformanceScreen(
                            state = state,
                            performanceId = screen.performanceId,
                            expandedLayout = expanded,
                            onBack = { state.screen = TheaterScreen.Playbill },
                            onShowSnackbar = { message ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                            },
                        )

                        is TheaterScreen.Tickets -> TicketsScreen(state = state)
                    }
                }
            }
        }
    }
}
