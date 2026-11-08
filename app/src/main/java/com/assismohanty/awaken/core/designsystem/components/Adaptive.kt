package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/**
 * Where side-by-side layouts stop fitting (design/components.md, small phones and large text): from 130% text, or on a phone
 * narrower than 380dp, two half-width tiles or a title with two actions go one under the other.
 */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE

@Composable
fun isSmallPhone(): Boolean = LocalConfiguration.current.screenWidthDp < SMALL_PHONE_DP

/** How much a fixed-size graphic that holds text (a ring with a label) grows with the text size, capped. */
@Composable
fun textGraphicScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MAX_GRAPHIC_SCALE)

private const val LARGE_TEXT_SCALE = 1.3f
private const val SMALL_PHONE_DP = 380
private const val MAX_GRAPHIC_SCALE = 1.6f
