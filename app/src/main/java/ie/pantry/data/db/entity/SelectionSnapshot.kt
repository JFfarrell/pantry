package ie.pantry.data.db.entity

/**
 * One recipe selection a shopping list was generated from. [servings] is null when the recipe had no
 * yield and the user gave none; that absence is preserved, never replaced by a default.
 */
data class SelectionSnapshot(val recipeId: Long, val servings: Int?)
