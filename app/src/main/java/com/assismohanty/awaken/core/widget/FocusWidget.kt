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
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.core.work.ACTION_OPEN_TODAY
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateMode
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.flow.first

/** What the Focus widget shows: today's cleared focus minutes, and whether a session is running now. */
data class FocusWidgetSnapshot(
    val todayMinutes: Int = 0,
    val runningMode: GateMode? = null,
    val runningSince: Instant? = null,
)

/**
 * Focus widget, 2x2: today's focus time, and "Stopwatch running since 18:30" while one is open. A home-screen widget
 * cannot tick every few milliseconds, so the live face stays in the app; tapping the widget opens it.
 */
class FocusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val profile = entry.hunterDao().getProfile()
        val clock = entry.clock()
        val today = clock.gameDate(profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)
        val seconds = entry.focusDao().observeSecondsByDay(today, today).first().sumOf { it.seconds }
        val running = entry.gateRunDao().running()
        val snapshot = FocusWidgetSnapshot(
            todayMinutes = (seconds / SECONDS_PER_MINUTE).toInt(),
            runningMode = running?.mode,
            runningSince = running?.startedAt,
        )
        val zone = clock.zone()
        provideContent { FocusContent(context, snapshot, zone) }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

private const val SECONDS_PER_MINUTE = 60L
private val light = AwakenPalette.Light
private val dark = AwakenPalette.Dark
private fun tone(pick: (AwakenPalette) -> Color): ColorProvider = dayNight(day = pick(light), night = pick(dark))
private fun style(color: ColorProvider, size: Int, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)

@Composable
private fun FocusContent(context: Context, snapshot: FocusWidgetSnapshot, zone: ZoneId) {
    val open = actionStartActivity(
        Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val time = "%d:%02d".format(snapshot.todayMinutes / MINUTES_PER_HOUR, snapshot.todayMinutes % MINUTES_PER_HOUR)
    val line = snapshot.runningSince?.let {
        val since = DateFormat.getTimeFormat(context).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(Date.from(it))
        context.getString(
            if (snapshot.runningMode == GateMode.STOPWATCH) R.string.focus_widget_running_stopwatch else R.string.focus_widget_running,
            since,
        )
    } ?: context.getString(R.string.focus_widget_today)
    val description = context.getString(R.string.focus_widget_cd, snapshot.todayMinutes, line)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(tone { it.panel })
            .cornerRadius(WIDGET_RADIUS)
            .padding(16.dp)
            .clickable(open)
            .semantics { contentDescription = description },
    ) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.focus_card_title), style = style(tone { it.mist }, 13))
            Text(time, style = style(tone { it.bone }, 40, bold = true))
            Text(line, style = style(if (snapshot.runningSince != null) tone { it.verified } else tone { it.mist }, 12), maxLines = 2)
        }
    }
}

private const val MINUTES_PER_HOUR = 60
private val WIDGET_RADIUS = 24.dp
