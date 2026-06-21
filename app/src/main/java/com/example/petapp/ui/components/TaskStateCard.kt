package com.example.petapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petapp.domain.model.TaskState

@Composable
fun TaskStateCard(
    taskState: TaskState,
    onConfirm: () -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = taskState !is TaskState.Idle,
        enter   = fadeIn(),
        exit    = fadeOut()
    ) {
        Surface(
            shape    = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.fillMaxWidth()
        ) {
            when (taskState) {
                is TaskState.Planning   -> SpinnerRow("Планирую задачу...")
                is TaskState.Execution  -> SpinnerRow("Выполняю план...")
                is TaskState.Validation -> SpinnerRow("Валидирую результат...")
                is TaskState.Replanning -> SpinnerRow("Перепланирую: ${taskState.reason.take(60).ifBlank { "создаю новый план" }}")
                is TaskState.Done       -> DoneRow(taskState.finalResult)
                is TaskState.AwaitingInput -> AwaitingInputSection(
                    plan      = taskState.plan,
                    critique  = taskState.critique,
                    onConfirm = onConfirm,
                    onReject  = onReject
                )
                is TaskState.Error -> ErrorRow(
                    message   = taskState.message,
                    onDismiss = onDismiss
                )
                is TaskState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SpinnerRow(label: String) {
    Row(
        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DoneRow(result: String) {
    Row(
        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            text  = result.lines().first().take(80),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AwaitingInputSection(
    plan: String,
    critique: String?,
    onConfirm: () -> Unit,
    onReject: (String) -> Unit
) {
    var showRejectDialog  by remember { mutableStateOf(false) }
    var rejectionReason   by remember { mutableStateOf("") }
    var critiqueExpanded  by remember { mutableStateOf(false) }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false; rejectionReason = "" },
            title   = { Text("Причина отклонения") },
            text    = {
                OutlinedTextField(
                    value         = rejectionReason,
                    onValueChange = { rejectionReason = it },
                    placeholder   = { Text("Опционально: объясните что не так") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReject(rejectionReason.trim())
                    showRejectDialog = false
                    rejectionReason  = ""
                }) { Text("Отклонить") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false; rejectionReason = "" }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text       = "План готов — ожидаю подтверждения",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        // ── Plan text ──────────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
            Text(
                text     = plan,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                maxLines = 8
            )
        }

        // ── Critique expandable ────────────────────────────────────────────────
        if (!critique.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clickable { critiqueExpanded = !critiqueExpanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = if (critiqueExpanded) "▲ Оценка плана (Critic)" else "▼ Оценка плана (Critic)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            AnimatedVisibility(visible = critiqueExpanded) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text     = critique,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row {
            Button(onClick = onConfirm) { Text("Одобрить") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showRejectDialog = true }) { Text("Отклонить") }
        }
    }
}

@Composable
private fun ErrorRow(message: String, onDismiss: () -> Unit) {
    Row(
        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = message,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text("Закрыть") }
    }
}
