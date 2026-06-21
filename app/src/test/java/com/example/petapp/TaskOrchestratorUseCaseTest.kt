package com.example.petapp

import com.example.petapp.domain.model.AgentRole
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.SwarmAgentOutput
import com.example.petapp.domain.usecase.DetectComplexityUseCase
import com.example.petapp.domain.usecase.RunAgentSwarmUseCase
import com.example.petapp.domain.usecase.TaskOrchestratorUseCase
import com.example.petapp.domain.usecase.TaskOrchestratorUseCase.OrchestratorResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TaskOrchestratorUseCaseTest {

    private lateinit var detectComplexity: DetectComplexityUseCase
    private lateinit var runSwarm: RunAgentSwarmUseCase
    private lateinit var orchestrator: TaskOrchestratorUseCase

    @Before
    fun setUp() {
        detectComplexity = mockk()
        runSwarm = mockk()
        orchestrator = TaskOrchestratorUseCase(detectComplexity, runSwarm, mockk(relaxed = true))
    }

    // ── Swarm result builders ──────────────────────────────────────────────

    private fun ok(role: AgentRole, content: String): Pair<AgentRole, Result<SwarmAgentOutput>> =
        role to Result.success(SwarmAgentOutput(role = role, content = content, durationMs = 0))

    private fun err(role: AgentRole): Pair<AgentRole, Result<SwarmAgentOutput>> =
        role to Result.failure(RuntimeException("swarm error"))

    private fun swarmOf(vararg entries: Pair<AgentRole, Result<SwarmAgentOutput>>) = entries.toMap()

    private val noHistory: List<Message> = emptyList()

    // ── detectAndPlan ──────────────────────────────────────────────────────

    @Test
    fun `detectAndPlan returns Simple when request is not complex`() = runTest {
        coEvery { detectComplexity(any()) } returns false

        val result = orchestrator.detectAndPlan("привет", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.Simple)
        coVerify(exactly = 0) { runSwarm(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `detectAndPlan returns PlanReady with plan and critique when both agents succeed`() = runTest {
        coEvery { detectComplexity(any()) } returns true
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.PLANNER, "1. Шаг один\n2. Шаг два"), ok(AgentRole.CRITIC, "Риски отсутствуют"))

        val result = orchestrator.detectAndPlan("напиши код", noHistory, null, "m")

        val planReady = result as OrchestratorResult.PlanReady
        assertEquals("1. Шаг один\n2. Шаг два", planReady.plan)
        assertEquals("Риски отсутствуют", planReady.critique)
    }

    @Test
    fun `detectAndPlan returns PlanReady with null critique when critic fails`() = runTest {
        coEvery { detectComplexity(any()) } returns true
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.PLANNER, "1. Шаг"), err(AgentRole.CRITIC))

        val result = orchestrator.detectAndPlan("напиши код", noHistory, null, "m")

        val planReady = result as OrchestratorResult.PlanReady
        assertEquals("1. Шаг", planReady.plan)
        assertNull(planReady.critique)
    }

    @Test
    fun `detectAndPlan returns Failed when planner fails`() = runTest {
        coEvery { detectComplexity(any()) } returns true
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(err(AgentRole.PLANNER))

        val result = orchestrator.detectAndPlan("напиши код", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.Failed)
    }

    @Test
    fun `detectAndPlan invokes onPlanning callback when request is complex`() = runTest {
        coEvery { detectComplexity(any()) } returns true
        coEvery { runSwarm(any(), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.PLANNER, "план"), ok(AgentRole.CRITIC, "критика"))

        var planningCalled = false
        orchestrator.detectAndPlan("напиши код", noHistory, null, "m", onPlanning = { planningCalled = true })

        assertTrue(planningCalled)
    }

    @Test
    fun `detectAndPlan does not invoke onPlanning when request is simple`() = runTest {
        coEvery { detectComplexity(any()) } returns false

        var planningCalled = false
        orchestrator.detectAndPlan("привет", noHistory, null, "m", onPlanning = { planningCalled = true })

        assertFalse(planningCalled)
    }

    // ── executeAndValidate ─────────────────────────────────────────────────

    @Test
    fun `executeAndValidate returns ExecutionDone with judge answer on full success`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "Результат выполнения"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "PASS\nВсё корректно"))
        coEvery { runSwarm(eq(listOf(AgentRole.JUDGE)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.JUDGE, "Финальный ответ пользователю"))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        val done = result as OrchestratorResult.ExecutionDone
        assertEquals("Финальный ответ пользователю", done.finalAnswer)
    }

    @Test
    fun `executeAndValidate accepts lowercase pass from validator`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "результат"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "pass всё хорошо"))
        coEvery { runSwarm(eq(listOf(AgentRole.JUDGE)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.JUDGE, "ответ"))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.ExecutionDone)
    }

    @Test
    fun `executeAndValidate falls back to executor content when judge fails`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "executor output"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "PASS"))
        coEvery { runSwarm(eq(listOf(AgentRole.JUDGE)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(err(AgentRole.JUDGE))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        val done = result as OrchestratorResult.ExecutionDone
        assertEquals("executor output", done.finalAnswer)
    }

    @Test
    fun `executeAndValidate returns ValidationFailed with reason from second line on FAIL`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "executor output"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "FAIL\nРезультат не соответствует цели"))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        val failed = result as OrchestratorResult.ValidationFailed
        assertEquals("Результат не соответствует цели", failed.reason)
        assertEquals("executor output", failed.executionResult)
    }

    @Test
    fun `executeAndValidate extracts reason from single-line FAIL response`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "executor output"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "FAIL Пропущен шаг 2"))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        val failed = result as OrchestratorResult.ValidationFailed
        assertEquals("Пропущен шаг 2", failed.reason)
    }

    @Test
    fun `executeAndValidate uses FAIL as reason when no explanation provided`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "executor output"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "FAIL"))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        val failed = result as OrchestratorResult.ValidationFailed
        assertEquals("FAIL", failed.reason)
    }

    @Test
    fun `executeAndValidate returns Failed when executor fails`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(err(AgentRole.EXECUTOR))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.Failed)
        // Validator and Judge must not be called
        coVerify(exactly = 0) { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { runSwarm(eq(listOf(AgentRole.JUDGE)), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `executeAndValidate returns Failed when validator fails`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "результат"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(err(AgentRole.VALIDATOR))

        val result = orchestrator.executeAndValidate("задача", "план", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.Failed)
    }

    @Test
    fun `executeAndValidate invokes onValidating with executor result`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.EXECUTOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.EXECUTOR, "executor output"))
        coEvery { runSwarm(eq(listOf(AgentRole.VALIDATOR)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.VALIDATOR, "PASS"))
        coEvery { runSwarm(eq(listOf(AgentRole.JUDGE)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.JUDGE, "ответ"))

        var validatingArg: String? = null
        orchestrator.executeAndValidate("задача", "план", noHistory, null, "m",
            onValidating = { validatingArg = it }
        )

        assertEquals("executor output", validatingArg)
    }

    // ── replan ─────────────────────────────────────────────────────────────

    @Test
    fun `replan returns PlanReady with plan and critique when both agents succeed`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.PLANNER, "улучшенный план"), ok(AgentRole.CRITIC, "теперь лучше"))

        val result = orchestrator.replan("задача", "старый план", "слишком общий", noHistory, null, "m")

        val planReady = result as OrchestratorResult.PlanReady
        assertEquals("улучшенный план", planReady.plan)
        assertEquals("теперь лучше", planReady.critique)
    }

    @Test
    fun `replan returns PlanReady with null critique when critic fails`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(ok(AgentRole.PLANNER, "улучшенный план"), err(AgentRole.CRITIC))

        val result = orchestrator.replan("задача", "старый план", "причина", noHistory, null, "m")

        val planReady = result as OrchestratorResult.PlanReady
        assertEquals("улучшенный план", planReady.plan)
        assertNull(planReady.critique)
    }

    @Test
    fun `replan returns Failed when planner fails`() = runTest {
        coEvery { runSwarm(eq(listOf(AgentRole.PLANNER, AgentRole.CRITIC)), any(), any(), any(), any(), any(), any()) } returns
            swarmOf(err(AgentRole.PLANNER))

        val result = orchestrator.replan("задача", "старый план", "причина", noHistory, null, "m")

        assertTrue(result is OrchestratorResult.Failed)
    }
}
