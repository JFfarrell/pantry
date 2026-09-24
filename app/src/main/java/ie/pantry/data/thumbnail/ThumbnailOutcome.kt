package ie.pantry.data.thumbnail

/** Why a recipe has no thumbnail. Every reason is a modelled state, never an error the caller must catch. */
enum class NoImageReason { DECODE_FAILED, WRITE_FAILED, NO_SOURCE }

sealed interface ThumbnailOutcome {
    /** The thumbnail was written; [relativePath] is relative to `filesDir`. */
    data class Stored(val relativePath: String) : ThumbnailOutcome

    data class NoImage(val reason: NoImageReason) : ThumbnailOutcome
}
