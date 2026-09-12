package com.nutrix.app.nutrition

import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.Sex

/**
 * Dietary Reference Intakes for adults, as published by the US National Academies and
 * summarised in the NIH Office of Dietary Supplements fact sheets.
 *
 * [rda] returns the RDA where one exists and the Adequate Intake where it does not — for a
 * daily tracking target the distinction does not change what the user should aim for, but
 * [isAdequateIntake] exposes it for the UI.
 *
 * Values are for non-pregnant, non-lactating adults. Pregnancy and lactation shift many of
 * these substantially, which is why [GoalCalculator] refuses to quietly generate targets for
 * someone who lists pregnancy as a condition and tells them to get clinical numbers instead.
 */
object ReferenceIntakes {

    /** Age brackets used by the DRI tables. */
    private fun bracket(age: Int): Int = when {
        age < 19 -> 18   // 14-18
        age < 31 -> 30   // 19-30
        age < 51 -> 50   // 31-50
        age < 71 -> 70   // 51-70
        else -> 71       // 71+
    }

    /** Nutrients whose figure below is an Adequate Intake rather than an RDA. */
    private val adequateIntakes = setOf(
        Nutrient.VITAMIN_K, Nutrient.VITAMIN_B5, Nutrient.VITAMIN_B7,
        Nutrient.POTASSIUM, Nutrient.SODIUM, Nutrient.MANGANESE, Nutrient.FIBER,
    )

    fun isAdequateIntake(nutrient: Nutrient): Boolean = nutrient in adequateIntakes

    /**
     * Daily recommended amount in the nutrient's base unit, or null when the nutrient has no
     * DRI (energy and the macronutrients are derived from bodyweight and activity instead).
     */
    fun rda(nutrient: Nutrient, sex: Sex, age: Int): Double? {
        val b = bracket(age)
        val male = sex == Sex.MALE
        return when (nutrient) {
            Nutrient.VITAMIN_A -> if (male) 900.0 else 700.0
            Nutrient.VITAMIN_C -> when {
                b == 18 -> if (male) 75.0 else 65.0
                else -> if (male) 90.0 else 75.0
            }
            Nutrient.VITAMIN_D -> if (b == 71) 20.0 else 15.0
            Nutrient.VITAMIN_E -> 15.0
            Nutrient.VITAMIN_K -> when {
                b == 18 -> 75.0
                male -> 120.0
                else -> 90.0
            }
            Nutrient.VITAMIN_B1 -> if (male) 1.2 else if (b == 18) 1.0 else 1.1
            Nutrient.VITAMIN_B2 -> if (male) 1.3 else if (b == 18) 1.0 else 1.1
            Nutrient.VITAMIN_B3 -> if (male) 16.0 else 14.0
            Nutrient.VITAMIN_B5 -> 5.0
            Nutrient.VITAMIN_B6 -> when {
                b == 18 -> if (male) 1.3 else 1.2
                b <= 50 -> 1.3
                else -> if (male) 1.7 else 1.5
            }
            Nutrient.VITAMIN_B7 -> if (b == 18) 25.0 else 30.0
            Nutrient.VITAMIN_B9 -> 400.0
            Nutrient.VITAMIN_B12 -> 2.4
            Nutrient.CALCIUM -> when {
                b == 18 -> 1300.0
                b <= 50 -> 1000.0
                b == 70 -> if (male) 1000.0 else 1200.0
                else -> 1200.0
            }
            Nutrient.IRON -> when {
                b == 18 -> if (male) 11.0 else 15.0
                b <= 50 -> if (male) 8.0 else 18.0
                else -> 8.0
            }
            Nutrient.MAGNESIUM -> when {
                b == 18 -> if (male) 410.0 else 360.0
                b == 30 -> if (male) 400.0 else 310.0
                else -> if (male) 420.0 else 320.0
            }
            Nutrient.ZINC -> if (male) 11.0 else if (b == 18) 9.0 else 8.0
            Nutrient.POTASSIUM -> when {
                b == 18 -> if (male) 3000.0 else 2300.0
                else -> if (male) 3400.0 else 2600.0
            }
            // Adequate Intake is 1500 mg; the tracking target is the 2300 mg
            // Chronic Disease Risk Reduction level, because sodium is a ceiling in practice.
            Nutrient.SODIUM -> 2300.0
            Nutrient.IODINE -> 150.0
            Nutrient.SELENIUM -> 55.0
            Nutrient.PHOSPHORUS -> if (b == 18) 1250.0 else 700.0
            Nutrient.COPPER -> if (b == 18) 0.89 else 0.9
            Nutrient.MANGANESE -> when {
                b == 18 -> if (male) 2.2 else 1.6
                else -> if (male) 2.3 else 1.8
            }
            // Derived from bodyweight/energy rather than a fixed DRI.
            Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT,
            Nutrient.SATURATED_FAT, Nutrient.FIBER, Nutrient.SUGAR,
            -> null
        }
    }

