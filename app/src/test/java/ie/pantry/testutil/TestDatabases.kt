package ie.pantry.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ie.pantry.data.db.PantryDatabase
import java.time.Clock

/** Real Room databases for tests: no mocks. Callers close what they open. */
object TestDatabases {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A fresh in-memory database; create it in `@Before` and close it in `@After`. */
    fun inMemory(clock: Clock): PantryDatabase = PantryDatabase.createInMemory(context, clock)

    /** A database file named [name] under the Robolectric app context's database directory. */
    fun onDisk(name: String, clock: Clock): PantryDatabase = PantryDatabase.createAt(context, name, clock)
}
