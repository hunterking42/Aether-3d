package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.accessibility.AetherAccessibilityService
import com.example.ai.ActionType
import com.example.ai.ActiveTaskItem
import com.example.ai.CoreMode
import com.example.ai.GeminiCommandService
import com.example.ai.PlannedStep
import com.example.ai.TaskStatus
import com.example.data.local.AssistantPreferences
import com.example.data.local.CommandLogEntity
import com.example.data.local.VaultRepository
import com.example.system.AndroidActionRouter
import com.example.system.DeviceTelemetry
import com.example.system.DeviceTelemetryProvider
import com.example.system.PermissionSnapshot
import com.example.system.PermissionsManager
import com.example.system.VoiceAndTtsManager
import com.example.tools.FreeWebToolsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AgentOrchestrator"

data class AssistantUiState(
    val coreMode: CoreMode = CoreMode.IDLE,
    val preferences: AssistantPreferences = AssistantPreferences(),
    val permissionSnapshot: PermissionSnapshot = PermissionSnapshot(),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val partialTranscript: String = "",
    val lastUserPrompt: String = "",
    val latestReply: String = "Aether 3D core online. Tap the sphere or type a command below.",
    val activeTasks: List<ActiveTaskItem> = emptyList(),
    val pendingConfirmationSteps: List<PlannedStep> = emptyList(),
    val pendingConfirmationReply: String = "",
    val torchEnabled: Boolean = false,
    val backgroundServiceActive: Boolean = false,
    val backgroundServicePaused: Boolean = false,
    val errorMessage: String? = null
)

