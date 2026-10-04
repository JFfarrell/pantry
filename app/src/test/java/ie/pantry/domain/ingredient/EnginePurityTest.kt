package ie.pantry.domain.ingredient

import ie.pantry.testutil.RepoPaths
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** R9: the engine is plain Kotlin, so its behaviour tests run in seconds on the JVM. */
class EnginePurityTest {

    @Test
    fun `main sources reference neither android nor org json`() {
        for (file in mainSourceFiles()) {
            val text = file.readText()
            assertFalse(text.contains("android."), "${file.name} must not reference android.")
            assertFalse(text.contains("org.json"), "${file.name} must not reference org.json")
        }
    }

    @Test
    fun `data layer imports are limited to AliasTable Lookup and QuantityDimension`() {
        val allowed = setOf(
            "import ie.pantry.data.reference.AliasTable",
            "import ie.pantry.data.reference.Lookup",
            "import ie.pantry.data.db.entity.QuantityDimension",
        )
        for (file in mainSourceFiles()) {
            for (line in file.readLines()) {
                val trimmed = line.trimStart().trimEnd()
                if (trimmed.startsWith("import ie.pantry.data.")) {
                    assertTrue(trimmed in allowed, "${file.name}: unexpected data-layer import: $trimmed")
                }
            }
        }
    }

    @Test
    fun `behaviour test classes do not use the Robolectric runner`() {
        val classesRequiringNoRobolectric = listOf(
            EntryPointTest::class.java,
            CanonicalKeyRuleTest::class.java,
            QuantityParserTest::class.java,
            ConversionScalingMergeTest::class.java,
            EngineErrorHygieneTest::class.java,
            EnginePurityTest::class.java,
        )
        for (clazz in classesRequiringNoRobolectric) {
            val runWith = clazz.getAnnotation(RunWith::class.java)
            assertNull(runWith, "${clazz.simpleName} must not carry @RunWith")
        }
    }

    private fun mainSourceFiles(): List<java.io.File> {
        val dir = java.io.File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/domain/ingredient")
        return dir.listFiles { f -> f.extension == "kt" }?.toList().orEmpty()
    }
}
