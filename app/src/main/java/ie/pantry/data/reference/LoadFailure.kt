package ie.pantry.data.reference

/**
 * Why a dataset failed to load. Every field comes from the loader's own vocabulary (an enum, a constant
 * array name, a position or a constant schema field name), never from asset content, so [toString] is
 * content-free too.
 */
data class LoadFailure(
    val dataset: ReferenceDataset,
    val category: Category,
    /** `entries`, `sections` or `mappings`; null for a failure at document or asset level. */
    val array: String?,
    /** Zero-based; for a duplicate key it is the second occurrence. Null at document or asset level. */
    val entryIndex: Int?,
    /** A constant schema field name; null when not field-specific, and always null for an unexpected field. */
    val field: String?,
) {
    enum class Category {
        ASSET_ABSENT,
        ASSET_UNREADABLE,
        INVALID_JSON,
        MISSING_FIELD,
        UNEXPECTED_FIELD,
        WRONG_TYPE,
        INVALID_ENUM_VALUE,
        INVALID_VALUE,
        DUPLICATE_KEY,
        INTERNAL_ERROR,
    }
}
