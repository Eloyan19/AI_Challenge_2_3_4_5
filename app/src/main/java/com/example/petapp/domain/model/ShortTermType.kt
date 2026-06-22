package com.example.petapp.domain.model

enum class ShortTermType(val displayName: String) {
    NONE("Без ограничений"),
    SLIDING_WINDOW("Скользящее окно"),
    STICKY_FACTS("Sticky Facts")
}
