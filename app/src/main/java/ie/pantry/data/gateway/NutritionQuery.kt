package ie.pantry.data.gateway

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Builds the one nutrition search URL. The term is added only as a percent-encoded query parameter value. */
internal object NutritionQuery {
    val ENDPOINT: HttpUrl = "https://world.openfoodfacts.org/cgi/search.pl".toHttpUrl()

    private const val TERM_PARAM = "search_terms"

    fun url(endpoint: HttpUrl, term: String): HttpUrl = endpoint.newBuilder()
        .addQueryParameter("search_simple", "1")
        .addQueryParameter("action", "process")
        .addQueryParameter("json", "1")
        .addQueryParameter("page_size", "5")
        .addQueryParameter(TERM_PARAM, term)
        .build()
}
