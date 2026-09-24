package ie.pantry.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ie.pantry.data.db.entity.RetailerAssistSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RetailerAssistDao {

    /** Emits null while the list has no session. */
    @Query("SELECT * FROM retailer_assist_session WHERE shoppingListId = :listId")
    fun observeSession(listId: Long): Flow<RetailerAssistSession?>

    /** Inserts the session, or replaces the list's existing one. */
    @Upsert
    suspend fun upsert(session: RetailerAssistSession)

    /** @return rows deleted. */
    @Query("DELETE FROM retailer_assist_session WHERE shoppingListId = :listId")
    suspend fun delete(listId: Long): Int
}
