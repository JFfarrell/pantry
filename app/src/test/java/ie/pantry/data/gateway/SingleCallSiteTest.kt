package ie.pantry.data.gateway

import ie.pantry.testutil.RepoPaths
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * R1 AC1 and AC3, R9 AC4: static guards over the main sources and the public surface.
 *
 * The raw-text checks have a blind spot (`[DEF-06]`): they see only the text of a throw, `require(`, `check(` or
 * `error(` argument, not a message built elsewhere and passed in by name. Two things close it. AD1's
 * `GatewayException` has a constructor that takes no `String`, so no content can be passed to it at all, and the
 * end-to-end sentinel fixtures in `GatewayErrorHygieneTest` prove no failure path leaks one.
 */
class SingleCallSiteTest {

    private val mainRoot = File(RepoPaths.repoRoot(), "app/src/main/java")
    private val gatewayDir = File(mainRoot, "ie/pantry/data/gateway")

    private fun mainSources(): List<File> = mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun gatewaySources(): List<File> = gatewayDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relative(file: File): String = file.relativeTo(mainRoot).path.replace(File.separatorChar, '/')

    @Test
    fun `only gateway package files import okhttp3`() {
        assertTrue(mainSources().size > gatewaySources().size, "expected main sources outside the gateway package")
        val offenders = mainSources()
            .filter { "import okhttp3." in it.readText() }
            .map(::relative)
            .filterNot { it.startsWith("ie/pantry/data/gateway/") }

        assertTrue(offenders.isEmpty(), "okhttp3 is imported outside the gateway package: $offenders")
    }

    @Test
    fun `no main source uses another http api`() {
        val forbidden = listOf("HttpURLConnection", "openConnection", "openStream", "java.net.http")
        val offenders = mainSources().flatMap { file ->
            val text = file.readText()
            forbidden.filter { it in text }.map { "${relative(file)}: $it" }
        }

        assertTrue(offenders.isEmpty(), offenders.toString())
    }

    @Test
    fun `gateway seams and the gateway constructor are reached only from their own files`() {
        fun filesContaining(token: String): List<String> = mainSources().filter { token in it.readText() }.map { it.name }

        assertEquals(listOf("GatewaySeams.kt"), filesContaining("GatewaySeams(").distinct())
        assertEquals(listOf("ExternalDataGateway.kt"), filesContaining("ExternalDataGateway(").distinct())
    }

    private val publicClasses: List<Class<*>> = listOf(
        ExternalDataGateway::class.java,
        ExternalDataGateway.Companion::class.java,
        GatewayResult::class.java,
        GatewayResult.Fetched::class.java,
        GatewayResult.Failed::class.java,
        FetchedBody::class.java,
        BodyEncoding::class.java,
        CallType::class.java,
        GatewayException::class.java,
        GatewayException.Category::class.java,
        GatewayPolicy::class.java,
        GatewayPolicy.Companion::class.java,
    )

    /** JVM-public methods, skipping the `$`-named ones the compiler generates for Kotlin-internal members. */
    private fun publicMethods(type: Class<*>): List<Method> =
        type.declaredMethods.filter { Modifier.isPublic(it.modifiers) && '$' !in it.name }

    @Test
    fun `the gateway exposes exactly three public suspend operations`() {
        val suspending = publicMethods(ExternalDataGateway::class.java)
            .filter { it.parameterTypes.lastOrNull() == Continuation::class.java }
            .map { it.name }
            .sorted()

        assertEquals(listOf("fetchImage", "fetchPage", "lookupNutrition"), suspending)
    }

    @Test
    fun `no public signature in the gateway package mentions an okhttp3 type`() {
        val signatures = publicClasses.flatMap { type ->
            val methods = publicMethods(type).map { m ->
                "${type.simpleName}.${m.name}(${m.genericParameterTypes.joinToString()}): ${m.genericReturnType}"
            }
            val fields = type.declaredFields
                .filter { Modifier.isPublic(it.modifiers) && '$' !in it.name }
                .map { "${type.simpleName}.${it.name}: ${it.genericType}" }
            val constructors = type.declaredConstructors
                .filter { Modifier.isPublic(it.modifiers) }
                .map { "${type.simpleName}.<init>(${it.genericParameterTypes.joinToString()})" }
            methods + fields + constructors
        }

        assertTrue(signatures.isNotEmpty())
        assertTrue(signatures.none { "okhttp3" in it }, signatures.filter { "okhttp3" in it }.toString())
    }

    private fun balanced(text: String, open: Int, openChar: Char, closeChar: Char): String {
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == openChar) depth++
            if (text[i] == closeChar && --depth == 0) return text.substring(open, i + 1)
        }
        return text.substring(open)
    }

    @Test
    fun `gateway sources have no logging and no interpolated exception messages`() {
        val forbidden = listOf("Log.", "println", "printStackTrace", "HttpLoggingInterceptor", "String.format")
        val offenders = mutableListOf<String>()
        for (file in gatewaySources()) {
            val text = file.readText()
            forbidden.filter { it in text }.mapTo(offenders) { "${file.name}: $it" }

            for (match in Regex("""\b(throw\s+[\w.]+|require|check|error)\(""").findAll(text)) {
                val open = match.range.last
                var arguments = balanced(text, open, '(', ')')
                val after = text.substring(open + arguments.length).trimStart()
                if (after.startsWith("{")) arguments += balanced(after, 0, '{', '}')
                if ('$' in arguments) offenders += "${file.name}: interpolated message in ${match.value}"
            }
        }

        assertTrue(offenders.isEmpty(), offenders.toString())
    }
}
