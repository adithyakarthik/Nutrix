package com.nutrix.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The unit a nutrient is measured in. Everything is stored in the base unit named here. */
enum class NutrientUnit(val label: String) {
    KCAL("kcal"),
    GRAM("g"),
    MILLIGRAM("mg"),
    MICROGRAM("µg"),
}

enum class NutrientGroup(val label: String) {
    ENERGY("Energy"),
    MACRO("Macros"),
    VITAMIN("Vitamins"),
    MINERAL("Minerals"),
}

/**
 * Whether hitting the goal means reaching it, staying under it, or landing near it.
 * Bodybuilders want to *clear* their protein number; nobody wants to clear their sodium number.
 */
enum class GoalDirection { AT_LEAST, AT_MOST, AROUND }

/**
 * Every nutrient Nutrix tracks.
 *
 * [key] is the stable wire/storage name: it is what gets written into the database and into
 * Claude's JSON, so renaming a constant never invalidates a user's history.
 */
@Serializable
enum class Nutrient(
    val key: String,
    val label: String,
    val unit: NutrientUnit,
    val group: NutrientGroup,
    val direction: GoalDirection,
) {
    @SerialName("energy_kcal")
    ENERGY("energy_kcal", "Calories", NutrientUnit.KCAL, NutrientGroup.ENERGY, GoalDirection.AROUND),

    @SerialName("protein_g")
    PROTEIN("protein_g", "Protein", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AT_LEAST),

    @SerialName("carbs_g")
    CARBS("carbs_g", "Carbs", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AROUND),

    @SerialName("fat_g")
    FAT("fat_g", "Fat", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AROUND),

    @SerialName("saturated_fat_g")
    SATURATED_FAT("saturated_fat_g", "Saturated fat", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AT_MOST),

    @SerialName("fiber_g")
    FIBER("fiber_g", "Fibre", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AT_LEAST),

    @SerialName("sugar_g")
    SUGAR("sugar_g", "Sugar", NutrientUnit.GRAM, NutrientGroup.MACRO, GoalDirection.AT_MOST),

    @SerialName("vitamin_a_mcg")
    VITAMIN_A("vitamin_a_mcg", "Vitamin A", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b1_mg")
    VITAMIN_B1("vitamin_b1_mg", "B1 Thiamin", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b2_mg")
    VITAMIN_B2("vitamin_b2_mg", "B2 Riboflavin", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b3_mg")
    VITAMIN_B3("vitamin_b3_mg", "B3 Niacin", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b5_mg")
    VITAMIN_B5("vitamin_b5_mg", "B5 Pantothenic acid", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b6_mg")
    VITAMIN_B6("vitamin_b6_mg", "B6 Pyridoxine", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b7_mcg")
    VITAMIN_B7("vitamin_b7_mcg", "B7 Biotin", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b9_mcg")
    VITAMIN_B9("vitamin_b9_mcg", "B9 Folate", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_b12_mcg")
    VITAMIN_B12("vitamin_b12_mcg", "B12 Cobalamin", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_c_mg")
    VITAMIN_C("vitamin_c_mg", "Vitamin C", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_d_mcg")
    VITAMIN_D("vitamin_d_mcg", "Vitamin D", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_e_mg")
    VITAMIN_E("vitamin_e_mg", "Vitamin E", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("vitamin_k_mcg")
    VITAMIN_K("vitamin_k_mcg", "Vitamin K", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, GoalDirection.AT_LEAST),

    @SerialName("calcium_mg")
    CALCIUM("calcium_mg", "Calcium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("iron_mg")
    IRON("iron_mg", "Iron", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("magnesium_mg")
    MAGNESIUM("magnesium_mg", "Magnesium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("zinc_mg")
    ZINC("zinc_mg", "Zinc", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("potassium_mg")
    POTASSIUM("potassium_mg", "Potassium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("sodium_mg")
    SODIUM("sodium_mg", "Sodium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_MOST),

    @SerialName("iodine_mcg")
    IODINE("iodine_mcg", "Iodine", NutrientUnit.MICROGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("selenium_mcg")
    SELENIUM("selenium_mcg", "Selenium", NutrientUnit.MICROGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("phosphorus_mg")
    PHOSPHORUS("phosphorus_mg", "Phosphorus", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("copper_mg")
    COPPER("copper_mg", "Copper", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST),

    @SerialName("manganese_mg")
    MANGANESE("manganese_mg", "Manganese", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, GoalDirection.AT_LEAST);

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Nutrient? = byKey[key.trim().lowercase()]

        /** The four headline numbers shown on the dashboard. */
        val headline: List<Nutrient> = listOf(ENERGY, PROTEIN, CARBS, FAT)

        val macros: List<Nutrient> = entries.filter { it.group == NutrientGroup.MACRO }
        val vitamins: List<Nutrient> = entries.filter { it.group == NutrientGroup.VITAMIN }
        val minerals: List<Nutrient> = entries.filter { it.group == NutrientGroup.MINERAL }
    }
}
