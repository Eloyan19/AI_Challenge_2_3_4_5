package com.example.petapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.ui.MainViewModel

@Composable
fun ContextHeader(
    stats: MainViewModel.SessionStats,
    strategyType: StrategyType,
    keepLastN: Int,
    auxData: String?
) {
    if (stats.turnCount == 0 && strategyType == StrategyType.NONE) return

    val fraction = stats.contextFraction
    val barColor = when {
        fraction >= 0.85f -> MaterialTheme.colorScheme.error
        fraction >= 0.70f -> Color(0xFFF57C00)
        else              -> MaterialTheme.colorScheme.primary
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {

            if (stats.turnCount > 0) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    StatChip("Ходов", "${stats.turnCount}")
                    StatChip("Сгенерировано", "${fmtTok(stats.totalCompletionTokens)} tok")
                    StatChip("Потрачено", "${"%.5f".format(stats.totalCost)} \$")
                }

                if (stats.contextTokens > 0) {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Контекст", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress   = { fraction.coerceIn(0f, 1f) },
                            modifier   = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color      = barColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                        Text(
                            "${(fraction * 100).toInt()}% · ${fmtTok(stats.contextTokens)}/${fmtTok(stats.contextLimit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor
                        )
                    }
                    if (fraction >= 0.85f) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "⚠ Контекст почти заполнен",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (strategyType != StrategyType.NONE && strategyType != StrategyType.BRANCHING) {
                if (stats.turnCount > 0) Spacer(Modifier.height(4.dp))
                val strategyLabel = when (strategyType) {
                    StrategyType.SLIDING_WINDOW -> "⊟ Sliding Window · последние $keepLastN сообщений"
                    StrategyType.SUMMARY        -> "🗜 Summary · последние $keepLastN + пересказ"
                    StrategyType.STICKY_FACTS   -> "📌 Sticky Facts · последние $keepLastN + факты"
                    else -> ""
                }
                Text(
                    text       = strategyLabel,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary
                )
                if (auxData != null && strategyType != StrategyType.SLIDING_WINDOW) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = auxData.lines().take(2).joinToString(" ").take(140) +
                                   if (auxData.length > 140) "…" else "",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun fmtTok(n: Int): String =
    if (n >= 1_000) "${"%.1f".format(n / 1_000.0)}K" else n.toString()
