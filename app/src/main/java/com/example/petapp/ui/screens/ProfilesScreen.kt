package com.example.petapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.petapp.domain.model.UserProfile
import com.example.petapp.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val profiles    by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профили") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Создать профиль")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (profiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Нет профилей",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Создайте профиль с инструкциями для ассистента. Он будет отправляться в каждый запрос как системное сообщение.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (activeProfile != null) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.switchProfile(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Отключить профиль")
                        }
                    }
                }

                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = activeProfile?.id == profile.id,
                        onSelect = { viewModel.switchProfile(profile.id) },
                        onEdit = { editingProfile = profile },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }

            item {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Создать профиль")
                }
            }
        }
    }

    if (showCreateDialog) {
        ProfileEditDialog(
            profile = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, instructions ->
                viewModel.saveProfile(null, name, instructions)
                showCreateDialog = false
            }
        )
    }

    editingProfile?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onConfirm = { name, instructions ->
                viewModel.saveProfile(profile.id, name, instructions)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Активный", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = profile.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!isActive) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать")
                }
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var instructions by remember { mutableStateOf(profile?.instructions ?: "") }
    val isValid = name.isNotBlank() && instructions.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Создать профиль" else "Редактировать профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название профиля") },
                    placeholder = { Text("Например: Серьёзный, Лёгкий, Кодинг") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Инструкции для ассистента") },
                    placeholder = { Text("Отвечай кратко и по делу. Используй примеры кода...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
                if (instructions.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Предпросмотр (как отправляется в API):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "system: \"=== ИНСТРУКЦИИ ПРОФИЛЯ ===\n${instructions.take(120)}${if (instructions.length > 120) "…" else ""}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(name.trim(), instructions.trim()) },
                enabled = isValid
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
