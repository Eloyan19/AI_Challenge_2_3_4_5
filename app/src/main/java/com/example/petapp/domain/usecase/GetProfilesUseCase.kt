package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.UserProfile
import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

class GetProfilesUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(): List<UserProfile> = repository.getProfiles()
}
