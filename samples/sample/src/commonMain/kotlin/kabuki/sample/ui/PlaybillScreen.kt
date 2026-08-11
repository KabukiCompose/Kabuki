package kabuki.sample.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kabuki.semantics.testListItem
import kabuki.semantics.testListLength
import kabuki.semantics.testTag
import androidx.compose.ui.unit.dp
import kabuki.sample.TheaterState
import kabuki.sample.model.Genre
import kabuki.sample.model.Performance

/** Placeholder cards shown while the playbill loads. */
private const val SHIMMER_CARD_COUNT = 6

enum class PlaybillTags {
    SCREEN,
    SHIMMER,
    SHIMMER_CARD,
    LIST,
    GENRE_FILTER,

    /** + genre name, or "ALL" */
    GENRE_OPTION,

    /** + performance id */
    CARD,
    CARD_TITLE,
    CARD_PRICE,
}

@Composable
fun PlaybillScreen(
    state: TheaterState,
    columns: Int,
    onOpenPerformance: (Performance) -> Unit,
) {
    var performances by remember { mutableStateOf<List<Performance>?>(null) }
    LaunchedEffect(Unit) {
        performances = state.repository.loadPerformances()
    }

    var genreFilter by remember { mutableStateOf<Genre?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag(PlaybillTags.SCREEN)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Playbill", style = MaterialTheme.typography.headlineMedium)
            GenreFilterDropdown(
                selected = genreFilter,
                onSelected = { genreFilter = it },
            )
        }

        val loaded = performances
        if (loaded == null) {
            // The shimmer tag lives on the grid: a single node for assertions,
            // no matter how many placeholder cards are inside. The count is
            // published too - on a small screen only a few placeholders are
            // composed, and a test asserting "six placeholders" must still hold.
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(PlaybillTags.SHIMMER)
                    .testListLength(SHIMMER_CARD_COUNT),
            ) {
                items(SHIMMER_CARD_COUNT) { ShimmerCard() }
            }
        } else {
            val filtered = loaded.filter { genreFilter == null || it.genre == genreFilter }
            // A lazy GRID with list semantics: testListItem/testListLength work
            // for grids exactly like for LazyColumn
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(PlaybillTags.LIST)
                    .testListLength(filtered.size),
            ) {
                itemsIndexed(filtered, key = { _, performance -> performance.id }) { index, performance ->
                    PerformanceCard(
                        performance = performance,
                        onClick = { onOpenPerformance(performance) },
                        modifier = Modifier.testListItem(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreFilterDropdown(
    selected: Genre?,
    onSelected: (Genre?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag(PlaybillTags.GENRE_FILTER),
        ) {
            Text(selected?.displayName ?: "All genres")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("All genres") },
                onClick = {
                    onSelected(null)
                    menuOpen = false
                },
                modifier = Modifier.testTag(PlaybillTags.GENRE_OPTION, "ALL"),
            )
            Genre.entries.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre.displayName) },
                    onClick = {
                        onSelected(genre)
                        menuOpen = false
                    },
                    modifier = Modifier.testTag(PlaybillTags.GENRE_OPTION, genre.name),
                )
            }
        }
    }
}

@Composable
private fun PerformanceCard(
    modifier: Modifier = Modifier,
    performance: Performance,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(PlaybillTags.CARD, performance.id),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = performance.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(PlaybillTags.CARD_TITLE),
            )
            Text(
                text = performance.genre.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = performance.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "from ¥${performance.price}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp).testTag(PlaybillTags.CARD_PRICE),
            )
        }
    }
}

/** Shimmer placeholder: an infinite alpha animation - a stress test for idle synchronization. */
@Composable
private fun ShimmerCard() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
    )
    Card(modifier = Modifier.fillMaxWidth().testTag(PlaybillTags.SHIMMER_CARD)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
