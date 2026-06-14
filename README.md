# AI Challenge — Android DeepSeek Agent

Android-приложение на Kotlin + Jetpack Compose, реализующее LLM-агента с инструментами поверх [DeepSeek API](https://platform.deepseek.com/).

## Что такое агент в этом приложении

Агент — это не просто обёртка над API. Это отдельная сущность (`SimpleAgent`), которая:

- **помнит контекст** — хранит историю сообщений и передаёт её в каждом запросе, позволяя вести полноценный диалог
- **инкапсулирует логику** — построение запроса, расчёт стоимости, обработка ошибок спрятаны внутри агента
- **умеет действовать** — сам решает когда и какой инструмент вызвать, выполняет его и возвращает результат в LLM

```
SimpleAgent
├── run(userInput)     — принимает сообщение пользователя, запускает цикл
├── agentLoop()        — LLM → tool call? → execute → LLM → ... → ответ
├── buildRequest()     — собирает ChatRequest с историей и схемами инструментов
├── calculateCost()    — считает стоимость по токенам
└── reset()            — очищает память (новая сессия)
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
| `get_weather` | Текущая погода для любого города | [Open-Meteo](https://open-meteo.com/) | не нужен |
| `convert_currency` | Конвертация валют по актуальному курсу | [Frankfurter.app](https://www.frankfurter.app/) | не нужен |
| `web_search` | Поиск актуальной информации в интернете | [Yandex XML Search](https://xml.yandex.com/) | нужен (бесплатный, 1000 запросов/день) |

Инструменты автоматически отключаются в Thinking Mode, так как DeepSeek-R1 не поддерживает tool calls.

## Архитектура

```
UI (MainActivity)
    └── MainViewModel            — держит агента, управляет состоянием
            └── SimpleAgent      — ядро: память + LLM-логика + цикл инструментов
                    ├── DeepSeekApiService (Retrofit) — запросы к DeepSeek
                    ├── ToolDefinitions               — JSON-схемы инструментов
                    └── ToolExecutor                  — выполнение инструментов
                            ├── Open-Meteo API        — погода
                            ├── Frankfurter API       — валюты
                            └── Yandex XML Search API — поиск
```

## Функции

- Чат-интерфейс с историей диалога
- Поддержка моделей `deepseek-v4-flash` и `deepseek-v4-pro`
- Thinking Mode с выбором Reasoning Effort
- Инструменты: погода, конвертер валют, поиск в интернете
- Статус активного инструмента отображается во время загрузки
- Токены, стоимость и время ответа под каждым сообщением
- Кнопка «Новая сессия» — сбрасывает память агента

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
- ViewModel + StateFlow (MVVM)
- DeepSeek API, Open-Meteo, Frankfurter, Yandex XML Search
