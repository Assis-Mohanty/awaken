package com.assismohanty.awaken.core.designsystem.lexicon

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * The one entry point for every user-visible word (DESIGN_SYSTEM §8.3). The app speaks plain, second-person, sentence-case
 * copy; there is a single set of strings. Feature code calls these wrappers, never `stringResource`, so a copy or
 * localisation change stays in one place (`FeatureBoundaryTest` enforces it).
 */
@Composable
@ReadOnlyComposable
fun lexString(@StringRes id: Int): String = stringResource(id)

@Composable
@ReadOnlyComposable
fun lexString(@StringRes id: Int, vararg formatArgs: Any): String = stringResource(id, *formatArgs)

@Composable
@ReadOnlyComposable
fun lexPlural(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count)

@Composable
@ReadOnlyComposable
fun lexPlural(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String = pluralStringResource(id, count, *formatArgs)

/** [Resources] lookups for code that builds text outside composition (snackbars, notifications). */
class LexResources(private val resources: Resources) {
    val configuration: Configuration get() = resources.configuration

    fun getString(@StringRes id: Int): String = resources.getString(id)

    fun getString(@StringRes id: Int, vararg formatArgs: Any): String = resources.getString(id, *formatArgs)

    fun getQuantityString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
        resources.getQuantityString(id, count, *formatArgs)
}

@Composable
fun rememberLexResources(): LexResources {
    val resources = LocalResources.current
    return remember(resources) { LexResources(resources) }
}
