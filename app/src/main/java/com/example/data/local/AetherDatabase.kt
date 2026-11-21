package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CommandLogEntity::class,
        MemoryNoteEntity::class,
        VoiceRoutineEntity::class,
        QuickContactEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AetherDatabase : RoomDatabase() {
    abstract fun commandLogDao(): CommandLogDao
    abstract fun memoryNoteDao(): MemoryNoteDao
    abstract fun voiceRoutineDao(): VoiceRoutineDao
    abstract fun quickContactDao(): QuickContactDao

    companion object {
        @Volatile
        private var INSTANCE: AetherDatabase? = null

        fun getInstance(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AetherDatabase {
            return Room.databaseBuilder(
                appContext,
                AetherDatabase::class.java,
                "aether_3d_vault.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val instance = getInstance(appContext)
                            val routineDao = instance.voiceRoutineDao()
                            routineDao.insertRoutine(
                                VoiceRoutineEntity(
                                    triggerPhrase = "Morning Briefing",
                                    description = "Weather check, battery telemetry & 7:30 AM reminder",
                                    instructionPrompt = "Check local weather, report my battery status, and summarize my pinned memories."
                                )
                            )
                            routineDao.insertRoutine(
                                VoiceRoutineEntity(
                                    triggerPhrase = "Study Focus",
                                    description = "Set 25-minute Pomodoro timer & lower media volume",
                                    instructionPrompt = "Start a 25 minute timer labeled Deep Focus and set media volume to 25 percent."
                                )
                            )
                            routineDao.insertRoutine(
                                VoiceRoutineEntity(
                                    triggerPhrase = "Night Shield",
                                    description = "Dim brightness, mute media & set 7 AM alarm",
                                    instructionPrompt = "Turn off flashlight, set brightness to 20 percent, and set an alarm for 7:00 AM."
                                )
                            )
                        }
                    }
                })
                .build()
        }
    }
}
