
package com.example.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

                // Состояния полей ввода (живут в Composable, не в ViewModel)
                var prompt by remember { mutableStateOf("") }
                var maxTokensText by remember { mutableStateOf("") }
                var stopText by remember { mutableStateOf("") }
                var temperatureText by remember { mutableStateOf("") }

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
                        // Заголовок формы
                        Text(
                            text = "Запрос к DeepSeek",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Поле для промпта
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Ваш промпт") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Max Tokens
                        OutlinedTextField(
                            value = maxTokensText,
                            onValueChange = { maxTokensText = it },
                            label = { Text("Max Tokens") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
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

                        // Temperature
                        OutlinedTextField(
                            value = temperatureText,
                            onValueChange = { temperatureText = it },
                            label = { Text("Temperature") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Кнопка отправки
                        Button(
                            onClick = {
                                // Парсим опциональные параметры
                                val maxTokens = maxTokensText.toIntOrNull()
                                val stop = stopText.trim().let { if (it.isNotEmpty()) listOf(it) else null }
                                val temperature = temperatureText.toDoubleOrNull()
                                viewModel.sendRequest(prompt, maxTokens, stop, temperature)
                            },
                            enabled = prompt.isNotBlank() && uiState !is MainViewModel.UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState is MainViewModel.UiState.Loading) "Загрузка..." else "Отправить")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Отображение результата
                        when (val state = uiState) {
                            is MainViewModel.UiState.Idle -> {
                                // Ничего не показываем, форма пустая
                            }
                            is MainViewModel.UiState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            }
                            is MainViewModel.UiState.Success -> {
                                Text(
                                    text = "Ответ:\n${state.content}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                TextButton(onClick = { viewModel.resetState() }) {
                                    Text("Новый запрос")
                                }
                            }
                            is MainViewModel.UiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                TextButton(onClick = { viewModel.resetState() }) {
                                    Text("Попробовать снова")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}