    /**
     * Tolerable Upper Intake Level from all sources, where one exists and is meaningful for
     * food tracking. Magnesium's UL is deliberately absent: it applies to supplements only,
     * so flagging a magnesium-rich meal would be wrong.
     */
    fun upperLimit(nutrient: Nutrient, sex: Sex, age: Int): Double? {
        val b = bracket(age)
        return when (nutrient) {
            Nutrient.VITAMIN_A -> 3000.0
            Nutrient.VITAMIN_C -> if (b == 18) 1800.0 else 2000.0
            Nutrient.VITAMIN_D -> 100.0
            Nutrient.VITAMIN_E -> if (b == 18) 800.0 else 1000.0
            Nutrient.VITAMIN_B3 -> if (b == 18) 30.0 else 35.0
            Nutrient.VITAMIN_B6 -> if (b == 18) 80.0 else 100.0
            Nutrient.VITAMIN_B9 -> if (b == 18) 800.0 else 1000.0
            Nutrient.CALCIUM -> when {
                b == 18 -> 3000.0
                b <= 50 -> 2500.0
                else -> 2000.0
            }
            Nutrient.IRON -> 45.0
            Nutrient.ZINC -> if (b == 18) 34.0 else 40.0
            Nutrient.SODIUM -> 2300.0
            Nutrient.IODINE -> if (b == 18) 900.0 else 1100.0
            Nutrient.SELENIUM -> 400.0
            Nutrient.PHOSPHORUS -> if (b == 18) 4000.0 else if (b == 71) 3000.0 else 4000.0
            Nutrient.COPPER -> if (b == 18) 8.0 else 10.0
            Nutrient.MANGANESE -> if (b == 18) 9.0 else 11.0
            else -> null
        }
    }

    /** A short, factual line about what a nutrient does — shown when the user taps it. */
    fun blurb(nutrient: Nutrient): String = when (nutrient) {
        Nutrient.ENERGY -> "Total energy from food. Everything else is spent inside this budget."
        Nutrient.PROTEIN -> "Builds and repairs muscle. The number that matters most if you lift."
        Nutrient.CARBS -> "Your main training fuel and what refills muscle glycogen."
        Nutrient.FAT -> "Hormone production and absorbing vitamins A, D, E and K."
        Nutrient.SATURATED_FAT -> "Keep under about 10% of calories for cardiovascular health."
        Nutrient.FIBER -> "Digestion, satiety and blood sugar control. Most people fall short."
        Nutrient.SUGAR -> "Free sugars. Under 10% of calories, ideally under 5%."
        Nutrient.VITAMIN_A -> "Vision, immune function and skin. From liver, dairy and orange vegetables."
        Nutrient.VITAMIN_B1 -> "Turns carbohydrate into energy. Higher needs on a high-carb diet."
        Nutrient.VITAMIN_B2 -> "Energy metabolism and red blood cells. Dairy, eggs, greens."
        Nutrient.VITAMIN_B3 -> "Energy metabolism and DNA repair. Meat, fish, peanuts."
        Nutrient.VITAMIN_B5 -> "Part of coenzyme A, used in nearly every energy pathway."
        Nutrient.VITAMIN_B6 -> "Protein metabolism — needs rise as protein intake rises."
        Nutrient.VITAMIN_B7 -> "Biotin. Fatty acid synthesis, hair and nails."
        Nutrient.VITAMIN_B9 -> "Folate. Cell division and red blood cell formation."
        Nutrient.VITAMIN_B12 -> "Nerves and red blood cells. Only reliably from animal foods or supplements."
        Nutrient.VITAMIN_C -> "Collagen, iron absorption and antioxidant defence."
        Nutrient.VITAMIN_D -> "Calcium absorption, bone and muscle function. Sunlight plus diet."
        Nutrient.VITAMIN_E -> "Protects cell membranes from oxidation. Nuts, seeds, oils."
        Nutrient.VITAMIN_K -> "Blood clotting and bone metabolism. Leafy greens."
        Nutrient.CALCIUM -> "Bone density and muscle contraction."
        Nutrient.IRON -> "Oxygen transport. Low iron shows up first as flat training sessions."
        Nutrient.MAGNESIUM -> "Muscle function, sleep quality and 300+ enzyme reactions."
        Nutrient.ZINC -> "Testosterone, immune function and recovery."
        Nutrient.POTASSIUM -> "Blood pressure and muscle contraction. Works against sodium."
        Nutrient.SODIUM -> "Needed for fluid balance, but most diets deliver far too much."
        Nutrient.IODINE -> "Thyroid hormones, which set your metabolic rate."
        Nutrient.SELENIUM -> "Thyroid conversion and antioxidant enzymes. Brazil nuts are dense."
        Nutrient.PHOSPHORUS -> "Bone mineral and cellular energy (ATP)."
        Nutrient.COPPER -> "Iron metabolism and connective tissue."
        Nutrient.MANGANESE -> "Bone formation and carbohydrate metabolism."
    }
}
