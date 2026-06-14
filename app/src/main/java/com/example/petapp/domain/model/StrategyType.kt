package com.example.petapp.domain.model

enum class StrategyType(val displayName: String) {
    NONE("Без сжатия"),
    SLIDING_WINDOW("Скользящее окно"),
    SUMMARY("LLM-пересказ"),
    STICKY_FACTS("Sticky Facts"),
    BRANCHING("Ветвление")
}
