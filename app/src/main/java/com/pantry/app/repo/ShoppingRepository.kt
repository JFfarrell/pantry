package com.pantry.app.repo

import com.pantry.app.data.db.ShoppingDao
import com.pantry.app.data.db.ShoppingItemEntity
import com.pantry.app.data.db.ShoppingListEntity
import com.pantry.app.shopping.Aisles
import com.pantry.app.shopping.ShoppingListBuilder
import com.pantry.app.shopping.ShoppingSource
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val dao: ShoppingDao) {

    fun observeLists(): Flow<List<ShoppingListEntity>> = dao.observeLists()
    fun observeLatestList(): Flow<ShoppingListEntity?> = dao.observeLatestList()
    fun observeItems(listId: String): Flow<List<ShoppingItemEntity>> = dao.observeItems(listId)

    suspend fun generate(name: String, sources: List<ShoppingSource>): String {
        val list = ShoppingListEntity(name = name)
        dao.insertList(list)
        dao.insertItems(ShoppingListBuilder.toEntities(list.id, ShoppingListBuilder.build(sources)))
        return list.id
    }

    suspend fun addManualItem(listId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.insertItems(
            listOf(
                ShoppingItemEntity(
                    listId = listId,
                    name = trimmed,
                    displayQuantity = "",
                    aisle = Aisles.classify(trimmed),
                    addedManually = true,
                    position = 9999
                )
            )
        )
    }

    suspend fun setChecked(id: Long, checked: Boolean) = dao.setChecked(id, checked)
    suspend fun deleteItem(id: Long) = dao.deleteItem(id)
    suspend fun deleteList(id: String) = dao.deleteList(id)
    /**
     * Sorted the same way the list is drawn. The DAO cannot express walking
     * order in SQL -- ordering on the aisle column sorts alphabetically, which
     * put the queue and the visible list into different orders.
     */
    suspend fun uncheckedItems(listId: String): List<ShoppingItemEntity> =
        dao.getUncheckedItems(listId)
            .sortedWith(compareBy({ Aisles.walkingOrder(it.aisle) }, { it.position }))

    /** Ticks off the items a Tesco run reported as added. */
    suspend fun checkOff(listId: String, names: List<String>) {
        if (names.isNotEmpty()) dao.checkOff(listId, names)
    }
}
