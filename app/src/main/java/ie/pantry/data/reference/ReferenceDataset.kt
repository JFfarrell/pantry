package ie.pantry.data.reference

/** The four bundled reference datasets and the asset each is read from. */
enum class ReferenceDataset(val assetPath: String) {
    STAPLES("reference/staples.json"),
    ALIASES("reference/aliases.json"),
    SEASONALITY("reference/seasonality.json"),
    SECTION_ORDER("reference/section_order.json"),
}
