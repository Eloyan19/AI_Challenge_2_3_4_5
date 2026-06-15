package com.example.petapp.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.petapp.di.LocalViewModelFactory
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.ui.ContextSettingsViewModel
import com.example.petapp.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSettingsScreen(
    navController: NavController,
    chatViewModel: MainViewModel,
    settingsViewModel: ContextSettingsViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val currentStrategy  by chatViewModel.currentStrategyType.collectAsStateWithLifecycle()
    val auxData          by chatViewModel.auxData.collectAsStateWithLifecycle()

    val selectedStrategy by settingsViewModel.selectedStrategy.collectAsStateWithLifecycle()
    val keepLastN        by settingsViewModel.keepLastN.collectAsStateWithLifecycle()

    // Local text mirror so the field shows partial input while typing
    var keepLastNText by remember { mutableStateOf(keepLastN.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление контекстом") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text  = "Стратегия управления контекстом",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            StrategyOption(
                type        = StrategyType.NONE,
                selected    = selectedStrategy,
                onSelect    = { settingsViewModel.selectStrategy(it) },
                description = "Полная история без каких-либо изменений."
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            StrategyOption(
                type        = StrategyType.SLIDING_WINDOW,
                selected    = selectedStrategy,
                onSelect    = { settingsViewModel.selectStrategy(it) },
                description = "Хранит только последние N сообщений. Старые отбрасываются без замены."
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            StrategyOption(
                type        = StrategyType.SUMMARY,
                selected    = selectedStrategy,
                onSelect    = { settingsViewModel.selectStrategy(it) },
                description = "Старые сообщения заменяются коротким пересказом (deepseek-v4-flash). Экономит токены, сохраняя суть."
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            StrategyOption(
                type        = StrategyType.STICKY_FACTS,
                selected    = selectedStrategy,
                onSelect    = { settingsViewModel.selectStrategy(it) },
                description = "После каждого хода факты обновляются через deepseek-v4-flash (бюджетно). Факты + последние N сообщений отправляются в каждый запрос."
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            StrategyOption(
                type        = StrategyType.BRANCHING,
                selected    = selectedStrategy,
                onSelect    = { settingsViewModel.selectStrategy(it) },
                description = "Можно сохранить точку ветвления в диалоге, создавать независимые ветки и переключаться между ними. Контекст не сжимается."
            )

            Spacer(Modifier.height(20.dp))

            if (selectedStrategy in listOf(
                    StrategyType.SLIDING_WINDOW,
                    StrategyType.SUMMARY,
                    StrategyType.STICKY_FACTS
                )
            ) {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text  = "Количество сообщений в «живом» окне",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = when (selectedStrategy) {
                        StrategyType.SLIDING_WINDOW -> "Хранить последних N сообщений — остальные отброшены."
                        StrategyType.SUMMARY        -> "Хранить последних N сообщений как есть, старые → summary."
                        StrategyType.STICKY_FACTS   -> "Отправлять последних N сообщений + блок фактов."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = keepLastNText,
                    onValueChange = { text ->
                        keepLastNText = text
                        text.toIntOrNull()?.let { settingsViewModel.setKeepLastN(it) }
                    },
                    label           = { Text("N (2–100)") },
                    modifier        = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    isError         = keepLastNText.toIntOrNull()?.let { it < 2 || it > 100 } == true
                )
            }

            if (selectedStrategy == currentStrategy && auxData != null &&
                currentStrategy in listOf(StrategyType.SUMMARY, StrategyType.STICKY_FACTS)
            ) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                val title = if (currentStrategy == StrategyType.SUMMARY) "Текущий summary" else "Текущие факты"
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Surface(
                    color  = MaterialTheme.colorScheme.surfaceVariant,
                    shape  = MaterialTheme.shapes.small
                ) {
                    Text(
                        text     = auxData!!,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Explain what happens to history when switching strategies on the fly
            if (selectedStrategy != currentStrategy) {
                Card(
                    colors  = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text  = "При смене стратегии:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (selectedStrategy) {
                                StrategyType.BRANCHING ->
                                    "Текущая история станет веткой «main». Вы сможете создавать ответвления от любого сообщения."
                                StrategyType.SLIDING_WINDOW ->
                                    "Со следующего запроса ИИ будет видеть только последние $keepLastN сообщений. Ваша история в чате сохранится полностью."
                                StrategyType.SUMMARY ->
                                    "Старые сообщения будут сжаты в пересказ при следующем запросе. История в чате не изменится."
                                StrategyType.STICKY_FACTS ->
                                    "После следующего ответа будут извлечены ключевые факты диалога. История в чате не изменится."
                                StrategyType.NONE ->
                                    "Контекст сжиматься не будет. ИИ будет видеть всю историю на каждый запрос."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    chatViewModel.applyStrategyConfig(selectedStrategy, keepLastN)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить")
            }
        }
    }
}

@Composable
private fun StrategyOption(
    type: StrategyType,
    selected: StrategyType,
    onSelect: (StrategyType) -> Unit,
    description: String
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = type == selected,
            onClick  = { onSelect(type) }
        )
        Column(modifier = Modifier.padding(start = 8.dp, top = 10.dp)) {
            Text(type.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
