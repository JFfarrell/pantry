package ie.pantry.ui.recipeimport

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.recipeimport.ImportDraft
import ie.pantry.recipeimport.ImportException
import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ValidatedUrl

/** One message per row of the error table that can appear on the Failed screen or the decline surface. */
enum class Failure {
    NOT_RECOGNISED,
    NOT_HTML,
    UNSUPPORTED_ENCODING,
    UNREADABLE,
    TOO_LARGE,
    TIMEOUT,
    CONNECTION,
    HTTP_5XX,
    HTTP_404,
    HTTP_DENIED,
    HTTP_OTHER,
    ADDRESS_REFUSED,
    REDIRECT_REFUSED,
    TOO_MANY_REDIRECTS,
    BAD_LINK,
    UNEXPECTED,
}

/** The one function that maps an import error to its message, from the kind, gateway category and HTTP status. */
fun failureOf(error: ImportException): Failure = when (error.kind) {
    ImportException.Kind.NOT_RECOGNISED -> Failure.NOT_RECOGNISED
    ImportException.Kind.NOT_HTML -> Failure.NOT_HTML
    ImportException.Kind.UNSUPPORTED_ENCODING -> Failure.UNSUPPORTED_ENCODING
    ImportException.Kind.UNREADABLE_CONTENT -> Failure.UNREADABLE
    ImportException.Kind.CONTENT_TOO_LARGE -> Failure.TOO_LARGE
    ImportException.Kind.FETCH_FAILED -> when (error.gatewayCategory) {
        Category.TIMEOUT -> Failure.TIMEOUT
        Category.CONNECTION_FAILED -> Failure.CONNECTION
        Category.HTTP_STATUS -> when (error.statusCode) {
            404 -> Failure.HTTP_404
            401, 403 -> Failure.HTTP_DENIED
            in 500..599 -> Failure.HTTP_5XX
            else -> Failure.HTTP_OTHER
        }
        Category.RESPONSE_TOO_LARGE -> Failure.TOO_LARGE
        Category.ADDRESS_REFUSED -> Failure.ADDRESS_REFUSED
        Category.SCHEME_REFUSED -> Failure.REDIRECT_REFUSED
        Category.TOO_MANY_REDIRECTS -> Failure.TOO_MANY_REDIRECTS
        Category.INVALID_REQUEST -> Failure.BAD_LINK
        Category.UNEXPECTED, null -> Failure.UNEXPECTED
    }
}

/** Retry is offered exactly for `TIMEOUT`, `CONNECTION_FAILED` and HTTP 500 to 599. */
internal fun isRetryable(error: ImportException): Boolean =
    error.kind == ImportException.Kind.FETCH_FAILED && when (error.gatewayCategory) {
        Category.TIMEOUT, Category.CONNECTION_FAILED -> true
        Category.HTTP_STATUS -> error.statusCode in 500..599
        else -> false
    }

enum class NoticeKind { LINK_RECEIVED, LINK_RECEIVED_IMPORT_CANCELLED, NO_LINK, IMPORT_CANCELLED }

/** A transient message; [seq] changes on every notice so a repeat of the same one is still a change. */
data class Notice(val kind: NoticeKind, val seq: Int)

sealed interface Screen {
    data class Input(val fieldError: Boolean = false) : Screen

    data class InProgress(val phase: ImportPhase) : Screen

    data class Failed(val message: Failure, val retryable: Boolean) : Screen

    class Declined(val url: ValidatedUrl) : Screen {
        override fun toString(): String = "Declined"
    }

    class Drafted(val draft: ImportDraft) : Screen {
        override fun toString(): String = "Drafted(draft=$draft)"
    }
}

/** What the import screen shows. [field] is verbatim user text or a picked URL, so it never prints. */
class ImportUiState(val screen: Screen, val field: String, val notice: Notice?, val focusSeq: Int) {

    fun copy(
        screen: Screen = this.screen,
        field: String = this.field,
        notice: Notice? = this.notice,
        focusSeq: Int = this.focusSeq,
    ): ImportUiState = ImportUiState(screen, field, notice, focusSeq)

    override fun toString(): String =
        "ImportUiState(screen=$screen, fieldLength=${field.length}, notice=${notice?.kind}, focusSeq=$focusSeq)"
}
