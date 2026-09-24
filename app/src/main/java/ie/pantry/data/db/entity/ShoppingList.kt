package ie.pantry.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "shopping_list")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Instant,
    /** The selections this list was generated from. A snapshot, not a foreign key: it outlives recipe deletion. */
    val sourceSelection: List<SelectionSnapshot>,
)
