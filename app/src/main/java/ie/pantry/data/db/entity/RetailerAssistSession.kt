package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "retailer_assist_session",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RetailerAssistSession(
    @PrimaryKey val shoppingListId: Long,
    /** Pointer into the walk order, >= 0. */
    val positionIndex: Int,
    /** Skipped `ShoppingListItem` ids; an empty list means none skipped, not absence. */
    val skippedItemIds: List<Long>,
)
