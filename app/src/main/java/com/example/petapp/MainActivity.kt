
package com.example.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
            PetAppTheme() {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Состояния всех полей
                var prompt by remember { mutableStateOf("") }
                var maxTokensText by remember { mutableStateOf("") }
                var stopText by remember { mutableStateOf("") }
                var temperatureText by remember { mutableStateOf("") }

                // Новые состояния
                var selectedModel by remember { mutableStateOf("deepseek-v4-flash") }
                var thinkingEnabled by remember { mutableStateOf(false) }
                var reasoningEffort by remember { mutableStateOf("") }

                val models = listOf("deepseek-v4-flash", "deepseek-v4-pro")
                val reasoningEfforts = listOf("min", "low", "medium", "high", "max")

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Запрос к DeepSeek",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Промпт
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Ваш промпт") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )

                        TextButton(
                            onClick = { prompt = "" },
                            enabled = prompt.isNotEmpty(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Очистить запрос")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Выбор модели
                        ModelDropdown(
                            models = models,
                            selected = selectedModel,
                            onSelected = { selectedModel = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Thinking Mode Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Thinking Mode")
                            Switch(
                                checked = thinkingEnabled,
                                onCheckedChange = { thinkingEnabled = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reasoning Effort (только при включенном thinking)
                        if (thinkingEnabled) {
                            ReasoningEffortDropdown(
                                efforts = reasoningEfforts,
                                selected = reasoningEffort,
                                onSelected = { reasoningEffort = it }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Max Tokens
                        OutlinedTextField(
                            value = maxTokensText,
                            onValueChange = { maxTokensText = it },
                            label = { Text("Max Tokens") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stop Sequence
                        OutlinedTextField(
                            value = stopText,
                            onValueChange = { stopText = it },
                            label = { Text("Stop Sequence") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Temperature (только когда thinking выключен)
                        if (!thinkingEnabled) {
                            OutlinedTextField(
                                value = temperatureText,
                                onValueChange = { temperatureText = it },
                                label = { Text("Temperature") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Кнопка отправки
                        Button(
                            onClick = {
                                val maxTokens = maxTokensText.toIntOrNull()
                                val stop = stopText.trim().let {
                                    if (it.isNotEmpty()) listOf(it) else null
                                }
                                val temperature = temperatureText.toDoubleOrNull()
                                val effort = reasoningEffort.trim().let {
                                    if (it.isNotEmpty()) it else null
                                }
                                viewModel.sendRequest(
                                    model = selectedModel,
                                    prompt = prompt,
                                    maxTokens = maxTokens,
                                    stop = stop,
                                    temperature = temperature,
                                    thinkingEnabled = thinkingEnabled,
                                    reasoningEffort = effort
                                )
                            },
                            enabled = prompt.isNotBlank() && uiState !is MainViewModel.UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState is MainViewModel.UiState.Loading) "Загрузка..." else "Отправить")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Кнопка очистки ответа
                        TextButton(
                            onClick = { viewModel.resetState() },
                            enabled = uiState is MainViewModel.UiState.Success || uiState is MainViewModel.UiState.Error
                        ) {
                            Text("Очистить ответ")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Отображение результата
                        when (val state = uiState) {
                            is MainViewModel.UiState.Idle -> {}
                            is MainViewModel.UiState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            }
                            is MainViewModel.UiState.Success -> {
                                Column {
                                    // Статистика (всегда видна первой)
                                    state.tokens?.let { tokens ->
                                        Text(
                                            text = "Токены: запрос ${tokens.promptTokens} (кэш ${tokens.cachedTokens}), ответ ${tokens.completionTokens}, всего ${tokens.totalTokens}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    state.cost?.let { cost ->
                                        Text(
                                            text = "Стоимость: ≈ ${"%.6f".format(cost)} $",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Время ответа: ${"%.1f".format(state.responseTimeSeconds)} сек",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Сам ответ
                                    Text(
                                        text = state.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            is MainViewModel.UiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Компоненты выпадающих списков (можно вынести в отдельный файл)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdown(models: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Модель") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningEffortDropdown(efforts: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Reasoning Effort") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            efforts.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(effort) },
                    onClick = {
                        onSelected(effort)
                        expanded = false
                    }
                )
            }
        }
    }
}