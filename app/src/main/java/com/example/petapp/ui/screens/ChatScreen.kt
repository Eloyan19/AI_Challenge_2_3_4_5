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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.domain.model.TaskState
import com.example.petapp.ui.MainViewModel
import com.example.petapp.ui.components.ApiSettingsSheet
import com.example.petapp.ui.components.ContextHeader
import com.example.petapp.ui.components.TaskStateCard
import com.example.petapp.ui.components.fmtTok

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
    val taskState        by viewModel.taskState.collectAsStateWithLifecycle()
    val activeProfile    by viewModel.activeProfile.collectAsStateWithLifecycle()

    var showApiSheet     by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showResetDialog  by remember { mutableStateOf(false) }
    var checkpointId     by remember { mutableStateOf<Long?>(null) }

    val isLoading  = uiState is MainViewModel.UiState.Loading
    val toolStatus = (uiState as? MainViewModel.UiState.Loading)?.toolStatus

    if (showResetDialog) {
        ResetConfirmationDialog(
            onConfirm = { viewModel.newSession(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showBranchDialog) {
        BranchCreationDialog(
            checkpointId = checkpointId,
            onCreate = { name, cpId ->
                viewModel.createBranch(name, cpId)
                showBranchDialog = false
                checkpointId = null
            },
            onDismiss = { showBranchDialog = false }
        )
    }

    Scaffold(
        topBar = {
            ChatTopAppBar(
                selectedModel     = selectedModel,
                strategyType      = strategyType,
                activeProfileName = activeProfile?.name,
                showBranchAction  = strategyType == StrategyType.BRANCHING,
                onOpenApiSheet    = { showApiSheet = true },
                onOpenSettings    = { navController.navigate("context_settings") },
                onOpenResetDialog = { showResetDialog = true },
                onOpenBranchDialog = {
                    checkpointId = chatHistory.lastOrNull()?.lastMessageId
                    showBranchDialog = true
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (strategyType == StrategyType.BRANCHING && branches.isNotEmpty()) {
                BranchBar(
                    branches       = branches,
                    activeBranchId = activeBranchId,
                    onSwitch       = { viewModel.switchBranch(it) }
                )
                HorizontalDivider()
            }

            ChatMessageList(
                chatHistory      = chatHistory,
                isLoading        = isLoading,
                toolStatus       = toolStatus,
                showBranchButton = strategyType == StrategyType.BRANCHING,
                onCreateBranch   = { msgId ->
                    checkpointId = msgId
                    showBranchDialog = true
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            ContextHeader(
                stats        = sessionStats,
                strategyType = strategyType,
                keepLastN    = keepLastN,
                auxData      = auxData
            )

            if (taskState !is TaskState.Idle) {
                TaskStateCard(
                    taskState = taskState,
                    onConfirm = { viewModel.confirmPlan() },
                    onReject  = { reason -> viewModel.rejectPlan(reason) },
                    onDismiss = { viewModel.dismissTaskState() },
                    onRetry   = { viewModel.retryFromValidationFailed() },
                    onReplan  = { reason -> viewModel.replanFromValidationFailed(reason) }
                )
            }

            if (uiState is MainViewModel.UiState.Error) {
                val err = uiState as MainViewModel.UiState.Error
                ErrorBanner(
                    message           = err.message,
                    isContextOverflow = err.isContextOverflow,
                    onDismiss         = { viewModel.dismissError() }
                )
            }

            ChatInputRow(
                isEnabled = !isLoading && taskState is TaskState.Idle,
                onSend    = { viewModel.sendMessage(it) }
            )
        }
    }

    if (showApiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showApiSheet = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ApiSettingsSheet(
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

// ── Dialogs ────────────────────────────────────────────────────────────────────

@Composable
private fun ResetConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Начать новую сессию?") },
        text             = { Text("Вся история диалога и ветки будут удалены без возможности восстановления.") },
        confirmButton    = { Button(onClick = onConfirm) { Text("Сбросить") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun BranchCreationDialog(
    checkpointId: Long?,
    onCreate: (name: String, checkpointId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var newBranchName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDismiss(); newBranchName = "" },
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
                onClick = { onCreate(newBranchName.trim(), checkpointId); newBranchName = "" }
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(); newBranchName = "" }) { Text("Отмена") }
        }
    )
}

// ── Top app bar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
    selectedModel: String,
    strategyType: StrategyType,
    activeProfileName: String?,
    showBranchAction: Boolean,
    onOpenApiSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenResetDialog: () -> Unit,
    onOpenBranchDialog: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text("AI Agent")
                val subtitle = buildList {
                    add(selectedModel)
                    if (strategyType != StrategyType.NONE) add(strategyType.displayName)
                    if (activeProfileName != null) add("👤 $activeProfileName")
                }.joinToString(" · ")
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        actions = {
            if (showBranchAction) {
                TextButton(onClick = onOpenBranchDialog) { Text("⎇ Ветка") }
            }
            TextButton(onClick = onOpenApiSheet) {
                Text(
                    text       = "API",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Контекст")
            }
            TextButton(onClick = onOpenResetDialog) { Text("Сброс") }
        }
    )
}

// ── Chat message list ──────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    chatHistory: List<MainViewModel.ChatTurn>,
    isLoading: Boolean,
    toolStatus: String?,
    showBranchButton: Boolean,
    onCreateBranch: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) listState.animateScrollToItem(chatHistory.size - 1)
    }
    LazyColumn(
        state               = listState,
        modifier            = modifier,
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(chatHistory, key = { it.lastMessageId ?: it.hashCode() }) { turn ->
            ChatTurnItem(
                turn             = turn,
                showBranchButton = showBranchButton,
                onCreateBranch   = onCreateBranch
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
}

// ── Input row ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputRow(
    isEnabled: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var prompt by remember { mutableStateOf("") }
    HorizontalDivider()
    Row(
        modifier          = modifier
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
            onClick = { onSend(prompt.trim()); prompt = "" },
            enabled        = prompt.isNotBlank() && isEnabled,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) { Text("→") }
    }
}

// ── Error banner ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(
    message: String,
    isContextOverflow: Boolean,
    onDismiss: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isContextOverflow) {
                    Text(
                        "Контекст переполнен — начните новую сессию",
                        color      = MaterialTheme.colorScheme.onErrorContainer,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text  = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

// ── Branch bar ─────────────────────────────────────────────────────────────────

@Composable
private fun BranchBar(branches: List<Branch>, activeBranchId: Long, onSwitch: (Long) -> Unit) {
    Row(
        modifier              = Modifier
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

// ── Chat turn ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatTurnItem(
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
