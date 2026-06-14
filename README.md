# AI Challenge — Android DeepSeek Agent

Android-приложение на Kotlin + Jetpack Compose, реализующее простого LLM-агента поверх [DeepSeek API](https://platform.deepseek.com/).

## Что такое агент в этом приложении

Агент — это не просто обёртка над API. Это отдельная сущность (`SimpleAgent`), которая:

- **помнит контекст** — хранит историю сообщений и передаёт её в каждом запросе, позволяя вести полноценный диалог
- **инкапсулирует логику** — построение запроса, расчёт стоимости, обработка ошибок спрятаны внутри агента
- **управляет конфигурацией** — модель, температура, thinking mode меняются через `AgentConfig`, не затрагивая UI

```
SimpleAgent
├── run(userInput)     — принимает сообщение пользователя
├── step()            — выполняет один шаг: запрос → ответ → сохранение в историю
├── buildRequest()    — собирает ChatRequest с полным контекстом
├── calculateCost()   — считает стоимость по токенам
└── reset()           — очищает память (новая сессия)
```

## Архитектура

```
UI (MainActivity)
    └── MainViewModel       — держит агента, управляет состоянием
            └── SimpleAgent — ядро: память + LLM-логика
                    └── DeepSeekApiService (Retrofit)
```

## Функции

- Чат-интерфейс с историей диалога
- Поддержка моделей `deepseek-v4-flash` и `deepseek-v4-pro`
- Thinking Mode с выбором Reasoning Effort
- Отображение токенов, стоимости и времени ответа под каждым сообщением
- Кнопка «Новая сессия» — сбрасывает память агента

## Настройка

Добавь API-ключ в `local.properties`:

```
DEEPSEEK_API_KEY=sk-...
```

## Стек

- Kotlin, Jetpack Compose, Material3
- Retrofit 2 + OkHttp + Gson
- ViewModel + StateFlow (MVVM)
- DeepSeek API
