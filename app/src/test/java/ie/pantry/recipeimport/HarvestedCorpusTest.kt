package ie.pantry.recipeimport

import ie.pantry.data.gateway.BodyEncoding
import ie.pantry.data.gateway.FetchedBody
import ie.pantry.testutil.CorpusEntry
import ie.pantry.testutil.CorpusOrigin
import ie.pantry.testutil.LineKind
import ie.pantry.testutil.ParseFidelityCorpus
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Job

/**
 * R10 and CFC-1: the `F5_HARVESTED` rows of F3's corpus are bound to committed page snapshots through
 * `harvest/snapshots.tsv`, and every harvested line is byte-identical to a line F5's own extraction produces from
 * its snapshot. No gateway and no image request: each snapshot goes straight through the reader and the extractor.
 */
class HarvestedCorpusTest {

    private val harvestDir = File(requireNotNull(javaClass.getResource("/recipeimport/harvest/snapshots.tsv")).toURI()).parentFile

    private class IndexRow(val sourceUrl: String, val retrieved: String, val path: String)

    private val index: List<IndexRow> = harvestDir.resolve("snapshots.tsv").readLines(Charsets.UTF_8).drop(1)
        .filter { it.isNotBlank() }
        .map { line -> line.split("\t").also { check(it.size == 3) { "malformed snapshots.tsv row" } }.let { IndexRow(it[0], it[1], it[2]) } }

    private val corpus: List<CorpusEntry> = ParseFidelityCorpus.load()
    private val harvested = corpus.filter { it.origin == CorpusOrigin.F5_HARVESTED && it.lineKind == LineKind.INGREDIENT }

    private fun indexEntryFor(entry: CorpusEntry): List<IndexRow> =
        index.filter { it.sourceUrl == entry.sourceUrl && it.retrieved == entry.retrieved.toString() }

    private fun host(url: String): String = URI(url).host.removePrefix("www.")

    private fun extractedLines(path: String): List<String> {
        val document = BoundedHtmlReader.parse(
            FetchedBody(harvestDir.resolve(path).readBytes(), BodyEncoding.IDENTITY, "text/html"),
            "https://example.invalid/", DECOMPRESSED_CAP_BYTES, Job(),
        ) {}
        return RecipeExtractor.extract(document, Job())?.recipe?.lines.orEmpty()
    }

    @Test
    fun `the corpus holds at least twenty harvested ingredient rows from at least three hosts`() {
        assertTrue(harvested.size >= 20, "only ${harvested.size} harvested ingredient rows")
        assertTrue(harvested.map { host(it.sourceUrl) }.toSet().size >= 3, "hosts: ${harvested.map { host(it.sourceUrl) }.toSet()}")
        for (entry in harvested) {
            assertTrue(entry.sourceUrl.startsWith("http"), "row ${entry.row} has no source url")
        }
    }

    @Test
    fun `no harvested line repeats a hand transcribed line`() {
        val hand = corpus.filter { it.origin == CorpusOrigin.HAND_TRANSCRIBED }.map { it.line }.toSet()
        val repeats = harvested.filter { it.line in hand }.map { "row ${it.row}" }
        assertEquals(emptyList(), repeats)
    }

    @Test
    fun `every harvested row has one index entry and a snapshot`() {
        for (entry in harvested) {
            val matches = indexEntryFor(entry)
            assertEquals(1, matches.size, "row ${entry.row} (${entry.sourceUrl}) needs exactly one index entry")
            assertTrue(harvestDir.resolve(matches.single().path).isFile, "row ${entry.row} has no snapshot file")
        }
    }

    @Test
    fun `every harvested line is byte identical to a line extracted from its snapshot`() {
        val failures = mutableListOf<String>()
        for (entry in harvested) {
            val path = indexEntryFor(entry).single().path
            val lines = extractedLines(path)
            if (entry.line !in lines) {
                failures += "row ${entry.row} line [${entry.line}] not among the ${lines.size} lines extracted from $path:\n" +
                    lines.joinToString("\n") { "    $it" }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `harvested lines have no tab line break or leading hash`() {
        for (entry in harvested) {
            assertTrue('\t' !in entry.line && '\n' !in entry.line && '\r' !in entry.line, "row ${entry.row} has a tab or line break")
            assertTrue(!entry.line.startsWith("#"), "row ${entry.row} starts with a hash")
            assertTrue(entry.line.isNotEmpty(), "row ${entry.row} is empty")
        }
    }

    @Test
    fun `snapshots are small trimmed utf 8 files with unique paths`() {
        assertEquals(index.map { it.path }.toSet().size, index.size, "paths must be unique")
        assertEquals(index.map { it.sourceUrl to it.retrieved }.toSet().size, index.size, "(source_url, retrieved) must be unique")
        val trimComment = "<!-- F5 harvest snapshot, trimmed:"
        for (row in index) {
            val file = harvestDir.resolve(row.path)
            assertTrue(file.isFile, "${row.path} is missing")
            assertTrue(file.length() <= 1024 * 1024, "${row.path} is ${file.length()} bytes")
            val bytes = file.readBytes()
            // Decoding strictly proves it is valid UTF-8.
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            assertTrue(String(bytes, Charsets.UTF_8).startsWith(trimComment), "${row.path} lacks the trim comment")
        }
    }
}
