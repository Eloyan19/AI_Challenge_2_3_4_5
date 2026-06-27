# AI Challenge — Android DeepSeek Agent

Android-приложение на Kotlin + Jetpack Compose, реализующее LLM-агента с инструментами поверх [DeepSeek API](https://platform.deepseek.com/).

## Что такое агент в этом приложении

Агент — это не просто обёртка над API. Это отдельная сущность (`SimpleAgent`), которая:

- **помнит контекст** — хранит историю сообщений и передаёт её в каждом запросе, позволяя вести полноценный диалог
- **инкапсулирует логику** — построение запроса, расчёт стоимости, обработка ошибок спрятаны внутри агента
- **умеет действовать** — сам решает, когда и какой инструмент вызвать, выполняет его и возвращает результат в LLM
- **управляет контекстом** — выбираемая стратегия определяет, что попадает в каждый запрос

```
SimpleAgent
├── run(userInput)        — принимает сообщение, запускает цикл
├── agentLoop()           — LLM → tool call? → execute → LLM → ... → ответ
├── buildLlmRequest()     — собирает LlmRequest через strategy.buildMessages()
├── appendMessages()      — добавляет сообщения в историю без LLM-вызова (оркестратор)
├── calculateCost()       — считает стоимость через providerConfig.pricingFor(model)
└── reset()               — очищает память (новая сессия)
```

## Агентный цикл (Agentic Loop)

```
Пользователь: "Какая погода в Ереване и сколько стоит 100 EUR в рублях?"
       ↓
  LLM думает: нужны инструменты
       ↓
  get_weather("Ереван")  +  convert_currency(100, "EUR", "RUB")
       ↓
  ToolExecutor выполняет запросы к внешним API
       ↓
  Результаты передаются обратно в LLM
       ↓
  LLM формирует финальный ответ пользователю
```

LLM сама решает, нужен ли инструмент, какой и с какими аргументами — это и есть агентность.

## Инструменты

| Инструмент | Что делает | API | Ключ |
|---|---|---|---|
| `get_weather` | Текущая погода для любого города | [wttr.in](https://wttr.in/) | не нужен |
| `convert_currency` | Конвертация валют по актуальному курсу | [Frankfurter.app](https://www.frankfurter.app/) | не нужен |
| `web_search` | Поиск актуальной информации в интернете | [Yandex XML Search](https://xml.yandex.com/) | нужен (бесплатный, 1000 запросов/день) |

Инструменты автоматически отключаются в Thinking Mode, так как DeepSeek-R1 не поддерживает tool calls.

---

## MCP-интеграция (Model Context Protocol)

Приложение поддерживает подключение внешних инструментов через [MCP](https://modelcontextprotocol.io/) — стандартный протокол, позволяющий LLM обращаться к инструментам на удалённом сервере.

### Зачем MCP вместо обычных tools

| | Function Calling (локальные tools) | MCP |
|---|---|---|
| Где живёт код инструмента | Внутри Android-приложения | На сервере |
| Добавить новый инструмент | Нужно обновить приложение | Добавляешь на сервер, приложение обнаружит автоматически |
| Схемы инструментов | Захардкожены в `ToolDefinitions` | Приходят с сервера через `tools/list` |
| При смене LLM | Нужно переписывать под новый формат | Сервер не трогаешь, меняется только маппер |

### Архитектура

```
MCP-сервер (jorchik.com/mcp/demo)
       │  tools/list → схемы инструментов
       │  tools/call → выполнение
       ▼
  McpClient
  ├── Lazy-инициализация сессии (mcp-session-id)
  ├── Кеш схем 5 минут (не тянуть на каждый turn)
  └── Graceful degradation: сервер недоступен → работаем с локальными tools
       │
       ▼
  ToolRegistry                   ← нейтральный агрегатор
  ├── allTools() = localTools + mcpClient.listTools()
  └── execute(toolCall):
        MCP-инструмент → McpClient.callTool()
        Локальный       → ToolExecutor.execute()
       │
       ▼
  SimpleAgent.buildLlmRequest()
  └── tools = toolRegistry.allTools()   ← 3 локальных + N с MCP-сервера
```

### LLM-агностичность

Схемы инструментов в MCP используют тот же JSON Schema что и OpenAI function calling — конвертация тривиальна. При смене DeepSeek на другой LLM:
- Меняется только `DeepSeekLlmService` (маппинг запроса/ответа)
- `McpClient`, `ToolRegistry`, инструменты на сервере — не трогаются

### MCP-сервер (jorchik.com)

Написан на Python + FastMCP, задеплоен на VPS, доступен по HTTPS через nginx.

```python
@mcp.tool()
def get_server_time() -> str:
    """Возвращает текущее время на сервере"""
    return datetime.now().strftime("Server time: %Y-%m-%d %H:%M:%S")

@mcp.tool()
def echo(message: str) -> str:
    """Повторяет сообщение через MCP-сервер"""
    return f"Echo: {message}"
```

Инфраструктура сервера: [github.com/Eloyan19/mcp-server](https://github.com/Eloyan19/mcp-server)

### Добавление нового MCP-инструмента

Приложение обновлять не нужно — достаточно добавить функцию на сервере:

```python
@mcp.tool()
def get_new_emails() -> list[str]:
    """Возвращает список новых писем"""
    return fetch_from_email_api()
```

При следующем запросе `tools/list` приложение автоматически увидит новый инструмент и передаст его схему в LLM.

---

## Task State Machine — оркестрация сложных задач

Для нетривиальных запросов приложение запускает **многоэтапный оркестратор** вместо прямого ответа агента. Решение принимается автоматически — пользователь не выбирает режим вручную.

### Как выглядит процесс

```
Пользователь отправляет сообщение
          ↓
  [Guard] sendMessage() проверяет TaskState:
    ≠ Idle → блокирует с объяснением ("Сначала одобри план ↑" и т.п.)
    = Idle → продолжает
          ↓
  TaskState: ANALYZING
  DetectComplexityUseCase
    → Шаг 1 — эвристика (без LLM, O(1)):
        ≤ 3 слов или начинается с «что такое», «сколько», «переведи» и т.д. → SIMPLE
        > 50 слов или содержит «пошагово», «составь план», «напиши код» и т.д. → COMPLEX
    → Шаг 2 — LLM-fallback (flash, maxTokens=10) только для неоднозначных случаев
    → "SIMPLE" → TaskState: Idle → обычный agent.run()
    → "COMPLEX" ↓
          ↓
  TaskState: PLANNING
  RunAgentSwarmUseCase (parallel):
    PLANNER  — строит пошаговый план
    CRITIC   — оценивает план, ищет риски
          ↓
  TaskState: AWAITING_INPUT
  UI: TaskStateCard показывает план + оценку Critic — ждём подтверждения
          ↓
  [Пользователь нажимает «Одобрить» / «Отклонить»]
          ↓
  «Одобрить»: TaskState: EXECUTION
    RunAgentSwarmUseCase: EXECUTOR — выполняет план
          ↓
    TaskState: VALIDATION
    RunAgentSwarmUseCase: VALIDATOR → JUDGE (PASS / FAIL)
          ↓
    PASS → TaskState: DONE → ответ в чат → ждём «Закрыть»
    FAIL → TaskState: VALIDATION_FAILED → три опции:
              • «Повторить» → AWAITING_INPUT (тот же план)
              • «Переработать план» → REPLANNING
              • «Закрыть» → Idle

  «Отклонить»: [Guard] replanCount < MAX_REPLAN_ATTEMPTS (3)?
    НЕТ → TaskState: ERROR («Превышен лимит перепланирований»)
    ДА  → replanCount++ → TaskState: REPLANNING
              PLANNER + CRITIC снова, с учётом причины отклонения
              ↓
          Новый план → TaskState: AWAITING_INPUT

  Аналогичный guard в «Переработать план» из ValidationFailed.
  replanCount сбрасывается в 0 при sendMessage(), confirmPlan() и dismissTaskState().
```

### Состояния машины состояний

| Состояние | Что происходит | UI |
|---|---|---|
| `Idle` | Обычный режим чата | — |
| `Analyzing` | Определяем сложность запроса | Спиннер «Анализирую задачу...» |
| `Planning` | Parallel-запросы к PLANNER и CRITIC | Спиннер «Планирую задачу...» |
| `AwaitingInput` | План готов, ждём решения пользователя | Карточка с планом + кнопки |
| `Execution` | EXECUTOR выполняет план | Спиннер «Выполняю план...» |
| `Validation` | VALIDATOR + JUDGE проверяют результат | Спиннер «Валидирую результат...» |
| `ValidationFailed` | Валидация не пройдена, нужно действие | Причина + «Повторить» / «Переработать план» / «Закрыть» |
| `Replanning` | Причина отклонения → новый план | Спиннер «Перепланирую...» |
| `Done` | Финальный ответ записан в чат | Зелёная галочка + первая строка + «Закрыть» |
| `Error` | Системная ошибка | Красный текст ошибки + «Закрыть» |

### TaskStateMachine — явная матрица переходов

`domain/model/TaskStateMachine.kt` — объект, который является единственным источником истины для допустимости переходов. Все изменения `taskState` в ViewModel проходят через него:

```kotlin
private fun setTaskState(newState: TaskState) {
    when (val t = TaskStateMachine.validate(_taskState.value, newState)) {
        is Allowed  -> _taskState.value = newState
        is Forbidden -> {
            Log.w("TaskStateMachine", t.reason)    // logcat
            _uiState.value = UiState.Error(t.reason) // видно в UI
        }
    }
}
```

**Жёстко запрещённые переходы** (возвращают `Forbidden`):
- `Idle → Execution / Validation / Done` — нет анализа и плана
- `Analyzing → Execution / Validation / Done` — нет плана
- `Planning → Execution / Done` — план не одобрен пользователем
- `AwaitingInput → Validation / Done` — выполнение не запущено
- `Execution → Done` — нет валидации

`AwaitingInput → Error` разрешён — используется когда `replanCount` достигает лимита `MAX_REPLAN_ATTEMPTS`.

### TaskStateCard — компонент в UI

Отображается между шапкой чата и историей сообщений. В состоянии `AwaitingInput`:

- Показывает полный текст плана (scrollable, до 8 строк)
- **Expandable секция «▼ Оценка плана (Critic)»** — по клику открывается оценка Critic-агента с замечаниями и рисками
- Кнопки «Одобрить» и «Отклонить»
- При нажатии «Отклонить» — диалог для ввода причины отклонения (опционально)

Пока задача в оркестраторе (не `Idle`), `sendMessage()` блокирует отправку с явным объяснением:
- `AwaitingInput` → «Сначала одобри или отклони план ↑»
- Любой активный этап → «Дождись завершения этапа `<имя>`»
- `Done` / `Error` → автосброс в `Idle`, сообщение отправляется

`Done` больше не исчезает мгновенно — карточка остаётся видимой до явного нажатия «Закрыть».

### Agent Swarm — параллельные агенты

Каждый «агент» в swarm — это отдельный независимый вызов `LlmService.chat()` в параллельном корутине:

```kotlin
// RunAgentSwarmUseCase
coroutineScope {
    roles.map { role ->
        async {
            val request = promptBuilder.build(role, userInput, taskState, compressedHistory, /* ... */)
            role to llmService.chat(request)
        }
    }.map { it.await() }.toMap()
}
```

**Важно:** swarm-вызовы идут напрямую через `LlmService`, минуя `SimpleAgent` — история агента **не засоряется** промежуточными плановыми сообщениями. В историю попадают только финальные user + assistant сообщения через `appendMessages()`.

### Роли агентов (AgentRole)

| Роль | `displayName` | Задача | Модель |
|---|---|---|---|
| `PLANNER` | Планировщик | Разбить задачу на чёткие шаги | `_selectedModel` пользователя |
| `CRITIC` | Критик | Найти риски и слабые места в плане | `_selectedModel` пользователя |
| `EXECUTOR` | Исполнитель | Выполнить план и сформулировать ответ | `_selectedModel` пользователя |
| `VALIDATOR` | Валидатор | Проверить, что ответ решает задачу | `backgroundModel` (flash) |
| `JUDGE` | Судья | Финальное решение PASS/FAIL на основе Executor+Validator | `backgroundModel` (flash) |

Все `systemPrompt` переведены на русский — это устраняет смешение языков в системных сообщениях, которое приводило к непредсказуемому языку ответов (LLM смешивал английские инструкции роли с русскими `stateContext` и историей пользователя).

**Исключение — ключевые слова VALIDATOR:** `TaskOrchestratorUseCase` проверяет ответ VALIDATOR через `startsWith("PASS", ignoreCase = true)`. Это код-зависимый протокол — ключевые слова `PASS`/`FAIL` должны оставаться на английском. Промпт явно фиксирует это: _«строго на английском, это фиксированный протокол»_.

### PromptBuilder — сборка запроса под роль

`DefaultPromptBuilder` строит `LlmRequest` по схеме:

```
[system: роль-специфичный промпт]         ← AgentRole.systemPrompt
[system: инструкции профиля пользователя] ← если активен профиль
[system: контекст задачи и состояния]     ← userInput, текущее состояние
[сжатая история]                          ← strategy.buildMessages(historySnapshot)
[user: userInput]                         ← сообщение пользователя
```

Контекст задачи (третий system-блок) описывает машине текущую стадию и её роль — это ключевое для правильного поведения JUDGE, VALIDATOR и других ролей.

### Персистентность плана (Room DB, v6)

Когда план готов и ждёт подтверждения (`AwaitingInput`), он сохраняется в таблице `task_plan` Room DB:

```
task_plan (singleton, id=1)
├── user_input   — оригинальный запрос пользователя
├── plan         — текст плана от PLANNER
├── critique     — оценка от CRITIC (nullable)
└── updated_at   — метка времени последнего обновления
```

Если пользователь закрыл приложение пока план ожидал подтверждения — при следующем запуске он увидит тот же `TaskStateCard` с тем же планом.

**При подтверждении плана** происходит важная вещь: план добавляется в `agent.history` как system-сообщение и сохраняется в `chat_messages` с `displayText = null` (невидимо в UI):

```kotlin
Message(
    role    = "system",
    content = "=== ЗАДАЧА В РАБОТЕ ===\nЗапрос: ...\n\nУтверждённый план:\n..."
)
```

Это позволяет контекстным стратегиям (Summary, StickyFacts, MemoryLayers) видеть план при следующих сжатиях — план становится полноценной частью истории диалога, а не просто временным состоянием приложения.

### Отличие от простого agent.run()

| | Прямой ответ | Task Orchestrator |
|---|---|---|
| Кто решает | LLM автоматически | Пользователь явно подтверждает план |
| История агента | agent.run() пишет напрямую | appendMessages() после Done |
| Инструменты | Да, через ToolExecutor | Нет (swarm вызывает LLM напрямую) |
| Параллельность | Последовательно | PLANNER + CRITIC параллельно |
| Когда применять | Вопросы, поиск, конвертация | Многошаговые задачи с неопределённостью |

---

## Архитектура

```
UI
├── MainActivity           — NavHost (chat / context_settings / memory_layers / profiles)
├── ChatScreen             — оркестратор: стейт + диалоги + вызовы компонентов
│     ├── ChatTopAppBar      — заголовок, кнопки API / настройки / сброс / ветка
│     ├── ChatMessageList    — LazyColumn + индикатор загрузки, владеет listState
│     ├── ChatInputRow       — поле ввода + кнопка «→», владеет prompt-стейтом
│     ├── ErrorBanner        — красная полоска при UiState.Error
│     ├── ResetConfirmationDialog / BranchCreationDialog — диалоги
│     └── BranchBar / ChatTurnItem — чипы веток и пузыри сообщений
└── ContextSettingsScreen  — выбор стратегии, параметры N, просмотр aux data

MainViewModel
├── sendMessage()              — guard по TaskState → Analyzing → detectAndPlan → simple/orchestrator
├── confirmPlan()              — Execution → onValidating(→ Validation) → Done / ValidationFailed
├── rejectPlan(reason)         — replanCount guard → Replanning → новый план (лимит: MAX_REPLAN_ATTEMPTS=3)
├── replanFromValidationFailed()— replanCount guard → Replanning из ValidationFailed
├── retryFromValidationFailed()— ValidationFailed → AwaitingInput (тот же план)
├── dismissTaskState()         — прямой сброс в Idle + clearTaskPlan + replanCount=0
├── applyStrategyConfig()      — меняет стратегию в runtime
├── createBranch / switchBranch / reconstructBranchHistory()
├── TaskOrchestratorUseCase
│     ├── DetectComplexityUseCase  — flash, "SIMPLE"/"COMPLEX"
│     └── RunAgentSwarmUseCase     — parallel coroutines per role
└── SimpleAgent
        ├── LlmService (interface)       ← DeepSeekLlmService через DI
        ├── LlmProviderConfig            ← модели, лимиты, цены
        ├── ContextStrategy (interface)  ← подставляется нужная реализация
        └── ToolRegistry                 ← агрегатор: localTools + MCP tools
                ├── ToolDefinitions.localTools
                │     ├── ToolExecutor
                │     │     ├── wttr.in
                │     │     ├── Frankfurter API
                │     │     └── Yandex XML Search API
                │     └── (схемы локальных tools)
                └── McpClient            ← MCP-протокол (JSON-RPC over HTTP)
                      └── jorchik.com/mcp/demo  (и любой другой MCP-сервер)

data/DeepSeekLlmService
└── DeepSeekApiService (Retrofit)  ← единственное место знающее о DeepSeek

Room DB (version 9)
├── chat_messages        — история сообщений (+branch_id)
├── conversation_summary — LLM-пересказ (Summary strategy)
├── sticky_facts         — извлечённые факты (StickyFacts strategy)
├── branches             — дерево веток (Branching strategy)
├── working_memory       — рабочая память сессии (MemoryLayers strategy)
├── long_term_memory     — долговременная память между сессиями (MemoryLayers strategy)
├── user_profiles        — именованные профили с инструкциями для ассистента
└── task_plan            — ожидающий план (singleton, TaskStateMachine persistence)
```

---

## UI-компоненты (ChatScreen)

`ChatScreen.kt` был декомпозирован с 725 до ~530 строк: два больших блока вынесены в отдельные файлы в `ui/components/`, шесть встроенных блоков превращены в приватные composable-функции.

### Файлы в ui/components/

| Файл | Что содержит | Строк |
|---|---|---|
| `TaskStateCard.kt` | Карточка Task Orchestrator (состояния, кнопки, expandable Critic) | 274 |
| `ApiSettingsSheet.kt` | Контент bottom sheet: выбор модели, Thinking Mode, Reasoning Effort, Max Tokens, Temperature | 184 |
| `ContextHeader.kt` | Панель токен-статистики, прогресс-бар контекста, метка стратегии + preview aux data | 127 |

Все компоненты следуют паттерну `TaskStateCard.kt`: **без ссылки на ViewModel**, только параметры + callback-лямбды.

### Приватные composable-ы в ChatScreen.kt

| Composable | Владеет стейтом | Назначение |
|---|---|---|
| `ChatTopAppBar` | — | TopAppBar: заголовок с моделью/стратегией, кнопки «⎇ Ветка» / API / ⚙ / Сброс |
| `ChatMessageList` | `listState`, `LaunchedEffect` | LazyColumn сообщений + индикатор загрузки с tool-статусом |
| `ChatInputRow` | `prompt: String` | OutlinedTextField + кнопка «→», disabled пока задача в оркестраторе |
| `ErrorBanner` | — | Красная полоска при `UiState.Error`: сообщение, флаг переполнения, «OK» |
| `ResetConfirmationDialog` | — | AlertDialog «Начать новую сессию?» |
| `BranchCreationDialog` | `newBranchName: String` | AlertDialog с полем имени ветки |
| `BranchBar` | — | Горизонтальный ряд FilterChip для переключения веток |
| `ChatTurnItem` + `TokenStatsText` | — | Пузыри user/assistant + метаданные токенов |

### Что осталось в ChatScreen (оркестратор)

Главная функция `ChatScreen` держит только то, что охватывает несколько UI-регионов и не может быть опущено вниз:

```kotlin
// Стейт-флоу: 14 collectAsStateWithLifecycle()
// Локальный стейт (охватывает 2+ UI-региона):
var showApiSheet     // → TopAppBar + ModalBottomSheet
var showBranchDialog // → TopAppBar + ChatTurnItem.onCreateBranch + BranchCreationDialog
var showResetDialog  // → TopAppBar + ResetConfirmationDialog
var checkpointId     // → TopAppBar action + ChatTurnItem.onCreateBranch → BranchCreationDialog
```

`prompt`, `newBranchName`, `listState` + `LaunchedEffect` — опущены вниз в owning-composable.

---

## Стратегии управления контекстом

Выбираются на отдельном экране (иконка ⚙ в шапке чата). Стратегия сохраняется в SharedPreferences и восстанавливается при перезапуске.

### Без сжатия (NONE)
Полная история передаётся в каждый запрос без изменений. Поведение по умолчанию.

### Sliding Window
Хранит только последние **N** сообщений — старые молча отбрасываются. Самый дешёвый способ удержать контекст в пределах лимита: токены снижаются сразу, но модель «забывает» начало диалога полностью.

```
strategy.prepareContext(history):
  while (history.size > N) history.removeAt(0)   // O(1) по токенам
```

### Summary (LLM-пересказ)
Старые сообщения заменяются коротким пересказом (3–6 предложений), сгенерированным через `deepseek-v4-flash`. Пересказ подставляется в каждый запрос как system-сообщение и персистируется в Room.

```
strategy.prepareContext → generateSummary(excess messages) → trim history
strategy.buildMessages  → [system: "Краткое содержание: ..."] + history
```

Позволяет сравнить расход токенов с компрессией и без, сохранив суть диалога.

### Sticky Facts
После каждого хода LLM (через `deepseek-v4-flash`) извлекает до 10 ключевых фактов из диалога и обновляет их список. В каждый запрос отправляются факты + последние N сообщений.

```
strategy.afterTurn(history) → extractFacts() → onAuxDataUpdated()
strategy.buildMessages      → [system: "Ключевые факты: • ..."] + history[-N:]
```

Хорошо подходит для диалогов, где важно помнить конкретные данные (имена, числа, решения), но не дословный ход беседы.

Когда задача в оркестраторе подтверждается — план попадает в историю как system-сообщение, и StickyFacts со временем извлечёт из него ключевые шаги и цели.

### Memory Layers (Слои памяти)

Явная трёхуровневая модель памяти ассистента. Каждый слой хранится отдельно и служит разной цели.

```
Каждый API-запрос собирается так:
  [system: долговременная память]   ← Layer 3
  [system: рабочая память]          ← Layer 2
  [последние N сообщений]           ← Layer 1
```

**Layer 1 — Краткосрочная (short-term)**
Последние N сообщений живого окна. Хранится только в оперативной памяти, очищается при сбросе сессии. Работает как sliding window: старые сообщения молча отбрасываются.

**Layer 2 — Рабочая (working memory)**
Текущая задача и контекст сессии, автоматически извлекаемые через `deepseek-v4-flash` после каждого хода. Содержит: что пользователь пытается сделать, активные сущности, контекст для следующего ответа. Персистируется в таблице `working_memory`, очищается при сбросе сессии.

```
strategy.afterTurn(history) → isTurnSignificant()? → extractWorkingMemory() → working_memory (DB)
strategy.buildMessages      → [system: "=== РАБОЧАЯ ПАМЯТЬ ===\n..."] + history[-N:]
```

**Фильтр незначительных ходов (`isTurnSignificant`):** LLM-вызов для извлечения рабочей памяти пропускается, если ход тривиален. Проверяется три условия — все должны быть выполнены:

| Условие | Почему |
|---|---|
| В истории ≥ 2 сообщений с текстом (user/assistant) | На первом ходу нечего извлекать |
| Последнее assistant-сообщение содержит текст | Tool-call-only ход: `content = null`, только `toolCalls` — нет текста для анализа |
| Суммарное кол-во слов (последний user + последний assistant) ≥ `minTurnWords` | Приветствия, «ок», «спасибо» не несут контекста |

Параметр `minTurnWords` настраивается через конструктор стратегии (дефолт — `12`) и может быть изменён в рантайме. Это позволяет подобрать порог под характер диалога без перекомпиляции:

```kotlin
// Создать с нестандартным порогом:
MemoryLayersStrategy(llmService, providerConfig, shortTermWindow = 8, minTurnWords = 6)

// Изменить на лету (например, из настроек):
(agent.strategy as? MemoryLayersStrategy)?.minTurnWords = 5
```

**Layer 3 — Долговременная (long-term memory)**
Профиль пользователя, важные предпочтения и решения. Единственный слой, который **не очищается при сбросе диалога** — живёт между сессиями. Категории: `profile`, `knowledge`, `decision`. Управляется явно: можно добавить вручную через диалог или извлечь из текущего диалога кнопкой (LLM-анализ).

```
strategy.buildMessages → [system: "=== ДОЛГОВРЕМЕННАЯ ПАМЯТЬ ===\n..."] + working + history
```

Экран **«Слои памяти»** (кнопка в настройках контекста) показывает все три слоя и позволяет добавлять / удалять записи долговременной памяти.

**Поведение при смене стратегии:** при переключении с любой стратегии на другую агент сбрасывается и перезагружает историю из DB по правилам новой стратегии — контекст всегда консистентен.

**Как слои доходят до агентов роя (оркестратор):** при каждом вызове PLANNER/CRITIC/EXECUTOR/VALIDATOR/JUDGE `MainViewModel` строит `compressedHistory` через `orchestratorHistory()`:

```
snapshot = agent.historySnapshot()          // история уже обрезана предыдущими ходами
compressedHistory = strategy.buildMessages(snapshot)
// → [system: LTM] + [system: WM] + история
```

`prepareContext` здесь **не вызывается** — это было бы мутацией живого состояния стратегии (обновление `_summary`, `_facts`, `_workingMemory` и сохранение в БД как побочный эффект). История уже поддерживается в нужном размере через `prepareContext` внутри `agent.run()` после каждого прямого ответа.

Эта история передаётся в `DefaultPromptBuilder`, который собирает финальный запрос:

```
[system: guardrails]
[system: роль агента]                     ← AgentRole.systemPrompt
[system: профиль пользователя]            ← activeProfile.instructions
[system: контекст задачи/состояния]       ← stateContext(TaskState)
[system: LTM]  ← из compressedHistory
[system: WM]   ← из compressedHistory
[история N сообщений]
[user: запрос]
```

**Исправленные баги оркестратора:**

| Баг | Симптом | Фикс |
|---|---|---|
| `orchestratorHistory()` вызывал `prepareContext` на snapshot | Мутировало живое состояние стратегии (`_summary`, `_facts`); при простых запросах — двойной LLM-вызов для суммаризации | Убран вызов `prepareContext` из `orchestratorHistory()` — только `buildMessages`, чистая функция |
| Откат истории при API-ошибке не работал с trimming-стратегиями | После ошибки сообщение пользователя оставалось в `agent.history` (индексный rollback ломался когда `prepareContext` уменьшал `history.size` ниже `turnStartIndex`) | `run()` делает снимок `historyBefore = history.toList()` до любых изменений и восстанавливает из него при исключении |
| `toolRegistry.allTools()` вызывался на каждой итерации цикла инструментов | До 5 сетевых запросов к MCP-серверу за один ход при cache miss | `allTools()` вызывается один раз в начале `agentLoop()`, список передаётся в `buildLlmRequest(tools)` |
| `replanCount` не сбрасывался в `confirmPlan()` | После отклонения планов и последующего подтверждения лимит перепланирований срабатывал преждевременно при ValidationFailed | Добавлен `replanCount = 0` в начало `confirmPlan()` |
| `retryFromValidationFailed()` не передавал critique | При повторе после ValidationFailed план показывался без замечаний Critic | `AwaitingInput` получает `s.critique`; добавлено поле `critique` в `TaskState.ValidationFailed` |
| `afterTurn` не вызывался после завершения задачи | Рабочая память и sticky facts не обновлялись после сложных оркестрированных задач | `afterTurn` добавлен в ветку `ExecutionDone` в `confirmPlan()` — off critical path через `viewModelScope.launch` |

### Branching (Ветвление)
Позволяет создавать независимые ветки диалога от любой точки истории и переключаться между ними.

**Как работает:**
1. Кнопка **⎇** на любом сообщении → диалог с именем ветки → новая ветка создаётся от этого сообщения.
2. Ветки отображаются в горизонтальной панели под шапкой; активная подсвечена.
3. При переключении агент восстанавливает историю через рекурсивный обход дерева:

```
main:    m1 — m2 — m3 — m4
                   ↑ checkpoint
branch-dev:        m5 — m6   (независимый путь)

getFullHistory(branch-dev):
  = main[m1..m3] + branch-dev[m5, m6]
```

Работает на произвольной глубине вложенности. Все ветки персистируются в Room; при переключении агент перегружает историю из БД.

---

## Статистика токенов и контекста

Под каждым ответом агента:
```
↑ 1.2K (кэш 800) • ↓ 256 • $0.000123 • 2.3 с
```

Панель над полем ввода показывает сессию целиком и заполненность окна контекста (лимит — 128K):
```
Ходов: 5  |  Сгенерировано: 3.2K tok  |  Потрачено: $0.00045
Контекст  [████████░░░░░░░░░░░]  42% · 54.2K/128.0K
🗜 Summary · последние 10 + пересказ
Пользователь спрашивал о погоде в Барселоне...
```

Цвет прогресс-бара:
- 🟢 < 70% — в норме
- 🟠 70–85% — приближается к пределу
- 🔴 > 85% — предупреждение; при ошибке показывается подсказка «Начните новую сессию»

---

## Профили пользователя

Именованные наборы системных инструкций для ассистента. Позволяют переключаться между разными «персонами» агента не меняя код.

**Как работает:**
- Каждый профиль — это имя + произвольный текст инструкций (системный промпт).
- Активный профиль инжектируется в каждый запрос как самое первое system-сообщение:
  ```
  [system: "=== ИНСТРУКЦИИ ПРОФИЛЯ ===\n<текст профиля>"]
  [system: рабочая память / summary / факты — если активна стратегия]
  [история сообщений]
  ```
- Активный профиль сохраняется в SharedPreferences и восстанавливается при перезапуске.
- При смене профиля рабочая память (Layer 2) очищается — новый профиль = новый контекст сессии.
- Долговременная память (Layer 3) и история сообщений при смене профиля **не сбрасываются**.

**Где хранится:** таблица `user_profiles` в Room DB (версия схемы v5).  
**Где открыть в приложении:** Настройки контекста → кнопка «Профили пользователя».

---

## LLM Provider Abstraction (Чистая архитектура)

### Проблема, которую мы решили

Исходно три стратегии (`SummaryStrategy`, `StickyFactsStrategy`, `MemoryLayersStrategy`) напрямую зависели от `data.DeepSeekApiService` — конкретного Retrofit-интерфейса. Это нарушение Clean Architecture: **domain не должен зависеть от data**. Замена DeepSeek на другой провайдер требовала правок в 4+ местах.

### Итоговая схема зависимостей

```
domain/                              ← ничего из data не импортирует
  model/
    Message.kt          ← перенесено из data/Models.kt
    Tool.kt             ← перенесено из data/Models.kt
    LlmRequest.kt       ← NEW: provider-agnostic запрос
    LlmResponse.kt      ← NEW: provider-agnostic ответ + LlmUsage
    LlmProviderConfig.kt← NEW: модели, лимиты, цены провайдера
    TaskState.kt        ← sealed class состояний (Idle/Analyzing/Planning/AwaitingInput/Execution/Validation/ValidationFailed/Done/Replanning/Error)
    TaskStateMachine.kt ← NEW: объект-валидатор переходов — матрица Allowed/Forbidden
    AgentRole.kt        ← NEW: enum PLANNER/CRITIC/EXECUTOR/VALIDATOR/JUDGE
    SwarmResult.kt      ← NEW: результат одного агента в swarm
    TaskPlanData.kt     ← NEW: domain-объект для персистентного плана
  LlmService.kt         ← NEW: interface { suspend fun chat(LlmRequest): LlmResponse }
  prompt/
    PromptBuilder.kt    ← NEW: interface для сборки LlmRequest под роль агента
    DefaultPromptBuilder.kt ← NEW: реализация (роль + профиль + контекст + история)
  usecase/
    DetectComplexityUseCase.kt  ← NEW: flash, "SIMPLE"/"COMPLEX"
    RunAgentSwarmUseCase.kt     ← NEW: parallel async per role
    TaskOrchestratorUseCase.kt  ← NEW: detectAndPlan / executeAndValidate / replan
  strategy/
    ContextStrategy.kt  — использует domain.model.Message
    SummaryStrategy.kt  — зависит от LlmService + LlmProviderConfig
    StickyFactsStrategy — зависит от LlmService + LlmProviderConfig
    MemoryLayersStrategy— зависит от LlmService + LlmProviderConfig

data/
  DeepSeekLlmService.kt ← NEW: реализует LlmService
                           маппит LlmRequest → ChatRequest (wire-format)
                           маппит ChatResponse → LlmResponse
                           ЕДИНСТВЕННОЕ место знающее о DeepSeek-специфике
  local/
    TaskPlanEntity.kt   ← NEW: Room entity для персистентного плана
    TaskPlanDao.kt      ← NEW: get / save (REPLACE) / clear
  DeepSeekApiService.kt — Retrofit-интерфейс, теперь скрыт внутри data
  Models.kt             — ChatRequest, ChatResponse, Thinking — только wire-format

ui/
  screens/
    ChatScreen.kt       — оркестратор (~160 строк): стейт + диалоги + вызовы компонентов
  components/
    TaskStateCard.kt    — Composable карточка машины состояний
    ApiSettingsSheet.kt ← NEW: настройки API (модель, thinking mode, temperature)
    ContextHeader.kt    ← NEW: панель статистики токенов и контекста

di/
  LlmModule.kt          ← NEW: @Binds LlmService → DeepSeekLlmService
  AppModule.kt          ← NEW: @Provides LlmProviderConfig (модели + цены DeepSeek)
  DatabaseModule.kt     ← обновлён: добавлен provideTaskPlanDao()
  AppComponent.kt       — добавлен LlmModule
```

### Что изолировано в DeepSeekLlmService

Вся DeepSeek-специфика — только здесь:
- `"thinking": {"type": "enabled"}` — активируется если `LlmRequest.thinkingEnabled == true`
- `reasoning_effort` — передаётся только вместе с thinking
- `temperature = null` когда thinking активен (API требует)
- `tools = null` когда thinking активен (несовместимы)

Остальные классы (`SimpleAgent`, стратегии, `MainViewModel`, оркестратор) этого **не знают**.

### LlmProviderConfig

Конфигурация провайдера, предоставляемая через DI:

```kotlin
LlmProviderConfig(
    providerName     = "DeepSeek",
    availableModels  = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
    defaultModel     = "deepseek-v4-flash",    // стартовая модель в UI
    backgroundModel  = "deepseek-v4-flash",    // модель для summary/facts/working memory
    contextLimit     = 128_000,                // токенов в окне контекста
    supportsThinking = true,
    supportsTools    = true,
    modelPricing = listOf(
        ModelPricing("deepseek-v4-flash",
            costPerMInputCached   = 0.0028,
            costPerMInputUncached = 0.14,
            costPerMOutput        = 0.28),
        ModelPricing("deepseek-v4-pro",
            costPerMInputCached   = 0.003625,
            costPerMInputUncached = 0.435,
            costPerMOutput        = 0.87)
    )
)
```

`SimpleAgent.calculateCost()` берёт цены из `providerConfig.pricingFor(model)` — ни одна цена в $$ больше не захардкожена в логике агента.

Стратегии и оркестратор вызывают `providerConfig.backgroundModel` вместо захардкоженной строки.

### Как сменить провайдера

**OpenAI-compatible (Groq, Mistral, Together, Ollama)** — wire-format одинаковый:
1. В `AppModule`: сменить `baseUrl`, API-ключ и `LlmProviderConfig` с нужными моделями и ценами.
2. В `LlmModule`: перебиндить `DeepSeekLlmService` на новый класс — или оставить как есть, если wire-format совместим.
3. Стратегии, `SimpleAgent`, `MainViewModel`, оркестратор — **не трогать**.

**Anthropic (другой формат API)**:
1. Написать `AnthropicLlmService : LlmService` в `data/` с нужным HTTP-клиентом.
2. Сменить `@Binds` в `LlmModule`.
3. Добавить `AnthropicProviderConfig` в `AppModule`.
4. Вся остальная логика — без изменений.

---

## История схемы Room DB

| Версия | Что добавлено |
|---|---|
| v1 | `chat_messages` |
| v2 | `conversation_summary` (Summary strategy) |
| v3 | `branches`, `sticky_facts`, поле `branch_id` в `chat_messages` |
| v4 | `working_memory`, `long_term_memory` (MemoryLayers strategy) |
| v5 | `user_profiles` (именованные профили) |
| v6 | `task_plan` (Task State Machine persistence) |
| v7 | Индексы на `chat_messages(branch_id)` и `chat_messages(turnId)` |
| v8 | Уникальный индекс на `long_term_memory(key_name)` — предотвращает дубликаты при параллельных upsert |
| v9 | Составной уникальный индекс `long_term_memory(category, key_name)` вместо `(key_name)` — факты с одинаковым именем в разных категориях (`profile.name` и `knowledge.name`) больше не конфликтуют при upsert |

---

## Тесты

Фреймворк: **JUnit 4 + MockK + kotlinx-coroutines-test**.

| Файл | Что покрывает | Тестов |
|---|---|---|
| `DetectComplexityUseCaseTest` | Эвристический фильтр (явно SIMPLE / явно COMPLEX без LLM-вызова) и LLM-fallback | 20 |
| `MemoryLayersStrategyTest` | Парсинг долговременных фактов из JSON (включая markdown-обёртки, пустые/битые ответы), `buildMessages` с разными слоями, guard `isTurnSignificant` | 22 |
| `TaskOrchestratorUseCaseTest` | `detectAndPlan`, `executeAndValidate`, `replan` — все ветви дерева решений | 17 |
| `ChatUseCasesTest` | Делегирование use cases к репозиторию | 11 |

**Итого: 70 unit-тестов.**

### Принципы покрытия

- **Use cases** тестируются изолированно через mockk-заглушки зависимостей; реальные LLM- и DB-вызовы не происходят.
- **Стратегии** — только чистая логика: парсинг, фильтрация, сборка сообщений. Внешние вызовы мокируются.
- **Swarm-роли** матчатся через `eq(listOf(AgentRole.X))` — тест явно проверяет какой именно набор агентов вызывается на каждом шаге оркестратора.
- **VALIDATOR** покрыт тремя форматами FAIL-ответа: `"FAIL\nпричина"`, `"FAIL причина"`, `"FAIL"` — потому что модель на практике возвращает любой из них.

---

## Отладка памяти и оркестрации

### Logcat — теги

| Тег | Что показывает | Когда появляется |
|---|---|---|
| `OrchestratorCtx` | стратегия, размер snapshot, кол-во сообщений к агентам роя, system-префиксы (LTM, WM) | каждый вызов `detectAndPlan` / `executeAndValidate` / `replan` |
| `MemoryLayersStrategy` | обновление / пропуск рабочей памяти и причина | после каждого хода при Memory Layers |
| `SimpleAgent` | размер истории и тип стратегии перед `run()` | прямые ответы |

Пример вывода `OrchestratorCtx` при исправно работающей системе:

```
D/OrchestratorCtx: strategy=MEMORY_LAYERS | snapshot=8 msgs → sent to swarm=10 msgs |
    prefixes=[=== ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (профиль и знания..., === РАБОЧАЯ ПАМЯТЬ (текущая сессия) ===]
```

Что проверять:
- `snapshot` — сколько сообщений в текущей истории агента
- `sent to swarm` — сколько сообщений (включая system-префиксы) получили агенты роя
- `prefixes` — LTM и WM system-сообщения доходят до агентов роя
- `MemoryLayersStrategy: Working memory updated` — рабочая память обновилась после завершения задачи

### Database Inspector

**View → App Inspection → Database Inspector** (только с подключённым устройством/эмулятором).

| Таблица | Содержит | Что проверять |
|---|---|---|
| `working_memory` | текущая рабочая память (Layer 2) | обновляется после каждого значимого хода и после завершения оркестрированной задачи |
| `long_term_memory` | LTM-записи по категориям | накапливается между сессиями |
| `sticky_facts` | факты при StickyFacts стратегии | обновляется после каждого хода |
| `conversation_summary` | summary при Summary стратегии | обновляется когда история превышает N |

### UI — ContextHeader

Панель под чатом показывает `auxData` — первые 2 строки рабочей памяти (или summary / facts). Если после сложной задачи текст изменился — `afterTurn` сработал корректно.

### OkHttp BODY logging (debug-сборка)

В debug-сборке (`./gradlew installDebug`) в Logcat тег `okhttp.OkHttpClient` выводит полный JSON каждого запроса к DeepSeek — массив `messages` со всеми role/content. Это самый полный способ проверить точный порядок системных сообщений (guardrails → роль → профиль → LTM → WM → история → user) для каждого агента роя.

В release-сборке интерцептор отключён автоматически (`debugImplementation`).

---

## Настройка

Добавь ключи в `local.properties`:

```properties
DEEPSEEK_API_KEY=sk-...

# Опционально — для инструмента web_search
# Ключ получить на https://xml.yandex.com/ (бесплатно, 1000 запросов/день)
YANDEX_SEARCH_USER=логин_яндекса
YANDEX_SEARCH_KEY=ключ_из_xml.yandex.com
```

## Стек

- Kotlin, Jetpack Compose, Material3
- Retrofit 2 + OkHttp + Gson
- ViewModel + StateFlow (MVVM + Clean Architecture)
- Room v9 (chat_messages, summary, sticky_facts, branches, working_memory, long_term_memory, user_profiles, task_plan)
- Navigation Compose (chat ↔ context settings ↔ memory layers ↔ profiles)
- DeepSeek API, wttr.in, Frankfurter, Yandex XML Search
