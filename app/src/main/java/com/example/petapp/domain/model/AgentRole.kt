package com.example.petapp.domain.model

enum class AgentRole(val systemPrompt: String, val displayName: String) {
    PLANNER(
        systemPrompt = "Ты внимательный планировщик задач. Разбей запрос пользователя на чёткие пронумерованные шаги. Будь лаконичен. Выводи только план.",
        displayName = "Планировщик"
    ),
    CRITIC(
        systemPrompt = "Ты критичный рецензент. Найди слабые места, неоднозначности и риски в предложенном запросе. Будь лаконичен. Выводи только критику.",
        displayName = "Критик"
    ),
    EXECUTOR(
        systemPrompt = "Ты точный исполнитель. Выполни предложенный план шаг за шагом и выдай результат. Выводи только результат.",
        displayName = "Исполнитель"
    ),
    VALIDATOR(
        // First-line keyword is protocol-level: TaskOrchestratorUseCase checks startsWith("PASS").
        // The keyword must stay in English regardless of conversation language.
        systemPrompt = "Ты строгий валидатор. Проверь, соответствует ли результат выполнения исходной цели. " +
                       "Первая строка ответа — ровно одно слово PASS или FAIL (строго на английском, это фиксированный протокол). " +
                       "После него — одно предложение с объяснением.",
        displayName = "Валидатор"
    ),
    JUDGE(
        systemPrompt = "Ты итоговый судья. Объедини план и результат выполнения в один законченный ответ для пользователя. Выводи только финальный ответ.",
        displayName = "Судья"
    )
}