class AgentOrchestrator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val vaultRepository = VaultRepository(appContext)
    val permissionsManager = PermissionsManager(appContext)
    val telemetryProvider = DeviceTelemetryProvider(appContext)
    private val webTools = FreeWebToolsRepository()
    private val geminiService = GeminiCommandService()
    private val actionRouter = AndroidActionRouter(
        context = appContext,
        telemetryProvider = telemetryProvider,
        vaultRepository = vaultRepository,
        webTools = webTools
    )

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _uiState = MutableStateFlow(
        AssistantUiState(preferences = vaultRepository.loadPreferences())
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var activeExecutionJob: Job? = null

    val voiceManager = VoiceAndTtsManager(
        context = appContext,
        onAmplitudeChanged = { amp -> _audioAmplitude.value = amp },
        onPartialSpeech = { partial ->
            _uiState.update { it.copy(partialTranscript = partial) }
        },
        onFinalSpeech = { text ->
            _uiState.update { it.copy(partialTranscript = text) }
            submitCommand(
                prompt = text,
                sourceMode = if (_uiState.value.backgroundServiceActive) "BACKGROUND" else "FOREGROUND"
            )
        },
        onSpeechError = { errMsg ->
            _uiState.update {
                it.copy(
                    coreMode = CoreMode.ERROR,
                    errorMessage = "$errMsg You can also type commands in the bar below.",
                    partialTranscript = ""
                )
            }
            scope.launch {
                delay(4_000L)
                _uiState.update {
                    if (it.coreMode == CoreMode.ERROR) it.copy(coreMode = CoreMode.IDLE) else it
                }
            }
        },
        onSpeakingStateChanged = { speaking ->
            _uiState.update { state ->
                if (speaking) {
                    state.copy(coreMode = CoreMode.SPEAKING)
                } else if (state.coreMode == CoreMode.SPEAKING) {
                    state.copy(coreMode = CoreMode.IDLE)
                } else {
                    state
                }
            }
        },
        onSpeakingFinished = {
            val prefs = _uiState.value.preferences
            if (prefs.continuousLoopEnabled && _uiState.value.permissionSnapshot.micGranted) {
                scope.launch {
                    delay(450L)
                    if (_uiState.value.coreMode == CoreMode.IDLE) {
                        startListening()
                    }
                }
            }
        }
    )

    init {
        refreshPermissionsAndTelemetry()
        scope.launch(Dispatchers.IO) {
            runCatching { telemetryProvider.queryLauncherAppMap() }
        }
    }

    fun refreshPermissionsAndTelemetry() {
        scope.launch {
            val (perms, telemetry) = withContext(Dispatchers.IO) {
                permissionsManager.checkSnapshot() to telemetryProvider.readTelemetry()
            }
            _uiState.update {
                it.copy(
                    permissionSnapshot = perms,
                    telemetry = telemetry
                )
            }
        }
    }

    fun updatePreferences(transform: (AssistantPreferences) -> AssistantPreferences) {
        val updated = transform(_uiState.value.preferences)
        vaultRepository.savePreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun cyclePersona() {
        val entries = com.example.ai.AssistantPersona.entries
        val current = _uiState.value.preferences.persona
        val next = entries[(entries.indexOf(current) + 1) % entries.size]
        updatePreferences { it.copy(persona = next) }
    }

    fun setBackgroundServiceState(active: Boolean, paused: Boolean = false) {
        _uiState.update {
            it.copy(backgroundServiceActive = active, backgroundServicePaused = paused)
        }
    }

    fun startListening() {
        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!micGranted) {
            _uiState.update {
                it.copy(
                    coreMode = CoreMode.ERROR,
                    errorMessage = "Microphone permission is required for voice. Grant Mic or type your command below."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                coreMode = CoreMode.LISTENING,
                partialTranscript = "",
                errorMessage = null
            )
        }
        voiceManager.startListening(_uiState.value.preferences.speechLocale)
    }

    fun stopListening() {
        voiceManager.stopListening()
        _uiState.update {
            if (it.coreMode == CoreMode.LISTENING) it.copy(coreMode = CoreMode.IDLE) else it
        }
    }

    fun abortAllTasks() {
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        _uiState.update {
            it.copy(
                coreMode = CoreMode.IDLE,
                activeTasks = it.activeTasks.map { item ->
                    if (item.status == TaskStatus.RUNNING || item.status == TaskStatus.QUEUED) {
                        item.copy(status = TaskStatus.FAILED, detail = "Aborted by user")
                    } else item
                },
                pendingConfirmationSteps = emptyList(),
                pendingConfirmationReply = ""
            )
        }
    }

    fun confirmPendingSensitiveSteps() {
        val pending = _uiState.value.pendingConfirmationSteps
        val reply = _uiState.value.pendingConfirmationReply
        if (pending.isEmpty()) return
        _uiState.update {
            it.copy(pendingConfirmationSteps = emptyList(), pendingConfirmationReply = "")
        }
        activeExecutionJob = scope.launch {
            executeStepsPipeline(
                userPrompt = _uiState.value.lastUserPrompt,
                initialReply = reply,
                steps = pending,
                bypassConfirmation = true,
                sourceMode = "FOREGROUND",
                hadScreenVision = false
            )
        }
    }

    fun dismissPendingConfirmation() {
        _uiState.update {
            it.copy(
                pendingConfirmationSteps = emptyList(),
                pendingConfirmationReply = "",
                coreMode = CoreMode.IDLE
            )
        }
    }

    fun submitCommand(
        prompt: String,
        attachedImage: Bitmap? = null,
        sourceMode: String = "FOREGROUND"
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() && attachedImage == null) return
        val effectivePrompt = cleanPrompt.ifBlank { "Analyze this image and tell me what action to take." }

        activeExecutionJob?.cancel()
        voiceManager.stopSpeaking()

        _uiState.update {
            it.copy(
                coreMode = CoreMode.THINKING,
                lastUserPrompt = effectivePrompt,
                partialTranscript = "",
                errorMessage = null,
                activeTasks = emptyList()
            )
        }

        activeExecutionJob = scope.launch {
            try {
                val prefs = _uiState.value.preferences

                // Load SQLite & telemetry context in parallel on Dispatchers.IO for zero lag
                val contextBundle = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val routinesDeferred = async { vaultRepository.getRoutinesSnapshot() }
                        val memoriesDeferred = async { vaultRepository.getTopMemories() }
                        val contactsDeferred = async { vaultRepository.getContactsSnapshot() }
                        val appsDeferred = async { telemetryProvider.queryLauncherAppNames(30) }
                        val permsDeferred = async { permissionsManager.checkSnapshot() }
                        val telemetryDeferred = async { telemetryProvider.readTelemetry() }

                        val routines = routinesDeferred.await()
                        val matchedRoutine = routines.firstOrNull { routine ->
                            effectivePrompt.contains(routine.triggerPhrase, ignoreCase = true)
                        }
                        val resolved = if (matchedRoutine != null) {
                            vaultRepository.recordRoutineUsed(matchedRoutine.id)
                            "${matchedRoutine.instructionPrompt} (Triggered routine: ${matchedRoutine.triggerPhrase})"
                        } else {
                            effectivePrompt
                        }
                        val telemetry = telemetryDeferred.await()

                        ContextSnapshotBundle(
                            resolvedPrompt = resolved,
                            memoriesText = memoriesDeferred.await().joinToString("\n") { "- [${it.category}] ${it.content}" },
                            routinesText = routines.joinToString("\n") { "- ${it.triggerPhrase}: ${it.instructionPrompt}" },
                            contactsText = contactsDeferred.await().joinToString("\n") {
                                "- ${it.aliasName} (${it.displayName}): phone=${it.phoneNumber}, email=${it.emailAddress}"
                            },
                            appsText = appsDeferred.await(),
                            telemetryText = telemetryProvider.buildPromptTelemetrySummary(telemetry),
                            perms = permsDeferred.await(),
                            telemetry = telemetry
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        permissionSnapshot = contextBundle.perms,
                        telemetry = contextBundle.telemetry
                    )
                }

                val accessibility = AetherAccessibilityService.currentInstance
                val wantsScreen = attachedImage == null && accessibility != null &&
                    shouldInspectScreenProactively(contextBundle.resolvedPrompt)

                var screenSummary: String? = null
                var screenBitmap: Bitmap? = attachedImage
                var usedScreenVision = attachedImage != null

                if (wantsScreen && accessibility != null) {
                    _uiState.update {
                        it.copy(
                            coreMode = CoreMode.OBSERVING_SCREEN,
                            lastUserPrompt = effectivePrompt,
                            errorMessage = null
                        )
                    }
                    val snap = accessibility.captureFullScreenContext(includeBitmap = true)
                    screenSummary = snap?.uiSummary
                    screenBitmap = snap?.screenshot
                    usedScreenVision = snap != null
                }

                _uiState.update {
                    it.copy(
                        coreMode = CoreMode.THINKING,
                        lastUserPrompt = effectivePrompt,
                        partialTranscript = "",
                        errorMessage = null
                    )
                }

                var planResult = geminiService.planCommand(
                    userPrompt = contextBundle.resolvedPrompt,
                    persona = prefs.persona,
                    deviceContextSummary = contextBundle.telemetryText,
                    memoriesSummary = contextBundle.memoriesText,
                    routinesSummary = contextBundle.routinesText,
                    contactsSummary = contextBundle.contactsText,
                    installedAppsSummary = contextBundle.appsText,
                    screenUiSummary = screenSummary,
                    screenBitmap = screenBitmap
                )

                var plan = planResult.getOrElse { err ->
                    if (screenBitmap !== attachedImage) screenBitmap?.recycle()
                    _uiState.update {
                        it.copy(
                            coreMode = CoreMode.ERROR,
                            errorMessage = err.localizedMessage ?: "Gemini planning failed."
                        )
                    }
                    return@launch
                }

                if (plan.needsScreenObservation && !usedScreenVision && accessibility != null) {
                    _uiState.update { it.copy(coreMode = CoreMode.OBSERVING_SCREEN) }
                    val snap = accessibility.captureFullScreenContext(includeBitmap = true)
                    if (snap != null) {
                        usedScreenVision = true
                        _uiState.update { it.copy(coreMode = CoreMode.THINKING) }
                        planResult = geminiService.planCommand(
                            userPrompt = contextBundle.resolvedPrompt,
                            persona = prefs.persona,
                            deviceContextSummary = contextBundle.telemetryText,
                            memoriesSummary = contextBundle.memoriesText,
                            routinesSummary = contextBundle.routinesText,
                            contactsSummary = contextBundle.contactsText,
                            installedAppsSummary = contextBundle.appsText,
                            screenUiSummary = snap.uiSummary,
                            screenBitmap = snap.screenshot
                        )
                        snap.screenshot?.recycle()
                        plan = planResult.getOrElse { plan }
                    }
                } else if (screenBitmap !== attachedImage) {
                    screenBitmap?.recycle()
                }

                executeStepsPipeline(
                    userPrompt = effectivePrompt,
                    initialReply = plan.reply,
                    steps = plan.steps,
                    bypassConfirmation = false,
                    sourceMode = sourceMode,
                    hadScreenVision = usedScreenVision
                )

                // Optional autonomous follow-up step for multi-screen chained UI goals
                if (plan.continueLoopAfterAction && accessibility != null) {
                    delay(1_200L)
                    _uiState.update { it.copy(coreMode = CoreMode.OBSERVING_SCREEN) }
                    val nextSnap = accessibility.captureFullScreenContext(includeBitmap = false)
                    if (nextSnap != null) {
                        _uiState.update { it.copy(coreMode = CoreMode.THINKING) }
                        val followUp = geminiService.planCommand(
                            userPrompt = "Continue user goal: ${contextBundle.resolvedPrompt}. Previous steps completed.",
                            persona = prefs.persona,
                            deviceContextSummary = contextBundle.telemetryText,
                            memoriesSummary = contextBundle.memoriesText,
                            routinesSummary = contextBundle.routinesText,
                            contactsSummary = contextBundle.contactsText,
                            installedAppsSummary = contextBundle.appsText,
                            screenUiSummary = nextSnap.uiSummary,
                            screenBitmap = null
                        )
                        followUp.getOrNull()?.let { nextPlan ->
                            val actionableFollowUp = nextPlan.steps.filter {
                                it.actionType != ActionType.SPEAK_ONLY && it.actionType != ActionType.SCREEN_EXPLAIN
                            }
                            if (actionableFollowUp.isNotEmpty()) {
                                executeStepsPipeline(
                                    userPrompt = effectivePrompt,
                                    initialReply = nextPlan.reply,
                                    steps = actionableFollowUp,
                                    bypassConfirmation = false,
                                    sourceMode = sourceMode,
                                    hadScreenVision = true
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Orchestrator execution error", e)
                _uiState.update {
                    it.copy(
                        coreMode = CoreMode.ERROR,
                        errorMessage = e.localizedMessage ?: "Execution error"
                    )
                }
            }
        }
    }

    private suspend fun executeStepsPipeline(
        userPrompt: String,
        initialReply: String,
        steps: List<PlannedStep>,
        bypassConfirmation: Boolean,
        sourceMode: String,
        hadScreenVision: Boolean
    ) {
        val prefs = _uiState.value.preferences
        val guardedNeeded = !prefs.autonomousModeEnabled && !bypassConfirmation && steps.any { it.isSensitive }

        if (guardedNeeded) {
            _uiState.update {
                it.copy(
                    coreMode = CoreMode.IDLE,
                    latestReply = initialReply,
                    pendingConfirmationSteps = steps,
                    pendingConfirmationReply = initialReply,
                    activeTasks = steps.mapIndexed { idx, step ->
                        ActiveTaskItem(
                            id = "task_$idx",
                            step = step,
                            status = TaskStatus.NEEDS_CONFIRMATION,
                            detail = "Awaiting confirmation"
                        )
                    }
                )
            }
            if (prefs.ttsEnabled) {
                voiceManager.speak(
                    "Please confirm to proceed with ${steps.firstOrNull { it.isSensitive }?.label ?: "this action"}.",
                    prefs.persona,
                    prefs.speechLocale
                )
            }
            return
        }

        val initialTasks = steps.mapIndexed { idx, step ->
            ActiveTaskItem(id = "task_$idx", step = step, status = TaskStatus.QUEUED)
        }
        _uiState.update {
            it.copy(
                coreMode = CoreMode.MULTITASKING,
                activeTasks = initialTasks,
                latestReply = initialReply
            )
        }

        val parallelIndexed = initialTasks.filter { it.step.isBackgroundParallel }
        val sequentialIndexed = initialTasks.filter { !it.step.isBackgroundParallel }
        val toolOutputs = mutableListOf<String>()

        if (parallelIndexed.isNotEmpty()) {
            coroutineScope {
                parallelIndexed.map { task ->
                    async(Dispatchers.IO) {
                        updateTaskStatus(task.id, TaskStatus.RUNNING, "Running...")
                        val result = actionRouter.executeStep(task.step, prefs.splitScreenLaunchEnabled)
                        updateTaskStatus(
                            task.id,
                            if (result.success) TaskStatus.DONE else TaskStatus.FAILED,
                            result.detail
                        )
                        if (result.torchStateChanged != null) {
                            _uiState.update { it.copy(torchEnabled = result.torchStateChanged) }
                        }
                        result.toolOutputForSummary?.let { synchronized(toolOutputs) { toolOutputs.add(it) } }
                    }
                }.awaitAll()
            }
        }

        for ((idx, task) in sequentialIndexed.withIndex()) {
            if (task.step.actionType == ActionType.SPEAK_ONLY || task.step.actionType == ActionType.SCREEN_EXPLAIN) {
                updateTaskStatus(task.id, TaskStatus.DONE, "Prepared")
                continue
            }
            updateTaskStatus(task.id, TaskStatus.RUNNING, "Executing...")
            val result = withContext(Dispatchers.IO) {
                actionRouter.executeStep(task.step, prefs.splitScreenLaunchEnabled)
            }
            updateTaskStatus(
                task.id,
                if (result.success) TaskStatus.DONE else TaskStatus.FAILED,
                result.detail
            )
            if (result.torchStateChanged != null) {
                _uiState.update { it.copy(torchEnabled = result.torchStateChanged) }
            }
            result.toolOutputForSummary?.let { toolOutputs.add(it) }
            if (idx < sequentialIndexed.lastIndex) {
                val waitMs = when (task.step.actionType) {
                    ActionType.LAUNCH_APP, ActionType.OPEN_SETTINGS, ActionType.SEARCH_IN_APP -> 1_450L
                    else -> 400L
                }
                delay(waitMs)
            }
        }

        val finalReply = if (toolOutputs.isNotEmpty()) {
            if (toolOutputs.size == 1 && sequentialIndexed.none { it.step.actionType != ActionType.SPEAK_ONLY }) {
                toolOutputs.first()
            } else {
                geminiService.synthesizeToolSummary(userPrompt, prefs.persona, toolOutputs)
                    ?: toolOutputs.joinToString(" ")
            }
        } else {
            initialReply
        }

        val finalTasks = _uiState.value.activeTasks
        val allFailed = finalTasks.isNotEmpty() && finalTasks.all { it.status == TaskStatus.FAILED }
        val summaryStr = finalTasks.joinToString(" | ") { "${it.step.label}: ${it.status.name}" }

        withContext(Dispatchers.IO) {
            vaultRepository.insertLog(
                CommandLogEntity(
                    userPrompt = userPrompt,
                    assistantReply = finalReply,
                    personaUsed = prefs.persona.displayName,
                    sourceMode = sourceMode,
                    hadScreenVision = hadScreenVision,
                    tasksSummary = summaryStr,
                    executionStatus = if (allFailed) "FAILED" else "EXECUTED"
                )
            )
        }

        _uiState.update {
            it.copy(
                latestReply = finalReply,
                coreMode = if (prefs.ttsEnabled) CoreMode.SPEAKING else CoreMode.IDLE
            )
        }

        if (prefs.ttsEnabled) {
            voiceManager.speak(finalReply, prefs.persona, prefs.speechLocale)
        }
    }

    private fun updateTaskStatus(taskId: String, status: TaskStatus, detail: String) {
        _uiState.update { state ->
            state.copy(
                activeTasks = state.activeTasks.map {
                    if (it.id == taskId) it.copy(status = status, detail = detail) else it
                }
            )
        }
    }

    private fun shouldInspectScreenProactively(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val keywords = listOf(
            "screen", "this page", "this app", "what am i looking", "read this",
            "summarize this", "tap ", "click ", "scroll ", "type ", "fill ", "select "
        )
        return keywords.any { lower.contains(it) }
    }

    private data class ContextSnapshotBundle(
        val resolvedPrompt: String,
        val memoriesText: String,
        val routinesText: String,
        val contactsText: String,
        val appsText: String,
        val telemetryText: String,
        val perms: PermissionSnapshot,
        val telemetry: DeviceTelemetry
    )

    companion object {
        @Volatile
        private var INSTANCE: AgentOrchestrator? = null

        fun getInstance(context: Context): AgentOrchestrator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentOrchestrator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
