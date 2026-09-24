package ie.pantry.testutil

import kotlin.test.fail

/** Content-bearing values that must never appear in any error message, log line or stack trace. */
object Sentinels {
    const val TITLE = "SENTINEL-TITLE-9c1e Grandma's Stew"
    const val INGREDIENT = "SENTINEL-ING-9c1e 400 g tomatoes"
    const val URL = "https://sentinel-9c1e.example/recipe"

    val all: List<String> = listOf(TITLE, INGREDIENT, URL, "SENTINEL")
}

/**
 * Fails if any [Sentinels] value (or the shared `SENTINEL` marker) appears in this throwable's message,
 * localized message, `toString()`, stack trace, any cause or any suppressed exception.
 */
fun Throwable.assertNoSentinel() {
    val seen = mutableSetOf<Throwable>()
    fun check(throwable: Throwable) {
        if (!seen.add(throwable)) return
        val texts = listOf(
            throwable.message,
            throwable.localizedMessage,
            throwable.toString(),
            throwable.stackTraceToString(),
        )
        for (text in texts.filterNotNull()) {
            for (sentinel in Sentinels.all) {
                if (text.contains(sentinel)) fail("sentinel '$sentinel' leaked into a ${throwable.javaClass.simpleName}")
            }
        }
        throwable.cause?.let(::check)
        throwable.suppressed.forEach(::check)
    }
    check(this)
}
