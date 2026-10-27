package com.assismohanty.awaken.feature.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.SectionTitle
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.RecapEntry
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** RECAPS on Status (DESIGN_SYSTEM §8.2): every stored recap, newest first, reopenable (GD §14.6). */
@Composable
internal fun RecapsSection(recaps: List<RecapEntry>, onOpenRecap: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        SectionTitle(lexString(R.string.recaps_title))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            recaps.forEach { recap ->
                val name = if (recap.yearly) {
                    recap.start.year.toString()
                } else {
                    YearMonth.from(recap.start).format(DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale()))
                }
                ListRow(
                    title = name,
                    icon = AwakenGlyphs.Calendar,
                    iconTint = AwakenTheme.colors.accent,
                    onClick = { onOpenRecap(recap.periodKey) },
                    trailing = {
                        Icon(
                            AwakenGlyphs.ChevronRight,
                            contentDescription = lexString(R.string.recap_open_cd, name),
                            tint = AwakenTheme.colors.textSecondary,
                            modifier = Modifier.size(AwakenTheme.dimens.iconSmall),
                        )
                    },
                )
            }
        }
    }
}
