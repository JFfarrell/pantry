package ie.pantry.data.db.entity

/** The unit dimension of a quantity. [UNQUANTIFIED] is the explicit "no amount" state, never a zero. */
enum class QuantityDimension { MASS, VOLUME, COUNT, UNQUANTIFIED }
