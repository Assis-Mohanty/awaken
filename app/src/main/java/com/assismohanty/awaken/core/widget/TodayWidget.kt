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
import com.assismohanty.awaken.core.designsystem.theme.AwakenColors
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
}

/**
 * The Today widget, 4x2 (ARCH §3.6): rank badge, level, streak flame, "3/5 cleared", the next quest, tap → Today.
 * Glance can't read `AwakenTheme`, so it draws with the same colour tokens (`AwakenColors`) and nothing else.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val snapshot = entry.observeToday()(flowOf(Unit)).first().toWidgetSnapshot()
        provideContent { WidgetContent(context, snapshot) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val colors = AwakenColors()

private fun text(color: Color, size: Int, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

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
            .background(colors.panel)
            .cornerRadius(WIDGET_RADIUS)
            .padding(12.dp)
            .clickable(open)
            .semantics { contentDescription = summary },
    ) {
        if (snapshot.hasHunter) Filled(context, snapshot) else Empty(context)
    }
}

@Composable
private fun Empty(context: Context) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.app_name), style = text(colors.system, 18, bold = true))
        Text(context.getString(R.string.widget_empty), style = text(colors.mist, 13))
    }
}

@Composable
private fun Filled(context: Context, snapshot: WidgetSnapshot) {
    val rankColor = colors.rank(snapshot.rank)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier.size(44.dp).background(rankColor).cornerRadius(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(snapshot.rank.name, style = text(colors.void, 22, bold = true))
            }
            Spacer(GlanceModifier.size(4.dp))
            Text(context.getString(R.string.level_short, snapshot.level), style = text(colors.bone, 13, bold = true))
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_flame),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(colors.crit)),
                    modifier = GlanceModifier.size(18.dp),
                )
                Text(" ${snapshot.streak}", style = text(colors.crit, 15, bold = true))
                Spacer(GlanceModifier.width(12.dp))
                Text(
                    context.getString(R.string.widget_progress, snapshot.completed, snapshot.total),
                    style = text(if (snapshot.cleared) colors.verified else colors.bone, 15, bold = true),
                )
            }
            Spacer(GlanceModifier.size(6.dp))
            Text(
                text = snapshot.nextQuest?.let { context.getString(R.string.widget_next, it) } ?: context.getString(R.string.widget_all_done),
                style = text(colors.mist, 13),
                maxLines = 2,
            )
        }
    }
}

private fun summaryText(context: Context, snapshot: WidgetSnapshot): String =
    if (!snapshot.hasHunter) {
        context.getString(R.string.widget_empty)
    } else {
        context.getString(
            R.string.widget_cd,
            snapshot.rank.name,
            snapshot.level,
            snapshot.streak,
            snapshot.completed,
            snapshot.total,
            snapshot.nextQuest ?: context.getString(R.string.widget_all_done),
        )
    }

private val WIDGET_RADIUS = 14.dp
