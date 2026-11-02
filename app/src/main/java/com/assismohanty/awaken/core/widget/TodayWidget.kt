package com.assismohanty.awaken.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.lumiDrawable
import com.assismohanty.awaken.core.designsystem.components.lumiStatusRes
import com.assismohanty.awaken.core.designsystem.theme.AwakenPalette
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveTodayUseCase
import com.assismohanty.awaken.core.work.ACTION_OPEN_TODAY
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Hilt access for the widget: Glance creates widgets and receivers itself, so they can't be constructor-injected. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun observeToday(): ObserveTodayUseCase
    fun dayRefresher(): DayRefresher
    fun hunterDao(): com.assismohanty.awaken.core.data.db.dao.HunterDao
    fun focusDao(): com.assismohanty.awaken.core.data.db.dao.FocusDao
    fun gateRunDao(): com.assismohanty.awaken.core.data.db.dao.GateRunDao
    fun clock(): com.assismohanty.awaken.core.data.clock.Clock
    fun reelWidgetData(): com.assismohanty.awaken.core.reelcounter.ReelWidgetData
}

/**
 * The Today widget, 4x2 (ARCH §3.6): level and rank, streak flame, "3 of 5 done", the next quest, tap → Today.
 * Glance can't read `AwakenTheme`, so it draws with the same colour tokens (`AwakenPalette`, light and dark) and nothing else.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val lumi = entry.reelWidgetData().currentLumi()
        val snapshot = entry.observeToday()(flowOf(Unit)).first().toWidgetSnapshot()
            .copy(reelsToday = lumi?.total, reelsLumiStage = lumi?.state?.stage ?: 1)
        provideContent { WidgetContent(context, snapshot) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val light = AwakenPalette.Light
private val dark = AwakenPalette.Dark

/** Follows the phone light/dark setting, like the app (DESIGN_SYSTEM §2.1b). */
private fun tone(pick: (AwakenPalette) -> Color): ColorProvider = dayNight(day = pick(light), night = pick(dark))

private fun text(color: ColorProvider, size: Int, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot) {
    val open = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val summary = summaryText(context, snapshot)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(tone { it.panel })
            .cornerRadius(WIDGET_RADIUS)
            .padding(16.dp)
            .clickable(open)
            .semantics { contentDescription = summary },
    ) {
        if (snapshot.hasHunter) Filled(context, snapshot) else Empty(context)
    }
}

@Composable
private fun Empty(context: Context) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.app_name), style = text(tone { it.bone }, 17, bold = true))
        Text(context.getString(R.string.widget_empty), style = text(tone { it.mist }, 13))
    }
}

@Composable
private fun Filled(context: Context, snapshot: WidgetSnapshot) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(snapshot.level.toString(), style = text(tone { it.bone }, 34, bold = true))
            Text(context.getString(R.string.widget_level, snapshot.rank.name), style = text(tone { it.mist }, 12))
        }
        Spacer(GlanceModifier.width(20.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_flame),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tone { it.crit }),
                    modifier = GlanceModifier.size(16.dp),
                )
                Text(" ${snapshot.streak}", style = text(tone { it.bone }, 15, bold = true))
                Spacer(GlanceModifier.width(12.dp))
                Text(
                    context.getString(R.string.widget_progress, snapshot.completed, snapshot.total),
                    style = text(if (snapshot.cleared) tone { it.verified } else tone { it.bone }, 15, bold = true),
                )
            }
            Spacer(GlanceModifier.size(6.dp))
            Text(
                text = snapshot.nextQuest?.let { context.getString(R.string.widget_next, it) } ?: context.getString(R.string.widget_all_done),
                style = text(tone { it.mist }, 13),
                maxLines = 2,
            )
            snapshot.reelsToday?.let {
                Spacer(GlanceModifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(lumiDrawable(snapshot.reelsLumiStage)),
                        contentDescription = context.resources.getQuantityString(
                            R.plurals.reel_lumi_cd, it, context.getString(lumiStatusRes(snapshot.reelsLumiStage)), it,
                        ),
                        modifier = GlanceModifier.size(LUMI_SIZE),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(context.getString(R.string.widget_reels, it), style = text(tone { p -> p.mist }, 13))
                }
            }
        }
    }
}

private fun summaryText(context: Context, snapshot: WidgetSnapshot): String =
    if (!snapshot.hasHunter) {
        context.getString(R.string.widget_empty)
    } else {
        val base = context.getString(
            R.string.widget_cd,
            snapshot.rank.name,
            snapshot.level,
            snapshot.streak,
            snapshot.completed,
            snapshot.total,
            snapshot.nextQuest ?: context.getString(R.string.widget_all_done),
        )
        snapshot.reelsToday?.let { "$base ${context.getString(R.string.widget_reels, it)}." } ?: base
    }

private val WIDGET_RADIUS = 24.dp

/** Lumi next to the reel total: small enough to sit in the 4x2 layout's last line. */
private val LUMI_SIZE = 24.dp
