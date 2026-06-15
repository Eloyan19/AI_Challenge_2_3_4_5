package com.example.petapp.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, viewModel: MainViewModel) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val chatHistory     by viewModel.chatHistory.collectAsStateWithLifecycle()
    val sessionStats    by viewModel.sessionStats.collectAsStateWithLifecycle()
    val strategyType    by viewModel.currentStrategyType.collectAsStateWithLifecycle()
    val keepLastN       by viewModel.keepLastN.collectAsStateWithLifecycle()
    val auxData         by viewModel.auxData.collectAsStateWithLifecycle()
    val branches        by viewModel.branches.collectAsStateWithLifecycle()
    val activeBranchId  by viewModel.activeBranchId.collectAsStateWithLifecycle()

    val selectedModel    by viewModel.selectedModel.collectAsStateWithLifecycle()
    val thinkingEnabled  by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val reasoningEffort  by viewModel.reasoningEffort.collectAsStateWithLifecycle()
    val maxTokensText    by viewModel.maxTokensText.collectAsStateWithLifecycle()
    val temperatureText  by viewModel.temperatureText.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    var prompt           by remember { mutableStateOf("") }
    var showApiSheet     by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showResetDialog  by remember { mutableStateOf(false) }
    var newBranchName    by remember { mutableStateOf("") }
    var checkpointId     by remember { mutableStateOf<Long?>(null) }

    val isLoading  = uiState is MainViewModel.UiState.Loading
    val toolStatus = (uiState as? MainViewModel.UiState.Loading)?.toolStatus

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) listState.animateScrollToItem(chatHistory.size - 1)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title   = { Text("Начать новую сессию?") },
            text    = { Text("Вся история диалога и ветки будут удалены без возможности восстановления.") },
            confirmButton   = {
                Button(onClick = { viewModel.newSession(); showResetDialog = false }) {
                    Text("Сбросить")
                }
            },
            dismissButton   = {
                TextButton(onClick = { showResetDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false; newBranchName = "" },
            title   = { Text("Создать ветку") },
            text    = {
                Column {
                    Text(
                        text  = if (checkpointId != null) "Ответвление от текущего сообщения"
                                else "Ответвление от конца истории",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = newBranchName,
                        onValueChange = { newBranchName = it },
                        label         = { Text("Название ветки") },
                        singleLine    = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newBranchName.isNotBlank(),
                    onClick = {
                        viewModel.createBranch(newBranchName.trim(), checkpointId)
                        showBranchDialog = false
                        newBranchName    = ""
                        checkpointId     = null
                    }
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showBranchDialog = false; newBranchName = "" }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Agent")
                        val subtitle = buildList {
                            add(selectedModel)
                            if (strategyType != StrategyType.NONE) add(strategyType.displayName)
                        }.joinToString(" · ")
                        Text(
                            text  = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                },
                actions = {
                    if (strategyType == StrategyType.BRANCHING) {
                        TextButton(onClick = {
                            checkpointId = chatHistory.lastOrNull()?.lastMessageId
                            showBranchDialog = true
                        }) { Text("⎇ Ветка") }
                    }
                    TextButton(onClick = { showApiSheet = true }) {
                        Text(
                            text  = "API",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { navController.navigate("context_settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Контекст")
                    }
                    TextButton(onClick = { showResetDialog = true }) { Text("Сброс") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Branch selector row ────────────────────────────────────────────
            if (strategyType == StrategyType.BRANCHING && branches.isNotEmpty()) {
                BranchBar(
                    branches       = branches,
                    activeBranchId = activeBranchId,
                    onSwitch       = { viewModel.switchBranch(it) }
                )
                HorizontalDivider()
            }

            // ── Chat history ───────────────────────────────────────────────────
            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f).fillMaxWidth(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chatHistory, key = { it.hashCode() }) { turn ->
                    ChatTurnItem(
                        turn              = turn,
                        showBranchButton  = strategyType == StrategyType.BRANCHING,
                        onCreateBranch    = { msgId ->
                            checkpointId     = msgId
                            showBranchDialog = true
                        }
                    )
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment     = Alignment.CenterVertically
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

            // ── Stats / context header ─────────────────────────────────────────
            ContextHeader(
                stats        = sessionStats,
                strategyType = strategyType,
                keepLastN    = keepLastN,
                auxData      = auxData
            )

            // ── Error banner ───────────────────────────────────────────────────
            if (uiState is MainViewModel.UiState.Error) {
                val err = uiState as MainViewModel.UiState.Error
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (err.isContextOverflow) {
                                Text(
                                    "Контекст переполнен — начните новую сессию",
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

            // ── Input ──────────────────────────────────────────────────────────
            HorizontalDivider()
            Row(
                modifier          = Modifier.fillMaxWidth().padding(8.dp),
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
                        viewModel.sendMessage(prompt.trim())
                        prompt = ""
                    },
                    enabled        = prompt.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) { Text("→") }
            }
        }
    }

    // ── API settings bottom sheet ──────────────────────────────────────────────
    if (showApiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showApiSheet = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ApiSettingsSheetContent(
                selectedModel        = selectedModel,
                thinkingEnabled      = thinkingEnabled,
                reasoningEffort      = reasoningEffort,
                maxTokensText        = maxTokensText,
                temperatureText      = temperatureText,
                onModelSelected      = { viewModel.setModel(it) },
                onThinkingChanged    = { viewModel.setThinkingEnabled(it) },
                onEffortSelected     = { viewModel.setReasoningEffort(it) },
                onMaxTokensChanged   = { viewModel.setMaxTokensText(it) },
                onTemperatureChanged = { viewModel.setTemperatureText(it) }
            )
        }
    }
}

// ── API settings sheet content ─────────────────────────────────────────────────

@Composable
private fun ApiSettingsSheetContent(
    selectedModel: String,
    thinkingEnabled: Boolean,
    reasoningEffort: String,
    maxTokensText: String,
    temperatureText: String,
    onModelSelected: (String) -> Unit,
    onThinkingChanged: (Boolean) -> Unit,
    onEffortSelected: (String) -> Unit,
    onMaxTokensChanged: (String) -> Unit,
    onTemperatureChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text       = "Настройки API",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(16.dp))

        // Model
        Text(
            text  = "Модель",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        ModelDropdown(
            models     = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            selected   = selectedModel,
            onSelected = onModelSelected
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Thinking mode
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Thinking Mode",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text  = "Расширенные рассуждения. Отключает инструменты и temperature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = thinkingEnabled, onCheckedChange = onThinkingChanged)
        }

        if (thinkingEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Reasoning Effort",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            ReasoningEffortDropdown(
                efforts    = listOf("min", "low", "medium", "high", "max"),
                selected   = reasoningEffort,
                onSelected = onEffortSelected
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Advanced
        Text(
            text       = "Дополнительно",
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value           = maxTokensText,
            onValueChange   = onMaxTokensChanged,
            label           = { Text("Max Tokens (пусто = по умолчанию модели)") },
            modifier        = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true
        )

        if (!thinkingEnabled) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value           = temperatureText,
                onValueChange   = onTemperatureChanged,
                label           = { Text("Temperature (0.0–2.0, пусто = по умолчанию)") },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine      = true
            )
        }
    }
}

// ── Branch bar ─────────────────────────────────────────────────────────────────

@Composable
fun BranchBar(branches: List<Branch>, activeBranchId: Long, onSwitch: (Long) -> Unit) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = "⎇",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        branches.forEach { branch ->
            FilterChip(
                selected = branch.id == activeBranchId,
                onClick  = { onSwitch(branch.id) },
                label    = { Text(branch.name, style = MaterialTheme.typography.labelSmall) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// ── Context header ─────────────────────────────────────────────────────────────

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
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

// ── Chat turn ──────────────────────────────────────────────────────────────────

@Composable
fun ChatTurnItem(
    turn: MainViewModel.ChatTurn,
    showBranchButton: Boolean,
    onCreateBranch: (Long?) -> Unit
) {
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color    = MaterialTheme.colorScheme.primaryContainer,
                shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.widthIn(max = maxBubbleWidth)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
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
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TokenStatsText(turn)
                        if (showBranchButton) {
                            TextButton(
                                onClick        = { onCreateBranch(turn.lastMessageId) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text  = "⎇",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenStatsText(turn: MainViewModel.ChatTurn) {
    val parts = buildList {
        turn.tokenInfo?.let { t ->
            val cached = if (t.cachedTokens > 0) " (кэш ${fmtTok(t.cachedTokens)})" else ""
            add("↑ ${fmtTok(t.promptTokens)}$cached")
            add("↓ ${fmtTok(t.completionTokens)}")
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
            modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(text = { Text(m) }, onClick = { onSelected(m); expanded = false })
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
            modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            efforts.forEach { e ->
                DropdownMenuItem(text = { Text(e) }, onClick = { onSelected(e); expanded = false })
            }
        }
    }
}
