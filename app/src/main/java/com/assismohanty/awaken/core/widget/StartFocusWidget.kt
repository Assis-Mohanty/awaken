package com.assismohanty.awaken.core.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.core.work.ACTION_OPEN_GATES
import com.assismohanty.awaken.core.work.ACTION_START_FOCUS
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/** Hilt access for the Start focus widget (and the tile), which Android creates itself. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FocusSurfaceEntryPoint {
    fun surface(): FocusSurfaceReader
    fun clock(): com.assismohanty.awaken.core.data.clock.Clock
}

/**
 * Start focus widget, 2x1 (GD §16.3, DS §9.3): "Start focus" and the session a tap starts again; while one runs, when it ends.
 * A tap starts the last-used session in the app, or opens the one that runs. Glance cannot read `AwakenTheme`, so it draws
 * with the palette, light and dark, like the other widgets.
 */
class StartFocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, FocusSurfaceEntryPoint::class.java)
        val surface = entry.surface().read()
        val zone = entry.clock().zone()
        provideContent { StartFocusContent(context, surface, zone) }
    }
}

class StartFocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StartFocusWidget()
}

private val light = AwakenPalette.Light
private val dark = AwakenPalette.Dark
private fun tone(pick: (AwakenPalette) -> Color): ColorProvider = dayNight(day = pick(light), night = pick(dark))
private fun style(color: ColorProvider, size: Int, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)

private fun clockText(context: Context, at: Instant, zone: ZoneId): String =
    DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date.from(at))

/** The second line: what a tap starts, or what runs. */
private fun line(context: Context, surface: FocusSurface, zone: ZoneId): String = when (surface) {
    is FocusSurface.Idle -> surface.last?.let { lastFocusLine(context, it, surface.subjectName) } ?: context.getString(R.string.focus_start_pick)
    is FocusSurface.Running ->
        surface.endsAt?.let { context.getString(R.string.focus_start_running_until, clockText(context, it, zone)) }
            ?: context.getString(R.string.focus_start_running_since, clockText(context, surface.startedAt, zone))
    is FocusSurface.Break ->
        if (surface.over) context.getString(R.string.focus_start_break_over) else context.getString(R.string.focus_start_break_until, clockText(context, surface.endsAt, zone))
}

@Composable
private fun StartFocusContent(context: Context, surface: FocusSurface, zone: ZoneId) {
    val idleWithLast = surface is FocusSurface.Idle && surface.last != null
    // Idle with something to repeat starts it; everything else (running, a break, nothing yet) opens the Focus screen.
    val action = if (idleWithLast) ACTION_START_FOCUS else ACTION_OPEN_GATES
    val open = actionStartActivity(
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val second = line(context, surface, zone)
    val title = context.getString(R.string.focus_start_title)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(tone { it.panel })
            .cornerRadius(WIDGET_RADIUS)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(open)
            .semantics { contentDescription = context.getString(R.string.focus_start_cd, title, second) },
    ) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = style(tone { it.bone }, 17, bold = true))
            Text(second, style = style(if (surface is FocusSurface.Idle) tone { it.mist } else tone { it.verified }, 12), maxLines = 1)
        }
    }
}

private val WIDGET_RADIUS = 24.dp
