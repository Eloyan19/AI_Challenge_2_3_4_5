package com.example.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.petapp.ui.MainViewModel
import com.example.petapp.ui.theme.PetAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PetAppTheme {
                AgentChatScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(viewModel: MainViewModel = viewModel()) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val chatHistory  by viewModel.chatHistory.collectAsStateWithLifecycle()
    val sessionStats by viewModel.sessionStats.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var prompt           by remember { mutableStateOf("") }
    var selectedModel    by remember { mutableStateOf("deepseek-v4-flash") }
    var thinkingEnabled  by remember { mutableStateOf(false) }
    var reasoningEffort  by remember { mutableStateOf("medium") }
    var maxTokensText    by remember { mutableStateOf("") }
    var temperatureText  by remember { mutableStateOf("") }
    var advancedExpanded by remember { mutableStateOf(false) }

    val isLoading  = uiState is MainViewModel.UiState.Loading
    val toolStatus = (uiState as? MainViewModel.UiState.Loading)?.toolStatus

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) listState.animateScrollToItem(chatHistory.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Agent") },
                actions = {
                    TextButton(onClick = { viewModel.newSession() }) {
                        Text("Новая сессия")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Config ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ModelDropdown(
                    models   = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
                    selected = selectedModel,
                    onSelected = { selectedModel = it }
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Thinking Mode", modifier = Modifier.weight(1f))
                    Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
                }

                if (thinkingEnabled) {
                    Spacer(Modifier.height(8.dp))
                    ReasoningEffortDropdown(
                        efforts    = listOf("min", "low", "medium", "high", "max"),
                        selected   = reasoningEffort,
                        onSelected = { reasoningEffort = it }
                    )
                }

                TextButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text  = if (advancedExpanded) "Скрыть ▲" else "Дополнительно ▼",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (advancedExpanded) {
                    OutlinedTextField(
                        value         = maxTokensText,
                        onValueChange = { maxTokensText = it },
                        label         = { Text("Max Tokens") },
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true
                    )
                    if (!thinkingEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value         = temperatureText,
                            onValueChange = { temperatureText = it },
                            label         = { Text("Temperature") },
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine    = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            HorizontalDivider()

            // ── Chat history ────────────────────────────────────────────────
            LazyColumn(
                state           = listState,
                modifier        = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chatHistory) { turn -> ChatTurnItem(turn) }

                if (isLoading) {
                    item {
                        Row(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment   = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            if (toolStatus != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text  = toolStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Session stats + context bar ─────────────────────────────────
            ContextHeader(stats = sessionStats)

            // ── Error banner ────────────────────────────────────────────────
            if (uiState is MainViewModel.UiState.Error) {
                val err = uiState as MainViewModel.UiState.Error
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (err.isContextOverflow) {
                                Text(
                                    text       = "Контекст переполнен — начните новую сессию",
                                    color      = MaterialTheme.colorScheme.onErrorContainer,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text  = err.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
                    }
                }
            }

            // ── Input ───────────────────────────────────────────────────────
            HorizontalDivider()
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value         = prompt,
                    onValueChange = { prompt = it },
                    placeholder   = { Text("Введите сообщение...") },
                    modifier      = Modifier.weight(1f),
                    maxLines      = 4,
                    shape         = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendMessage(
                            userInput        = prompt.trim(),
                            model            = selectedModel,
                            maxTokens        = maxTokensText.toIntOrNull(),
                            temperature      = temperatureText.toDoubleOrNull(),
                            thinkingEnabled  = thinkingEnabled,
                            reasoningEffort  = reasoningEffort.takeIf { thinkingEnabled }
                        )
                        prompt = ""
                    },
                    enabled        = prompt.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("→")
                }
            }
        }
    }
}

// ── Session stats panel ────────────────────────────────────────────────────────

@Composable
fun ContextHeader(stats: MainViewModel.SessionStats) {
    if (stats.turnCount == 0) return

    val fraction = stats.contextFraction
    val barColor = when {
        fraction >= 0.85f -> MaterialTheme.colorScheme.error
        fraction >= 0.70f -> Color(0xFFF57C00)   // оранжевый
        else              -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {

            // Строка с суммарной статистикой сессии
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                StatChip("Ходов", "${stats.turnCount}")
                StatChip("Сгенерировано", "${fmtTokens(stats.totalCompletionTokens)} tok")
                StatChip("Потрачено", "${"%.5f".format(stats.totalCost)} \$")
            }

            // Прогресс-бар контекста (только когда есть данные)
            if (stats.contextTokens > 0) {
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text  = "Контекст",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress     = { fraction.coerceIn(0f, 1f) },
                        modifier     = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color        = barColor,
                        trackColor   = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    Text(
                        text  = "${(fraction * 100).toInt()}% · ${fmtTokens(stats.contextTokens)}/${fmtTokens(stats.contextLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = barColor
                    )
                }

                if (fraction >= 0.85f) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = "⚠ Контекст почти заполнен — следующий запрос может завершиться ошибкой",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun fmtTokens(n: Int): String =
    if (n >= 1_000) "${"%.1f".format(n / 1_000.0)}K" else n.toString()

// ── Chat turn ──────────────────────────────────────────────────────────────────

@Composable
fun ChatTurnItem(turn: MainViewModel.ChatTurn) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // User bubble — right-aligned
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color  = MaterialTheme.colorScheme.primaryContainer,
                shape  = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text     = turn.userMessage,
                    modifier = Modifier.padding(12.dp),
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                    style    = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Agent bubble — left-aligned
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text  = turn.agentResponse,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    TokenStatsText(turn)
                }
            }
        }
    }
}

@Composable
private fun TokenStatsText(turn: MainViewModel.ChatTurn) {
    val parts = buildList {
        turn.tokenInfo?.let { t ->
            val cached = if (t.cachedTokens > 0) " (кэш ${fmtTokens(t.cachedTokens)})" else ""
            add("↑ ${fmtTokens(t.promptTokens)}$cached")
            add("↓ ${fmtTokens(t.completionTokens)}")
        }
        turn.cost?.let { add("${"%.6f".format(it)} \$") }
        turn.durationSec?.let { add("${"%.1f".format(it)} с") }
    }
    if (parts.isEmpty()) return
    Text(
        text  = parts.joinToString(" • "),
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
        style = MaterialTheme.typography.labelSmall
    )
}

// ── Dropdowns ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdown(models: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = selected,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Модель") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text    = { Text(model) },
                    onClick = { onSelected(model); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningEffortDropdown(efforts: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = selected,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Reasoning Effort") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            efforts.forEach { effort ->
                DropdownMenuItem(
                    text    = { Text(effort) },
                    onClick = { onSelected(effort); expanded = false }
                )
            }
        }
    }
}
