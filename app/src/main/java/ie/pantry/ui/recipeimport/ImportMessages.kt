package ie.pantry.ui.recipeimport

import androidx.annotation.StringRes
import ie.pantry.R
import ie.pantry.recipeimport.Completeness
import ie.pantry.recipeimport.ImportPhase

/**
 * Every import message is a constant string resource with no format argument, so no URL, title or line can reach
 * the screen through an error. This maps each state to its resource id and lists them all for the tests.
 */
object ImportMessages {

    @StringRes
    fun of(failure: Failure): Int = when (failure) {
        Failure.NOT_RECOGNISED -> R.string.import_error_not_recognised
        Failure.NOT_HTML -> R.string.import_error_not_html
        Failure.UNSUPPORTED_ENCODING -> R.string.import_error_unsupported_encoding
        Failure.UNREADABLE -> R.string.import_error_unreadable
        Failure.TOO_LARGE -> R.string.import_error_too_large
        Failure.TIMEOUT -> R.string.import_error_timeout
        Failure.CONNECTION -> R.string.import_error_connection
        Failure.HTTP_5XX -> R.string.import_error_http_5xx
        Failure.HTTP_404 -> R.string.import_error_http_404
        Failure.HTTP_DENIED -> R.string.import_error_http_denied
        Failure.HTTP_OTHER -> R.string.import_error_http_other
        Failure.ADDRESS_REFUSED -> R.string.import_error_address_refused
        Failure.REDIRECT_REFUSED -> R.string.import_error_redirect_refused
        Failure.TOO_MANY_REDIRECTS -> R.string.import_error_too_many_redirects
        Failure.BAD_LINK -> R.string.import_error_bad_link
        Failure.UNEXPECTED -> R.string.import_error_unexpected
    }

    @StringRes
    fun of(notice: Notice): Int = when (notice.kind) {
        NoticeKind.LINK_RECEIVED -> R.string.import_notice_link_received
        NoticeKind.LINK_RECEIVED_IMPORT_CANCELLED -> R.string.import_notice_link_received_cancelled
        NoticeKind.NO_LINK -> R.string.import_notice_no_link
        NoticeKind.IMPORT_CANCELLED -> R.string.import_notice_cancelled
    }

    /** The status line for an import in progress. */
    @StringRes
    fun of(phase: ImportPhase): Int = when (phase) {
        ImportPhase.FETCHING_PAGE -> R.string.import_status_page
        ImportPhase.FETCHING_PICTURE -> R.string.import_status_picture
    }

    /** The message a screen shows as its main text, or null for Input, which shows only its field. */
    @StringRes
    fun of(screen: Screen): Int? = when (screen) {
        is Screen.Input -> null
        is Screen.InProgress -> of(screen.phase)
        is Screen.Failed -> of(screen.message)
        is Screen.Declined -> of(Failure.NOT_RECOGNISED)
        is Screen.Drafted -> draftNotice(screen.draft.completeness)
    }

    /** The draft-produced notice: names what was found, never the content. */
    @StringRes
    fun draftNotice(completeness: Completeness): Int = when (completeness) {
        Completeness.Complete -> R.string.import_notice_recipe_found
        is Completeness.Partial -> when {
            completeness.foundTitle && completeness.foundIngredients && !completeness.foundMethod ->
                R.string.import_notice_partly_ingredients_title
            completeness.foundIngredients && completeness.foundMethod -> R.string.import_notice_partly_ingredients_method
            completeness.foundIngredients -> R.string.import_notice_partly_ingredients
            completeness.foundTitle && completeness.foundMethod -> R.string.import_notice_partly_method_title
            else -> R.string.import_notice_partly_method
        }
    }

    /** Every import string, the control labels and the hint included, for the distinctness and hygiene tests. */
    val ALL: List<Int> = listOf(
        R.string.import_label_link,
        R.string.import_hint,
        R.string.import_action_import,
        R.string.import_action_cancel,
        R.string.import_action_retry,
        R.string.import_action_try_another,
        R.string.import_action_import_another,
        R.string.import_action_share,
        R.string.import_error_invalid_url,
        R.string.import_notice_no_link,
        R.string.import_notice_link_received,
        R.string.import_notice_link_received_cancelled,
        R.string.import_notice_cancelled,
        R.string.import_status_page,
        R.string.import_status_picture,
        R.string.import_error_not_html,
        R.string.import_error_unsupported_encoding,
        R.string.import_error_unreadable,
        R.string.import_error_too_large,
        R.string.import_error_not_recognised,
        R.string.import_decline_share_explainer,
        R.string.import_error_timeout,
        R.string.import_error_connection,
        R.string.import_error_http_5xx,
        R.string.import_error_http_404,
        R.string.import_error_http_denied,
        R.string.import_error_http_other,
        R.string.import_error_address_refused,
        R.string.import_error_redirect_refused,
        R.string.import_error_too_many_redirects,
        R.string.import_error_bad_link,
        R.string.import_error_unexpected,
        R.string.import_notice_recipe_found,
        R.string.import_notice_partly_ingredients,
        R.string.import_notice_partly_ingredients_title,
        R.string.import_notice_partly_ingredients_method,
        R.string.import_notice_partly_method,
        R.string.import_notice_partly_method_title,
    )
}
