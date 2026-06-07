package com.example.petapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.ApiClient
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val apiService = ApiClient.service

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val content: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun sendRequest(
        prompt: String,
        maxTokens: Int?,
        stop: List<String>?,
        temperature: Double?
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val request = ChatRequest(
                    model = "deepseek-v4-flash",
                    messages = listOf(Message("user", prompt)),
                    maxTokens = maxTokens,
                    stop = stop,
                    temperature = temperature
                )
                val response = apiService.getChatCompletion(request)
                val content = response.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content
                    ?: "Ответ пуст"
                Log.v("Response", content)
                _uiState.value = UiState.Success(content)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}