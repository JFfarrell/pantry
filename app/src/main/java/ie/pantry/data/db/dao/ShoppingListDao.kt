package ie.pantry.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem
import ie.pantry.data.db.relation.ShoppingListWithItems
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShoppingListDao {

    @Query("SELECT * FROM shopping_list ORDER BY createdAt DESC, id DESC")
    abstract fun observeLists(): Flow<List<ShoppingList>>

    /** Emits null while the list is absent. */
    @Transaction
    @Query("SELECT * FROM shopping_list WHERE id = :id")
    abstract fun observeList(id: Long): Flow<ShoppingListWithItems?>

    /**
     * Inserts the list and its items in one transaction, assigning the new list id to every item.
     * If any item insert fails, no list row persists.
     * @return the new list id.
     */
    @Transaction
    open suspend fun insertListWithItems(list: ShoppingList, items: List<ShoppingListItem>): Long {
        val id = rawInsertList(list)
        if (items.isNotEmpty()) rawInsertItems(items.map { it.copy(shoppingListId = id) })
        return id
    }

    /** @return rows updated. */
    @Update
    abstract suspend fun updateItem(item: ShoppingListItem): Int

    /** Deletes the list; its items and any retailer-assist session cascade. @return rows deleted. */
    @Query("DELETE FROM shopping_list WHERE id = :id")
    abstract suspend fun deleteList(id: Long): Int

    @Insert
    protected abstract suspend fun rawInsertList(list: ShoppingList): Long

    @Insert
    protected abstract suspend fun rawInsertItems(items: List<ShoppingListItem>)
}
