package kabuki.sample.screens

import kabuki.page.ListItem
import kabuki.page.ListItemScope
import kabuki.page.Screen
import kabuki.sample.ui.ReviewTags

/**
 * Reviews modal: a long lazy list with typed items - the main LazyList DSL demo.
 */
class ReviewsDialog : Screen<ReviewsDialog>() {

    override val root = node(ReviewTags.DIALOG)

    val closeButton = node(ReviewTags.CLOSE_BUTTON)

    val reviews = lazyList(ReviewTags.LIST) {
        itemType(::ReviewItem)
    }
}

class ReviewItem(scope: ListItemScope) : ListItem(scope) {
    val author = child(ReviewTags.ITEM_AUTHOR)
    val rating = child(ReviewTags.ITEM_RATING)
    val text = child(ReviewTags.ITEM_TEXT)
}
