package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userPrompt: String,
    val assistantReply: String,
    val personaUsed: String,
    val sourceMode: String,
    val hadScreenVision: Boolean,
    val tasksSummary: String,
    val executionStatus: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_notes")
data class MemoryNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String = "FACT",
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_routines")
data class VoiceRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerPhrase: String,
    val description: String,
    val instructionPrompt: String,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quick_contacts")
data class QuickContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aliasName: String,
    val displayName: String,
    val phoneNumber: String = "",
    val emailAddress: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
