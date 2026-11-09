package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.GymAnswers
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.RestRules
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import java.time.Duration

private val SEX_KEYS = listOf("M", "F")
private val GOALS = listOf("strength", "muscle", "general")
private val REGIONS = InjuryRegion.entries.filter { it != InjuryRegion.NO_INJURIES }

/**
 * The answers onboarding asked, now editable inside Gym (GD §18.1): level, goal, equipment, injuries, training days, body
 * weight and sex, units, rest and logging. Every change goes through [GymEvent.UpdateAnswers].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymAnswersContent(answers: GymAnswers, unit: PlateUnit, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    fun change(block: (GymAnswers) -> GymAnswers) = onEvent(GymEvent.UpdateAnswers(block))
    val chips = Arrangement.spacedBy(Spacing.xs)

    Heading(R.string.gym_set_you)
    Label(R.string.gym_set_level)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        FitnessLevel.entries.forEach { level ->
            ChoiceChip(text = lexString(levelRes(level)), selected = answers.fitness == level, onClick = { change { it.copy(fitness = level) } })
        }
    }
    Label(R.string.gym_set_goal)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        GOALS.forEach { goal ->
            ChoiceChip(text = lexString(goalRes(goal)), selected = answers.goal == goal, onClick = { change { it.copy(goal = goal) } })
        }
    }

    Label(R.string.gym_set_gear)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        Gear.entries.forEach { gear ->
            val on = gear in answers.gear
            ChoiceChip(
                text = lexString(gearRes(gear)),
                selected = on,
                onClick = { change { it.copy(gear = if (on) it.gear - gear else it.gear + gear) } },
                multiSelect = true,
            )
        }
    }
    Text(lexString(R.string.gym_set_gear_note), style = AwakenTheme.type.caption, color = colors.textSecondary)

    Label(R.string.gym_set_injuries)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        REGIONS.forEach { region ->
            val on = region in answers.injuries
            ChoiceChip(
                text = lexString(injuryRes(region)),
                selected = on,
                onClick = { change { it.copy(injuries = if (on) it.injuries - region else it.injuries + region) } },
                multiSelect = true,
            )
        }
    }
    Text(lexString(R.string.gym_set_injuries_note), style = AwakenTheme.type.caption, color = colors.textSecondary)

    Label(R.string.gym_set_days)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        (1..7).forEach { day ->
            val on = day in answers.trainingDays
            ChoiceChip(
                text = weekdayShort(day),
                selected = on,
                onClick = { change { it.copy(trainingDays = if (on) it.trainingDays - day else it.trainingDays + day) } },
                multiSelect = true,
            )
        }
    }

    Heading(R.string.gym_set_body)
    Label(R.string.gym_set_weight)
    NumberField(
        value = answers.bodyWeightKg ?: 0.0,
        onValueChange = { kg -> change { it.copy(bodyWeightKg = kg.takeIf { v -> v > 0.0 }) } },
        description = lexString(R.string.gym_set_weight),
        key = "weight",
        suffix = lexString(R.string.gym_plates_unit_kg),
    )
    Label(R.string.gym_set_sex)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        SEX_KEYS.forEach { sex ->
            ChoiceChip(
                text = lexString(if (sex == "M") R.string.gym_sex_m else R.string.gym_sex_f),
                selected = answers.sex == sex,
                onClick = { change { it.copy(sex = if (it.sex == sex) null else sex) } },
            )
        }
    }
    Text(lexString(R.string.gym_set_sex_note), style = AwakenTheme.type.caption, color = colors.textSecondary)

    Heading(R.string.gym_set_units)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        PlateUnit.entries.forEach { u ->
            ChoiceChip(text = lexString(if (u == PlateUnit.KG) R.string.gym_plates_unit_kg else R.string.gym_plates_unit_lb), selected = u == unit, onClick = { onEvent(GymEvent.PlateUnitPicked(u)) })
        }
    }

    Heading(R.string.gym_set_rest)
    RestStep(R.string.gym_set_rest_compound, answers.restCompoundSec) { seconds -> change { it.copy(restCompoundSec = seconds) } }
    RestStep(R.string.gym_set_rest_isolation, answers.restIsolationSec) { seconds -> change { it.copy(restIsolationSec = seconds) } }
    SwitchRow(R.string.gym_set_rest_sound, answers.restSound) { on -> change { it.copy(restSound = on) } }
    SwitchRow(R.string.gym_set_rest_vibrate, answers.restVibrate) { on -> change { it.copy(restVibrate = on) } }

    Heading(R.string.gym_set_logging)
    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
        ChoiceChip(text = lexString(R.string.gym_log_coached), selected = answers.coached, onClick = { change { it.copy(coached = true) } })
        ChoiceChip(text = lexString(R.string.gym_log_list), selected = !answers.coached, onClick = { change { it.copy(coached = false) } })
    }
    Heading(R.string.gym_set_plates_section)
}

@Composable
private fun Heading(res: Int) {
    Text(lexString(res), style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.textPrimary)
}

@Composable
private fun Label(res: Int) {
    Text(lexString(res), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
}

/** Rest in 15-second steps, 15 to 600 s; stepping down from 15 s keeps the lowest step (a default is never off). */
@Composable
private fun RestStep(label: Int, seconds: Int, onChange: (Int) -> Unit) {
    Stepper(
        label = lexString(label),
        value = formatElapsed(Duration.ofSeconds(seconds.toLong())),
        onLess = { onChange(RestRules.stepDown(seconds) ?: RestRules.STEP_SEC) },
        onMore = { onChange(RestRules.stepUp(seconds)) },
    )
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lexString(label), style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        AwakenSwitch(checked = checked)
    }
}

private fun levelRes(level: FitnessLevel): Int = when (level) {
    FitnessLevel.BEGINNER -> R.string.gym_level_beginner
    FitnessLevel.INTERMEDIATE -> R.string.gym_level_intermediate
    FitnessLevel.ADVANCED -> R.string.gym_level_advanced
}

private fun goalRes(goal: String): Int = when (goal) {
    "strength" -> R.string.gym_goal_strength
    "muscle" -> R.string.gym_goal_muscle
    else -> R.string.gym_goal_general
}

private fun gearRes(gear: Gear): Int = when (gear) {
    Gear.PULL_UP_BAR -> R.string.gym_gear_pull_up_bar
    Gear.DIP_BARS -> R.string.gym_gear_dip_bars
    Gear.BENCH -> R.string.gym_gear_bench
    Gear.DUMBBELLS -> R.string.gym_gear_dumbbells
    Gear.BARBELL -> R.string.gym_gear_barbell
    Gear.CABLES -> R.string.gym_gear_cables
    Gear.MACHINES -> R.string.gym_gear_machines
    Gear.KETTLEBELLS -> R.string.gym_gear_kettlebells
    Gear.BANDS -> R.string.gym_gear_bands
}

private fun injuryRes(region: InjuryRegion): Int = when (region) {
    InjuryRegion.SHOULDERS -> R.string.gym_injury_shoulders
    InjuryRegion.BACK -> R.string.gym_injury_back
    InjuryRegion.WRIST -> R.string.gym_injury_wrist
    InjuryRegion.KNEE -> R.string.gym_injury_knee
    InjuryRegion.HIP -> R.string.gym_injury_hip
    InjuryRegion.NO_INJURIES -> R.string.gym_injury_back
}
