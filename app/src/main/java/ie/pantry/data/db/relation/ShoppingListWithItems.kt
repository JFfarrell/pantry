package ie.pantry.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem

data class ShoppingListWithItems(
    @Embedded val shoppingList: ShoppingList,
    @Relation(parentColumn = "id", entityColumn = "shoppingListId")
    val items: List<ShoppingListItem>,
)
