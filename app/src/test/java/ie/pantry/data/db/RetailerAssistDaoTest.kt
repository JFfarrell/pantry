package ie.pantry.data.db

import ie.pantry.data.db.dao.RetailerAssistDao
import ie.pantry.data.db.dao.ShoppingListDao
import ie.pantry.data.db.entity.RetailerAssistSession
import ie.pantry.data.db.entity.ShoppingList
import ie.pantry.testutil.FlowRecorder
import ie.pantry.testutil.MutableClock
import ie.pantry.testutil.TestDatabases
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R4 for `observeSession`: emits after upsert (insert and update) and delete; null when absent. */
@RunWith(RobolectricTestRunner::class)
class RetailerAssistDaoTest {

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

    private suspend fun seedList(): Long =
        lists.insertListWithItems(ShoppingList(createdAt = clock.instant(), sourceSelection = emptyList()), emptyList())

    @Test
    fun `session flow emits null when no session`() = runTest {
        val id = seedList()
        val recorder = FlowRecorder(sessions.observeSession(id), backgroundScope)

        assertNull(recorder.awaitNext())
    }

    @Test
    fun `session flow emits after upsert insert`() = runTest {
        val id = seedList()
        val recorder = FlowRecorder(sessions.observeSession(id), backgroundScope)
        assertNull(recorder.awaitNext())

        sessions.upsert(RetailerAssistSession(id, positionIndex = 2, skippedItemIds = listOf(5L, 9L)))

        val session = assertNotNull(recorder.awaitUntil { it != null })
        assertEquals(2, session.positionIndex)
        assertEquals(listOf(5L, 9L), session.skippedItemIds)
    }

    @Test
    fun `session flow emits after upsert update`() = runTest {
        val id = seedList()
        sessions.upsert(RetailerAssistSession(id, positionIndex = 1, skippedItemIds = emptyList()))
        val recorder = FlowRecorder(sessions.observeSession(id), backgroundScope)
        assertEquals(1, assertNotNull(recorder.awaitNext()).positionIndex)

        sessions.upsert(RetailerAssistSession(id, positionIndex = 4, skippedItemIds = listOf(3L)))

        val updated = assertNotNull(recorder.awaitUntil { it?.positionIndex == 4 })
        assertEquals(listOf(3L), updated.skippedItemIds)
    }

    @Test
    fun `session flow emits after delete`() = runTest {
        val id = seedList()
        sessions.upsert(RetailerAssistSession(id, positionIndex = 1, skippedItemIds = emptyList()))
        val recorder = FlowRecorder(sessions.observeSession(id), backgroundScope)
        assertNotNull(recorder.awaitNext())

        assertEquals(1, sessions.delete(id))

        assertNull(recorder.awaitUntil { it == null })
    }

    @Test
    fun `empty skipped ids round-trip as empty list`() = runTest {
        val id = seedList()

        sessions.upsert(RetailerAssistSession(id, positionIndex = 0, skippedItemIds = emptyList()))

        val stored = assertNotNull(FlowRecorder(sessions.observeSession(id), backgroundScope).awaitNext())
        // An empty set is not absence: it must come back as an empty list, never null.
        assertEquals(emptyList(), stored.skippedItemIds)
    }
}
