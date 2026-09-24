package ie.pantry.data.db

import android.database.sqlite.SQLiteConstraintException
import ie.pantry.data.db.dao.RetailerAssistDao
import ie.pantry.data.db.dao.ShoppingListDao
import ie.pantry.data.db.entity.QuantityDimension
import ie.pantry.data.db.entity.RetailerAssistSession
import ie.pantry.data.db.entity.SelectionSnapshot
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.data.db.entity.ShoppingListItem
import ie.pantry.testutil.FlowRecorder
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R4: list `Flow`s emit after every write, and a list is written atomically with its items. */
@RunWith(RobolectricTestRunner::class)
class ShoppingListDaoTest {

    private val clock = MutableClock()
    private lateinit var db: PantryDatabase
    private lateinit var lists: ShoppingListDao
    private lateinit var sessions: RetailerAssistDao

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(clock)
        lists = db.shoppingListDao()
        sessions = db.retailerAssistDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun list(selection: List<SelectionSnapshot> = emptyList()) =
        ShoppingList(createdAt = clock.instant(), sourceSelection = selection)

    private fun item(key: String, walk: Int, id: Long = 0) = ShoppingListItem(
        id = id,
        shoppingListId = 0,
        canonicalKey = key,
        displayName = key.replaceFirstChar(Char::uppercase),
        quantityAmount = null,
        quantityUnit = null,
        dimension = QuantityDimension.UNQUANTIFIED,
        section = null,
        walkOrderIndex = walk,
    )

    private fun count(table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `lists flow emits empty list when none`() = runTest {
        val recorder = FlowRecorder(lists.observeLists(), backgroundScope)

        assertEquals(emptyList(), recorder.awaitNext())
    }

    @Test
    fun `list flow emits null for missing id`() = runTest {
        val recorder = FlowRecorder(lists.observeList(404L), backgroundScope)

        assertNull(recorder.awaitNext())
    }

    @Test
    fun `lists and list flows emit after insertListWithItems`() = runTest {
        val listsRecorder = FlowRecorder(lists.observeLists(), backgroundScope)
        assertEquals(emptyList(), listsRecorder.awaitNext())
        // The first row of a fresh database gets rowid 1.
        val listRecorder = FlowRecorder(lists.observeList(1L), backgroundScope)
        assertNull(listRecorder.awaitNext())

        val id = lists.insertListWithItems(list(), listOf(item("flour", 0), item("eggs", 1)))

        assertEquals(1L, id)
        assertEquals(listOf(id), listsRecorder.awaitUntil { it.isNotEmpty() }.map { it.id })
        val withItems = assertNotNull(listRecorder.awaitUntil { it != null })
        assertEquals(setOf("flour", "eggs"), withItems.items.map { it.canonicalKey }.toSet())
        assertEquals(setOf(id), withItems.items.map { it.shoppingListId }.toSet())
    }

    @Test
    fun `list flow emits after updateItem`() = runTest {
        val id = lists.insertListWithItems(list(), listOf(item("flour", 0)))
        val recorder = FlowRecorder(lists.observeList(id), backgroundScope)
        val stored = assertNotNull(recorder.awaitNext()).items.single()

        assertEquals(1, lists.updateItem(stored.copy(displayName = "Plain flour")))

        val updated = assertNotNull(recorder.awaitUntil { it?.items?.singleOrNull()?.displayName == "Plain flour" })
        assertEquals("Plain flour", updated.items.single().displayName)
    }

    @Test
    fun `lists and list flows emit after deleteList`() = runTest {
        val id = lists.insertListWithItems(list(), listOf(item("flour", 0)))
        val listsRecorder = FlowRecorder(lists.observeLists(), backgroundScope)
        val listRecorder = FlowRecorder(lists.observeList(id), backgroundScope)
        assertEquals(1, listsRecorder.awaitNext().size)
        assertNotNull(listRecorder.awaitNext())

        assertEquals(1, lists.deleteList(id))

        assertEquals(emptyList(), listsRecorder.awaitUntil { it.isEmpty() })
        assertNull(listRecorder.awaitUntil { it == null })
    }

    @Test
    fun `insertListWithItems is atomic when an item insert fails`() = runTest {
        // Two items with the same explicit primary key: the second insert violates the PK.
        val clashing = listOf(item("flour", 0, id = 7), item("eggs", 1, id = 7))

        assertFailsWith<SQLiteConstraintException> { lists.insertListWithItems(list(), clashing) }

        assertEquals(0, count("shopping_list"), "no list row may persist after a failed item insert")
        assertEquals(0, count("shopping_list_item"))
    }

    @Test
    fun `source selection snapshot round-trips`() = runTest {
        val selection = listOf(SelectionSnapshot(1L, 4), SelectionSnapshot(2L, null))

        val id = lists.insertListWithItems(list(selection), emptyList())

        assertEquals(selection, assertNotNull(lists.observeList(id).first()).shoppingList.sourceSelection)
    }

    @Test
    fun `deleting list cascades items and session`() = runTest {
        val id = lists.insertListWithItems(list(), listOf(item("flour", 0), item("eggs", 1)))
        sessions.upsert(RetailerAssistSession(id, positionIndex = 1, skippedItemIds = listOf(1L)))
        assertEquals(2, count("shopping_list_item"))
        assertEquals(1, count("retailer_assist_session"))

        lists.deleteList(id)

        assertEquals(0, count("shopping_list_item"))
        assertEquals(0, count("retailer_assist_session"))
    }
}
