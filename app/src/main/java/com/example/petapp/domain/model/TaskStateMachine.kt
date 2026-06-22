package com.example.petapp.domain.model

object TaskStateMachine {

    sealed class Transition {
        object Allowed : Transition()
        data class Forbidden(val reason: String) : Transition()
    }

    fun validate(from: TaskState, to: TaskState): Transition {
        // Сброс в Idle разрешён из любого состояния
        if (to is TaskState.Idle) return Transition.Allowed

        return when (from) {
            is TaskState.Idle -> when (to) {
                is TaskState.Analyzing -> Transition.Allowed
                else -> forbidden(from, to, "Начни с анализа задачи")
            }
            is TaskState.Analyzing -> when (to) {
                is TaskState.Planning,
                is TaskState.AwaitingInput, // plan ready after parallel Planner+Critic run
                is TaskState.Idle,          // запрос оказался простым
                is TaskState.Error -> Transition.Allowed
                else -> forbidden(from, to, "После анализа нужен план")
            }
            is TaskState.Planning -> when (to) {
                is TaskState.AwaitingInput,
                is TaskState.Error -> Transition.Allowed
                is TaskState.Execution -> Transition.Forbidden(
                    "Нельзя перейти к выполнению без одобрения плана"
                )
                is TaskState.Done -> Transition.Forbidden(
                    "Нельзя завершить задачу без выполнения и валидации"
                )
                else -> forbidden(from, to)
            }
            is TaskState.AwaitingInput -> when (to) {
                is TaskState.Execution,
                is TaskState.Replanning,
                is TaskState.Error -> Transition.Allowed
                is TaskState.Validation -> Transition.Forbidden(
                    "Нельзя перейти к валидации без выполнения"
                )
                is TaskState.Done -> Transition.Forbidden(
                    "Нельзя завершить задачу без выполнения и валидации"
                )
                else -> forbidden(from, to)
            }
            is TaskState.Execution -> when (to) {
                is TaskState.Validation,
                is TaskState.Error -> Transition.Allowed
                is TaskState.Done -> Transition.Forbidden(
                    "Нельзя завершить задачу без валидации"
                )
                else -> forbidden(from, to)
            }
            is TaskState.Validation -> when (to) {
                is TaskState.Done,
                is TaskState.ValidationFailed,
                is TaskState.Error -> Transition.Allowed
                else -> forbidden(from, to)
            }
            is TaskState.ValidationFailed -> when (to) {
                is TaskState.AwaitingInput,  // повторная попытка с тем же планом
                is TaskState.Replanning,     // переработать план
                is TaskState.Error -> Transition.Allowed
                else -> forbidden(from, to)
            }
            is TaskState.Done -> when (to) {
                is TaskState.Analyzing -> Transition.Allowed  // новая задача после завершения
                else -> forbidden(from, to)
            }
            is TaskState.Replanning -> when (to) {
                is TaskState.AwaitingInput,
                is TaskState.Error -> Transition.Allowed
                else -> forbidden(from, to)
            }
            is TaskState.Error -> when (to) {
                is TaskState.Analyzing -> Transition.Allowed  // новая задача после ошибки
                else -> forbidden(from, to)
            }
        }
    }

    private fun forbidden(from: TaskState, to: TaskState, hint: String? = null): Transition.Forbidden {
        val base = "Переход ${from::class.simpleName} → ${to::class.simpleName} запрещён"
        return Transition.Forbidden(if (hint != null) "$base. $hint" else base)
    }
}
