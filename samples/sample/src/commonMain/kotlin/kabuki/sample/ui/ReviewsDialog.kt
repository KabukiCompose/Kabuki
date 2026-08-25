package kabuki.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kabuki.sample.data.TheaterRepository
import kabuki.sample.model.Performance
import kabuki.sample.model.Review
import kabuki.semantics.testListItem
import kabuki.semantics.testListLength
import kabuki.semantics.testTag

/** Star rating color - fixed (not theme-based) so tests can assert it exactly. */
val RatingGold = Color(0xFFC9A227)

/** Stars in the rating widget. */
private const val MAX_RATING = 5

enum class ReviewTags {
    DIALOG,
    CLOSE_BUTTON,
    LOADER,
    LIST,
    ITEM_AUTHOR,
    ITEM_RATING,
    ITEM_TEXT,
}

/**
 * Full-height reviews dialog: a long lazy list inside a modal.
 * Items are marked with testListItem(index) and the container publishes its
 * full length via testListLength - the Kabuki LazyList DSL relies on both.
 */
@Composable
fun ReviewsDialog(
    performance: Performance,
    repository: TheaterRepository,
    onDismiss: () -> Unit,
) {
    var reviews by remember { mutableStateOf<List<Review>?>(null) }
    LaunchedEffect(performance.id) {
        reviews = repository.loadReviews(performance.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag(ReviewTags.DIALOG)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Reviews", style = MaterialTheme.typography.headlineSmall)
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag(ReviewTags.CLOSE_BUTTON),
                    ) {
                        Text("Close")
                    }
                }
                Text(
                    text = performance.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )

                val loaded = reviews
                if (loaded == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(ReviewTags.LOADER))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag(ReviewTags.LIST)
                            .testListLength(loaded.size),
                    ) {
                        itemsIndexed(loaded) { index, review ->
                            ReviewCard(review = review, modifier = Modifier.testListItem(index))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: Review,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = review.author,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(ReviewTags.ITEM_AUTHOR),
                )
                Text(
                    text = "★".repeat(review.rating) + "☆".repeat(MAX_RATING - review.rating),
                    style = MaterialTheme.typography.titleSmall,
                    color = RatingGold,
                    modifier = Modifier.testTag(ReviewTags.ITEM_RATING),
                )
            }
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp).testTag(ReviewTags.ITEM_TEXT),
            )
        }
    }
}
