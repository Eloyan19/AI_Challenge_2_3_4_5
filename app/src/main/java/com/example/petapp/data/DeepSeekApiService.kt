package com.example.petapp.data

import retrofit2.http.Body
import retrofit2.http.POST

interface DeepSeekApiService {
    @POST("chat/completions")
    suspend fun getChatCompletion(@Body request: ChatRequest): ChatResponse
}