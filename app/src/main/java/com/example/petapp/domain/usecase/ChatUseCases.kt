package com.example.petapp.domain.usecase

import javax.inject.Inject

/**
 * Aggregates all repository-backed use cases into a single injectable holder.
 *
 * Dagger constructs each use case individually (they all have @Inject constructors) and
 * groups them here so MainViewModel receives one dependency instead of 20+.
 */
class ChatUseCases @Inject constructor(
    val loadHistory:          LoadHistoryUseCase,
    val saveTurn:             SaveTurnUseCase,
    val clearHistory:         ClearHistoryUseCase,
    val getSummary:           GetSummaryUseCase,
    val saveSummary:          SaveSummaryUseCase,
    val clearSummary:         ClearSummaryUseCase,
    val getFacts:             GetFactsUseCase,
    val saveFacts:            SaveFactsUseCase,
    val clearFacts:           ClearFactsUseCase,
    val getBranches:          GetBranchesUseCase,
    val createBranch:         CreateBranchUseCase,
    val getWorkingMemory:     GetWorkingMemoryUseCase,
    val saveWorkingMemory:    SaveWorkingMemoryUseCase,
    val clearWorkingMemory:   ClearWorkingMemoryUseCase,
    val getLongTermMemory:    GetLongTermMemoryUseCase,
    val addLongTermMemory:    AddLongTermMemoryUseCase,
    val deleteLongTermMemory: DeleteLongTermMemoryUseCase,
    val getProfiles:          GetProfilesUseCase,
    val saveProfile:          SaveProfileUseCase,
    val deleteProfile:        DeleteProfileUseCase,
)
