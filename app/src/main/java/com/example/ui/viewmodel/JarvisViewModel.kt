package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.CoreMode
import com.example.ai.SpeechLocaleOption
import com.example.core.AgentOrchestrator
import com.example.core.AssistantUiState
import com.example.data.local.CommandLogEntity
import com.example.data.local.MemoryNoteEntity
import com.example.data.local.QuickContactEntity
import com.example.data.local.VoiceRoutineEntity
import com.example.service.VoiceAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val orchestrator = AgentOrchestrator.getInstance(application)
    private val vault = orchestrator.vaultRepository

    val uiState: StateFlow<AssistantUiState> = orchestrator.uiState
    val audioAmplitude: StateFlow<Float> = orchestrator.audioAmplitude
    val permissionsManager = orchestrator.permissionsManager

    val historyLogs: StateFlow<List<CommandLogEntity>> = vault.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryNoteEntity>> = vault.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<VoiceRoutineEntity>> = vault.allRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickContacts: StateFlow<List<QuickContactEntity>> = vault.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshPermissionsAndTelemetry() {
        orchestrator.refreshPermissionsAndTelemetry()
    }

    fun onMicTapped() {
        val currentMode = uiState.value.coreMode
        if (currentMode == CoreMode.LISTENING) {
            orchestrator.stopListening()
        } else {
            orchestrator.startListening()
        }
    }

    fun submitTextCommand(text: String) {
        orchestrator.submitCommand(prompt = text, sourceMode = "FOREGROUND")
    }

    fun submitImageCommand(imageUri: Uri, prompt: String) {
        viewModelScope.launch {
            val bitmap = decodeDownscaledBitmap(imageUri)
            if (bitmap != null) {
                orchestrator.submitCommand(
                    prompt = prompt.ifBlank { "Describe this image and extract any actionable info." },
                    attachedImage = bitmap,
                    sourceMode = "FOREGROUND"
                )
            }
        }
    }

    fun cyclePersona() = orchestrator.cyclePersona()

    fun setSpeechLocale(option: SpeechLocaleOption) {
        orchestrator.updatePreferences { it.copy(speechLocale = option) }
    }

    fun toggleTts() {
        val next = !uiState.value.preferences.ttsEnabled
        if (!next) orchestrator.voiceManager.stopSpeaking()
        orchestrator.updatePreferences { it.copy(ttsEnabled = next) }
    }

    fun toggleContinuousLoop() {
        orchestrator.updatePreferences { it.copy(continuousLoopEnabled = !it.continuousLoopEnabled) }
    }

    fun toggleAutonomousMode() {
        orchestrator.updatePreferences { it.copy(autonomousModeEnabled = !it.autonomousModeEnabled) }
    }

    fun toggleSplitScreenLaunch() {
        orchestrator.updatePreferences { it.copy(splitScreenLaunchEnabled = !it.splitScreenLaunchEnabled) }
    }

    fun markOnboardingSeen() {
        orchestrator.updatePreferences { it.copy(hasCompletedOnboardingPrompt = true) }
    }

    fun toggleBackgroundService() {
        val app = getApplication<Application>()
        if (uiState.value.backgroundServiceActive) {
            VoiceAgentService.stop(app)
        } else {
            VoiceAgentService.start(app)
        }
    }

    fun abortAllTasks() = orchestrator.abortAllTasks()

    fun confirmSensitiveSteps() = orchestrator.confirmPendingSensitiveSteps()

    fun dismissSensitiveSteps() = orchestrator.dismissPendingConfirmation()

    // SQLite Vault CRUD
    fun addMemory(content: String, category: String = "FACT") {
        if (content.isBlank()) return
        viewModelScope.launch { vault.saveMemory(content.trim(), category) }
    }

    fun toggleMemoryPinned(note: MemoryNoteEntity) {
        viewModelScope.launch { vault.toggleMemoryPinned(note.id, !note.isPinned) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { vault.deleteMemory(id) }
    }

    fun saveRoutine(trigger: String, desc: String, prompt: String) {
        if (trigger.isBlank() || prompt.isBlank()) return
        viewModelScope.launch { vault.saveRoutine(trigger.trim(), desc.trim(), prompt.trim()) }
    }

    fun runRoutine(routine: VoiceRoutineEntity) {
        orchestrator.submitCommand(prompt = routine.triggerPhrase, sourceMode = "FOREGROUND")
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { vault.deleteRoutine(id) }
    }

    fun addQuickContact(alias: String, displayName: String, phone: String, email: String) {
        if (alias.isBlank()) return
        viewModelScope.launch {
            vault.saveQuickContact(
                QuickContactEntity(
                    aliasName = alias.trim(),
                    displayName = displayName.trim().ifBlank { alias.trim() },
                    phoneNumber = phone.trim(),
                    emailAddress = email.trim()
                )
            )
        }
    }

    fun importPickedContact(contactUri: Uri) {
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                var name = ""
                var phone = ""
                resolver.query(contactUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty()
                        val contactId = if (idIdx >= 0) cursor.getString(idIdx) else null
                        val hasPhone = if (hasPhoneIdx >= 0) cursor.getInt(hasPhoneIdx) > 0 else false
                        if (hasPhone && contactId != null) {
                            resolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )?.use { pCursor ->
                                if (pCursor.moveToFirst()) {
                                    phone = pCursor.getString(0).orEmpty()
                                }
                            }
                        }
                    }
                }
                if (name.isNotBlank()) QuickContactEntity(
                    aliasName = name.substringBefore(" "),
                    displayName = name,
                    phoneNumber = phone
                ) else null
            }
            if (resolved != null) {
                vault.saveQuickContact(resolved)
            }
        }
    }

    fun deleteQuickContact(id: Long) {
        viewModelScope.launch { vault.deleteQuickContact(id) }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { vault.deleteLog(id) }
    }

    fun clearLogs() {
        viewModelScope.launch { vault.clearLogs() }
    }

    private suspend fun decodeDownscaledBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val raw = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            if (raw.width > 1024) {
                val targetH = (raw.height * (1024f / raw.width)).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(raw, 1024, targetH, true)
                if (scaled !== raw) raw.recycle()
                scaled
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        }
    }
}
