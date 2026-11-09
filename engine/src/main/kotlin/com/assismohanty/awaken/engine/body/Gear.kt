package com.assismohanty.awaken.engine.body

import com.assismohanty.awaken.engine.onboarding.EquipmentChoice
import com.assismohanty.awaken.engine.onboarding.InjuryRegion

/** What the hunter has to train with (Gym settings). [key] is the spelling used in `plans.json` and the overlay. */
enum class Gear(val key: String) {
    PULL_UP_BAR("pull_up_bar"),
    DIP_BARS("dip_bars"),
    BENCH("bench"),
    DUMBBELLS("dumbbells"),
    BARBELL("barbell"),
    CABLES("cables"),
    MACHINES("machines"),
    KETTLEBELLS("kettlebells"),
    BANDS("bands"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Gear? = byKey[key]

        /** What the onboarding choice owned (ENGINE_DECISIONS: BODYWEIGHT = nothing, DUMBBELLS = dumbbells + bench, GYM = everything). */
        fun fromOnboarding(choice: EquipmentChoice?): Set<Gear> = when (choice) {
            EquipmentChoice.DUMBBELLS -> setOf(DUMBBELLS, BENCH)
            EquipmentChoice.GYM -> entries.toSet()
            EquipmentChoice.BODYWEIGHT, null -> emptySet()
        }

        /** The gear a catalog equipment value needs, or null when it needs none ("body only") or is not covered by any gear. */
        fun forEquipment(equipment: Equipment): Gear? = when (equipment) {
            Equipment.DUMBBELL -> DUMBBELLS
            Equipment.BARBELL, Equipment.EZ_BAR -> BARBELL
            Equipment.KETTLEBELLS -> KETTLEBELLS
            Equipment.CABLE -> CABLES
            Equipment.MACHINE -> MACHINES
            Equipment.BANDS -> BANDS
            Equipment.BODY_ONLY, Equipment.MEDICINE_BALL, Equipment.EXERCISE_BALL, Equipment.FOAM_ROLL, Equipment.OTHER -> null
        }
    }
}

/**
 * What one exercise needs: its catalog [equipment] plus anything the overlay adds ([requires], e.g. a pull-up bar for a
 * "body only" pull-up). Equipment "other" is usable only when [requires] says what it is.
 */
data class ExerciseNeeds(val equipment: Equipment, val requires: Set<Gear> = emptySet()) {
    fun usableWith(owned: Set<Gear>): Boolean {
        if (!owned.containsAll(requires)) return false
        val gear = Gear.forEquipment(equipment)
        return when {
            gear != null -> gear in owned
            equipment == Equipment.BODY_ONLY -> true
            else -> requires.isNotEmpty()
        }
    }
}

/** Which exercises load an injured area (GD §18.1). Not medical advice: the injury note in the app says so. */
object InjuryRules {
    /**
     * Whether an exercise loads [region]: by its muscles (primary only, so a light secondary role doesn't hide it), by the
     * knee/hip rule for leg compounds and jumps, or because the overlay lists the region for it.
     */
    fun loads(region: InjuryRegion, primary: Set<Muscle>, mechanic: String?, category: String?, overlayLoads: Set<InjuryRegion>): Boolean {
        if (region in overlayLoads) return true
        val compound = mechanic == "compound"
        return when (region) {
            InjuryRegion.NO_INJURIES -> false
            InjuryRegion.SHOULDERS -> Muscle.SHOULDERS in primary
            InjuryRegion.BACK -> Muscle.LOWER_BACK in primary
            InjuryRegion.WRIST -> Muscle.FOREARMS in primary
            InjuryRegion.KNEE -> category == "plyometrics" || (compound && Muscle.QUADRICEPS in primary)
            InjuryRegion.HIP -> compound && primary.any { it == Muscle.GLUTES || it == Muscle.ADDUCTORS || it == Muscle.ABDUCTORS }
        }
    }

    fun hidden(injuries: Set<InjuryRegion>, primary: Set<Muscle>, mechanic: String?, category: String?, overlayLoads: Set<InjuryRegion>): Boolean =
        injuries.any { loads(it, primary, mechanic, category, overlayLoads) }
}
