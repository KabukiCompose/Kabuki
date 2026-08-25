package kabuki.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kabuki.sample.TheaterState
import kabuki.sample.model.Performance
import kabuki.semantics.testBackgroundColor
import kabuki.semantics.testTag

enum class PerformanceTags {
    SCREEN,
    LOADER,
    TITLE,
    POSTER,
    SELECT_SEATS_BUTTON,
    REVIEWS_BUTTON,
}

@Composable
fun PerformanceScreen(
    state: TheaterState,
    performanceId: String,
    expandedLayout: Boolean,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var details by remember { mutableStateOf<Performance?>(null) }
    LaunchedEffect(performanceId) {
        details = state.repository.loadDetails(performanceId)
    }

    var seatDialogOpen by remember { mutableStateOf(false) }
    var reviewsDialogOpen by remember { mutableStateOf(false) }

    val loaded = details
    if (loaded == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(PerformanceTags.LOADER))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(PerformanceTags.SCREEN),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag(NavTags.BACK_BUTTON),
        ) {
            Text("← Back to playbill")
        }

        Text(
            text = loaded.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp).testTag(PerformanceTags.TITLE),
        )
        Text(
            text = "${loaded.genre.displayName} - ${loaded.durationMinutes} min",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Adaptive layout: poster and description side by side on wide windows, stacked on narrow ones
        if (expandedLayout) {
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Poster()
                DescriptionBlock(
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                    performance = loaded,
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Poster()
                DescriptionBlock(
                    modifier = Modifier.padding(top = 16.dp),
                    performance = loaded,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Button(
                onClick = { seatDialogOpen = true },
                modifier = Modifier.testTag(PerformanceTags.SELECT_SEATS_BUTTON),
            ) {
                Text("Select seats - from ¥${loaded.price}")
            }
            TextButton(
                onClick = { reviewsDialogOpen = true },
                modifier = Modifier.testTag(PerformanceTags.REVIEWS_BUTTON),
            ) {
                Text("Reviews")
            }
        }
    }

    if (reviewsDialogOpen) {
        ReviewsDialog(
            performance = loaded,
            repository = state.repository,
            onDismiss = { reviewsDialogOpen = false },
        )
    }

    if (seatDialogOpen) {
        SeatPickerDialog(
            performance = loaded,
            repository = state.repository,
            onDismiss = { seatDialogOpen = false },
            onBuy = { ticket ->
                state.tickets += ticket
                seatDialogOpen = false
                onShowSnackbar("Ticket purchased: row ${ticket.row}, seat ${ticket.number}")
            },
        )
    }
}

/** Poster background - fixed (not theme-based) so tests can assert it exactly. */
val PosterBackground = Color(0xFF3E2E4D)

@Composable
private fun Poster() {
    // The background color is published to semantics: colors are not part of
    // the semantics tree, so tests can only see what production code publishes
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 280.dp)
            .background(PosterBackground)
            .testTag(PerformanceTags.POSTER)
            .testBackgroundColor(PosterBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "歌舞伎",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
    }
}

@Composable
private fun DescriptionBlock(
    performance: Performance,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = performance.fullDescription, style = MaterialTheme.typography.bodyLarge)
        Text(text = "Cast:", style = MaterialTheme.typography.titleSmall)
        performance.actors.forEach { actor ->
            Text(text = "- $actor", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
