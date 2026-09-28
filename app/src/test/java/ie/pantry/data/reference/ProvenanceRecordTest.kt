package ie.pantry.data.reference

import ie.pantry.testutil.RepoPaths
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R3 AC4: `docs/reference-data-provenance.md` names a source and a retrieval date for each dataset, each
 * confined to its own `## ` section — a line in one section must never satisfy another (design `[SEAL-21]`,
 * spec `[SEAL-07]`, `[SEAL-17]`).
 */
@RunWith(RobolectricTestRunner::class)
class ProvenanceRecordTest {

    private val expectedHeadings = listOf("Staples", "Aliases", "Seasonality", "Section order")

    private fun provenanceFile(): File = File(RepoPaths.repoRoot(), "docs/reference-data-provenance.md")

    /** Splits [text] at lines matching exactly `## <heading>` (two hashes); a `### ` subsection line stays
     * inside its parent's body, since it does not match the two-hash-then-space prefix. */
    private fun splitSections(text: String): Map<String, String> {
        val sections = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in text.lines()) {
            if (line.startsWith("## ")) {
                current = StringBuilder()
                sections[line.removePrefix("## ").trim()] = current
            } else {
                current?.append(line)?.append('\n')
            }
        }
        return sections.mapValues { it.value.toString() }
    }

    private fun sourceLine(body: String): String? =
        Regex("""^- \*\*Source:\*\* (.+)$""", RegexOption.MULTILINE).find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun retrievedDate(body: String): String? =
        Regex("""^- \*\*Retrieved:\*\* (\d{4}-\d{2}-\d{2})$""", RegexOption.MULTILINE).find(body)?.groupValues?.get(1)

    @Test
    fun `provenance record exists at the repository docs path`() {
        assertTrue(provenanceFile().isFile, "expected ${provenanceFile()} to exist")
    }

    @Test
    fun `provenance record has the four exact dataset headings`() {
        val sections = splitSections(provenanceFile().readText())

        for (heading in expectedHeadings) {
            assertTrue(heading in sections, "missing heading '## $heading'")
        }
    }

    @Test
    fun `each dataset section has its own non-empty Source line`() {
        val sections = splitSections(provenanceFile().readText())

        for (heading in expectedHeadings) {
            val body = requireNotNull(sections[heading]) { "missing heading '## $heading'" }
            assertNotNull(sourceLine(body), "section '$heading' has no non-empty Source line")
        }
    }

    @Test
    fun `each dataset section has its own Retrieved date that parses as an ISO date`() {
        val sections = splitSections(provenanceFile().readText())

        for (heading in expectedHeadings) {
            val body = requireNotNull(sections[heading]) { "missing heading '## $heading'" }
            val date = requireNotNull(retrievedDate(body)) { "section '$heading' has no Retrieved date" }
            LocalDate.parse(date)
        }
    }

    @Test
    fun `a Retrieved date in a neighbouring section does not satisfy a section missing its own`() {
        val synthetic = """
            ## Alpha
            - **Source:** something
            - **Retrieved:** 2026-01-01

            ## Beta
            - **Source:** something else
        """.trimIndent()
        val sections = splitSections(synthetic)

        assertNotNull(retrievedDate(requireNotNull(sections["Alpha"])))
        assertEquals(null, retrievedDate(requireNotNull(sections["Beta"])), "Beta's missing Retrieved must not be satisfied by Alpha's")
        assertFailsWith<DateTimeParseException> { LocalDate.parse("not-a-date") }
    }
}
