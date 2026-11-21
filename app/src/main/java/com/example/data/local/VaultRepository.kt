package com.example.data.local

import android.content.Context
import com.example.ai.AssistantPersona
import com.example.ai.SpeechLocaleOption
import kotlinx.coroutines.flow.Flow

data class AssistantPreferences(
    val persona: AssistantPersona = AssistantPersona.JARVIS,
    val speechLocale: SpeechLocaleOption = SpeechLocaleOption.EN_US,
    val ttsEnabled: Boolean = true,
    val continuousLoopEnabled: Boolean = false,
    val autonomousModeEnabled: Boolean = true,
    val splitScreenLaunchEnabled: Boolean = false,
    val hasCompletedOnboardingPrompt: Boolean = false
)

class VaultRepository(context: Context) {
    private val db = AetherDatabase.getInstance(context)
    private val commandLogDao = db.commandLogDao()
    private val memoryNoteDao = db.memoryNoteDao()
    private val voiceRoutineDao = db.voiceRoutineDao()
    private val quickContactDao = db.quickContactDao()

    private val prefs = context.applicationContext.getSharedPreferences(
        "aether_3d_prefs",
        Context.MODE_PRIVATE
    )

    val recentLogs: Flow<List<CommandLogEntity>> = commandLogDao.observeRecentLogs()
    val allMemories: Flow<List<MemoryNoteEntity>> = memoryNoteDao.observeAllMemories()
    val allRoutines: Flow<List<VoiceRoutineEntity>> = voiceRoutineDao.observeAllRoutines()
    val allContacts: Flow<List<QuickContactEntity>> = quickContactDao.observeAllContacts()

    suspend fun insertLog(log: CommandLogEntity): Long = commandLogDao.insertLog(log)
    suspend fun deleteLog(id: Long) = commandLogDao.deleteLogById(id)
    suspend fun clearLogs() = commandLogDao.clearAllLogs()

    suspend fun getTopMemories(): List<MemoryNoteEntity> = memoryNoteDao.getTopMemoriesSnapshot()
    suspend fun saveMemory(content: String, category: String = "FACT"): Long =
        memoryNoteDao.insertMemory(MemoryNoteEntity(content = content, category = category))

    suspend fun toggleMemoryPinned(id: Long, pinned: Boolean) = memoryNoteDao.setPinned(id, pinned)
    suspend fun deleteMemory(id: Long) = memoryNoteDao.deleteMemoryById(id)

    suspend fun getRoutinesSnapshot(): List<VoiceRoutineEntity> = voiceRoutineDao.getAllRoutinesSnapshot()
    suspend fun saveRoutine(triggerPhrase: String, description: String, instructionPrompt: String): Long =
        voiceRoutineDao.insertRoutine(
            VoiceRoutineEntity(
                triggerPhrase = triggerPhrase,
                description = description,
                instructionPrompt = instructionPrompt
            )
        )

    suspend fun recordRoutineUsed(id: Long) = voiceRoutineDao.incrementUsage(id)
    suspend fun deleteRoutine(id: Long) = voiceRoutineDao.deleteRoutineById(id)

    suspend fun getContactsSnapshot(): List<QuickContactEntity> = quickContactDao.getAllContactsSnapshot()
    suspend fun saveQuickContact(contact: QuickContactEntity): Long = quickContactDao.insertContact(contact)
    suspend fun deleteQuickContact(id: Long) = quickContactDao.deleteContactById(id)

    fun loadPreferences(): AssistantPreferences {
        val personaName = prefs.getString("persona", AssistantPersona.JARVIS.name)
        val persona = AssistantPersona.entries.firstOrNull { it.name == personaName }
            ?: AssistantPersona.JARVIS

        val localeName = prefs.getString("locale", SpeechLocaleOption.EN_US.name)
        val locale = SpeechLocaleOption.entries.firstOrNull { it.name == localeName }
            ?: SpeechLocaleOption.EN_US

        return AssistantPreferences(
            persona = persona,
            speechLocale = locale,
            ttsEnabled = prefs.getBoolean("tts_enabled", true),
            continuousLoopEnabled = prefs.getBoolean("continuous_loop", false),
            autonomousModeEnabled = prefs.getBoolean("autonomous_mode", true),
            splitScreenLaunchEnabled = prefs.getBoolean("split_screen", false),
            hasCompletedOnboardingPrompt = prefs.getBoolean("onboarding_done", false)
        )
    }

    fun savePreferences(updated: AssistantPreferences) {
        prefs.edit()
            .putString("persona", updated.persona.name)
            .putString("locale", updated.speechLocale.name)
            .putBoolean("tts_enabled", updated.ttsEnabled)
            .putBoolean("continuous_loop", updated.continuousLoopEnabled)
            .putBoolean("autonomous_mode", updated.autonomousModeEnabled)
            .putBoolean("split_screen", updated.splitScreenLaunchEnabled)
            .putBoolean("onboarding_done", updated.hasCompletedOnboardingPrompt)
            .apply()
    }
}
