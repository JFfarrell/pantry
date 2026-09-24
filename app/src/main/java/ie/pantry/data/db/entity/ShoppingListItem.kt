package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_list_item",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shoppingListId")],
)
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shoppingListId: Long,
    val canonicalKey: String,
    val displayName: String,
    /** Null iff [dimension] is [QuantityDimension.UNQUANTIFIED]. */
    val quantityAmount: Double?,
    /** Null iff [dimension] is [QuantityDimension.UNQUANTIFIED]. */
    val quantityUnit: String?,
    val dimension: QuantityDimension,
    /** Absent means an unknown section (the terminal bucket). */
    val section: String?,
    /** Stable walk order, >= 0. */
    val walkOrderIndex: Int,
)
