package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiCommandService"

class GeminiCommandService(
    private val nvidiaFallback: NvidiaFallbackService = NvidiaFallbackService()
) {

    private val stepSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = ActionType.entries.map { it.name },
                description = "Exact action identifier to execute on Android or via tools"
            ),
            "label" to Schema.string(description = "Short 2-5 word HUD label for this step"),
            "target" to Schema.string(
                description = "App name, contact name/number, city/URL/query, settings panel (wifi/bluetooth/display/sound/battery/location/general), scroll direction (up/down), global action (HOME/BACK/RECENTS/NOTIFICATIONS/QUICK_SETTINGS/LOCK_SCREEN/TAKE_SCREENSHOT), or UI button text"
            ),
            "textPayload" to Schema.string(
                description = "Message body, alarm label, email body/subject, memory text, routine prompt, currency target code, or text to type into an active UI field"
            ),
            "intValue" to Schema.integer(
                description = "Hour (0-23) for alarm, duration seconds for timer, volume/brightness percent (0-100), or normalized X coordinate (0-1000) for UI_TAP_COORDINATES"
            ),
            "secondaryIntValue" to Schema.integer(
                description = "Minute (0-59) for alarm, or normalized Y coordinate (0-1000) for UI_TAP_COORDINATES"
            ),
            "doubleValue" to Schema.double(
                description = "Numeric amount for CURRENCY_CONVERT"
            ),
            "boolValue" to Schema.boolean(
                description = "True for ON/ENABLE (torch on, auto-rotate on), false for OFF/DISABLE"
            ),
            "isBackgroundParallel" to Schema.boolean(
                description = "True if this step can run concurrently in the background (weather, wiki, currency, memory save, torch, volume, brightness, alarm, timer); false for foreground UI or app launches"
            ),
            "isSensitive" to Schema.boolean(
                description = "True for dialing calls, sending messages/emails, locking screen, or tapping irreversible UI buttons"
            )
        ),
        optionalProperties = listOf(
            "target",
            "textPayload",
            "intValue",
            "secondaryIntValue",
            "doubleValue",
            "boolValue",
            "isBackgroundParallel",
            "isSensitive"
        )
    )

    private val planSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Natural, concise spoken reply matching the active persona in English or Bangla (বাংলা) as used by the user"
            ),
            "needsScreenObservation" to Schema.boolean(
                description = "Set true only if the request explicitly requires inspecting or interacting with the user's current screen and no screen snapshot was provided yet"
            ),
            "continueLoopAfterAction" to Schema.boolean(
                description = "Set true if executing the UI step opens a new screen that requires one more observe-act step to finish the user's multi-step goal"
            ),
            "steps" to Schema.array(
                items = stepSchema,
                description = "Ordered list of 1 to 6 executable actions. Use SPEAK_ONLY when purely answering a question."
            )
        ),
        optionalProperties = listOf("needsScreenObservation", "continueLoopAfterAction")
    )

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
                responseMimeType = "application/json"
                responseSchema = planSchema
            }
        )
    }

    private val unconstrainedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.4f
            }
        )
    }

    private val fallbackLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.4f
            }
        )
    }

    private val summaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.45f
            }
        )
    }

    suspend fun planCommand(
        userPrompt: String,
        persona: AssistantPersona,
        deviceContextSummary: String,
        memoriesSummary: String,
        routinesSummary: String,
        contactsSummary: String,
        installedAppsSummary: String,
        screenUiSummary: String?,
        screenBitmap: Bitmap?
    ): Result<CommandPlan> {
        val fullInstruction = buildString {
            appendLine(persona.stylePrompt)
            appendLine("You are Aether 3D, a multitasking Android OS agent supporting Bangla (বাংলা) and English ONLY. Convert the user's voice/text request into a structured CommandPlan JSON.")
            appendLine("Rules:")
            appendLine("1. Support multitasking & compound commands: NEVER drop the second part of a double task! If user asks two tasks together (e.g. 'open YouTube, search Nova king entertainment, and turn on flashlight'), emit ALL required steps. Mark non-UI background tools (WEATHER_LOOKUP, WIKIPEDIA_LOOKUP, CURRENCY_CONVERT, SAVE_MEMORY, CREATE_ROUTINE, TOGGLE_TORCH, ADJUST_VOLUME, SET_BRIGHTNESS, SET_AUTO_ROTATE, SET_ALARM, SET_TIMER) with isBackgroundParallel=true.")
            appendLine("2. CRITICAL FOR APP SEARCH ('open YouTube and search X', 'search X in Spotify/Maps/Play Store/Chrome'): emit SEARCH_IN_APP with target=app name (e.g. 'YouTube') and textPayload=exact query (e.g. 'Nova king entertainment') — DO NOT emit bare LAUNCH_APP alone when a search query is included! Only use bare LAUNCH_APP when no in-app search/action is requested.")
            appendLine("3. For screen questions ('what is on my screen', 'summarize this', 'tap X', 'scroll down', 'type Y'), if screenUiSummary or an image is provided, answer directly or emit UI_TAP_TEXT, UI_TAP_COORDINATES (0-1000 scale), UI_INPUT_TEXT, UI_SCROLL, or GLOBAL_ACTION. If neither screenUiSummary nor image is attached yet and the command needs the screen, set needsScreenObservation=true.")
            appendLine("4. For calls/SMS/WhatsApp/Email, set target=contact name or phone/email, textPayload=message body, and isSensitive=true.")
            appendLine("5. For weather queries, emit WEATHER_LOOKUP with target=city name (or 'current' for local weather).")
            appendLine("6. For factual/encyclopedia queries needing live verification, emit WIKIPEDIA_LOOKUP with target=topic.")
            appendLine("7. For currency conversion, emit CURRENCY_CONVERT with target='FROM->TO' (e.g. 'USD->BDT' or 'USD->INR') and doubleValue=amount.")
            appendLine("8. For 'remember ...', emit SAVE_MEMORY with textPayload=fact to save.")
            appendLine("9. Match language cleanly: reply in Bangla if user wrote/spoke Bangla (or Banglish), otherwise English. If no device action is needed, emit a single SPEAK_ONLY step.")
            appendLine()
            appendLine("LIVE DEVICE TELEMETRY:")
            appendLine(deviceContextSummary)
            if (memoriesSummary.isNotBlank()) {
                appendLine("\nLOCAL SQLITE MEMORIES:\n$memoriesSummary")
            }
            if (routinesSummary.isNotBlank()) {
                appendLine("\nSAVED VOICE ROUTINES:\n$routinesSummary")
            }
            if (contactsSummary.isNotBlank()) {
                appendLine("\nQUICK CONTACTS:\n$contactsSummary")
            }
            if (installedAppsSummary.isNotBlank()) {
                appendLine("\nINSTALLED APPS SAMPLE:\n$installedAppsSummary")
            }
            if (!screenUiSummary.isNullOrBlank()) {
                appendLine("\nACTIVE SCREEN UI TREE:\n$screenUiSummary")
            }
            appendLine("\nUSER COMMAND: $userPrompt")
        }

        suspend fun invokeModel(model: com.google.firebase.ai.GenerativeModel, promptText: String): String {
            val response = withTimeout(45_000L) {
                if (screenBitmap != null) {
                    model.generateContent(
                        content {
                            image(screenBitmap)
                            text(promptText)
                        }
                    )
                } else {
                    model.generateContent(promptText)
                }
            }
            return response.text?.trim().orEmpty()
        }

        // Primary attempt: structured JSON schema on gemini-3.8-flash
        var primaryError: Exception? = null
        try {
            val rawJson = invokeModel(plannerModel, fullInstruction)
            if (rawJson.isNotBlank()) {
                return Result.success(parseCommandPlan(rawJson))
            }
            primaryError = IllegalStateException("Gemini returned an empty response.")
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.e(TAG, "Primary Gemini planning timed out", e)
            primaryError = e
        } catch (e: Exception) {
            Log.e(TAG, "Primary Gemini planning failed (${e.javaClass.simpleName}): ${e.message}", e)
            primaryError = e
        }

        val allowedActionTypes = ActionType.entries.joinToString(", ") { it.name }
        val fallbackPrompt = "$fullInstruction\n\nValid actionType values: $allowedActionTypes.\nRespond with a valid JSON object matching {\"reply\": string, \"needsScreenObservation\": boolean, \"steps\": [{\"actionType\": string, \"label\": string, \"target\": string, \"textPayload\": string, \"intValue\": integer, \"doubleValue\": number, \"boolValue\": boolean, \"isBackgroundParallel\": boolean, \"isSensitive\": boolean}]}."

        // Skip redundant Gemini models if Gemini already failed with an auth/AppCheck/network-host error
        val skipMoreGeminiRetries = primaryError != null && isNonRetryableGeminiError(primaryError)
        if (!skipMoreGeminiRetries) {
            // Fallback attempt 1: unconstrained gemini-3.8-flash (handles schema rejections / empty candidates)
            try {
                val rawFallback = invokeModel(unconstrainedModel, fallbackPrompt)
                if (rawFallback.isNotBlank()) {
                    return Result.success(parseCommandPlanOrPlainReply(rawFallback))
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.e(TAG, "Unconstrained Gemini fallback timed out", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unconstrained Gemini fallback failed (${e.javaClass.simpleName}): ${e.message}", e)
                if (primaryError == null) primaryError = e
            }

            // Fallback attempt 2: gemini-3.5-flash-lite
            try {
                val rawLite = invokeModel(fallbackLiteModel, fallbackPrompt)
                if (rawLite.isNotBlank()) {
                    return Result.success(parseCommandPlanOrPlainReply(rawLite))
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.e(TAG, "Lite Gemini fallback timed out", e)
            } catch (e: Exception) {
                Log.e(TAG, "Lite Gemini fallback failed (${e.javaClass.simpleName}): ${e.message}", e)
                if (primaryError == null) primaryError = e
            }
        }

        // Fallback attempt 3: NVIDIA NIM API (meta/llama-3.3-70b-instruct via BuildConfig.NVIDIA_API_KEY)
        if (nvidiaFallback.isConfigured()) {
            Log.w(TAG, "Gemini failed (${primaryError?.message}); routing to NVIDIA NIM fallback.")
            val nvidiaResult = nvidiaFallback.generateCommandJson(
                systemPersonaPrompt = persona.stylePrompt,
                fullPrompt = fallbackPrompt
            )
            nvidiaResult.onSuccess { rawNvidia ->
                return Result.success(parseCommandPlanOrPlainReply(rawNvidia))
            }.onFailure { nvidiaErr ->
                Log.e(TAG, "NVIDIA NIM fallback also failed: ${nvidiaErr.message}", nvidiaErr)
                return Result.failure(
                    IllegalStateException(
                        "${extractReadableError(primaryError)} | NVIDIA fallback: ${nvidiaErr.localizedMessage}"
                    )
                )
            }
        }

        val geminiErrMsg = extractReadableError(primaryError)
        val hint = if (!nvidiaFallback.isConfigured()) {
            " (Configure NVIDIA_API_KEY in Secrets panel to enable NVIDIA fallback)"
        } else ""
        return Result.failure(IllegalStateException("$geminiErrMsg$hint"))
    }

    private fun isNonRetryableGeminiError(e: Exception): Boolean {
        val msg = ((e.message ?: "") + " " + (e.cause?.message ?: "")).lowercase()
        return msg.contains("unknownhost") ||
            msg.contains("unable to resolve host") ||
            msg.contains("failed to connect") ||
            msg.contains("network is unreachable") ||
            msg.contains("403") ||
            msg.contains("app check") ||
            msg.contains("permission_denied")
    }

    private fun extractReadableError(e: Exception?): String {
        val detail = e?.cause?.localizedMessage?.takeIf { it.isNotBlank() }
            ?: e?.localizedMessage?.takeIf { it.isNotBlank() }
        return detail ?: "Gemini AI request failed. Check internet connection."
    }

    suspend fun synthesizeToolSummary(
        userPrompt: String,
        persona: AssistantPersona,
        toolOutputs: List<String>
    ): String? {
        if (toolOutputs.isEmpty()) return null
        val prompt = buildString {
            appendLine(persona.stylePrompt)
            appendLine("The user asked: \"$userPrompt\"")
            appendLine("Live tool outputs retrieved just now:")
            toolOutputs.forEachIndexed { idx, line -> appendLine("${idx + 1}. $line") }
            appendLine("Produce a crisp, natural spoken summary (max 2-3 sentences) combining these live facts accurately.")
        }
        try {
            val resp = withTimeout(25_000L) {
                summaryModel.generateContent(prompt)
            }
            val text = resp.text?.trim()
            if (!text.isNullOrBlank()) return text
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.e(TAG, "Tool summary timed out", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synthesize tool summary via Gemini", e)
        }
        return nvidiaFallback.synthesizeSummary(persona.stylePrompt, prompt)
    }

    internal fun parseCommandPlanOrPlainReply(rawOutput: String): CommandPlan {
        val trimmed = rawOutput.trim()
        val unwrapped = trimmed
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()
        val firstBrace = unwrapped.indexOf('{')
        val lastBrace = unwrapped.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val candidate = unwrapped.substring(firstBrace, lastBrace + 1)
            runCatching {
                val obj = JSONObject(candidate)
                if (obj.has("reply") || obj.has("steps")) {
                    return parseCommandPlan(candidate)
                }
            }
        }
        return CommandPlan(
            reply = unwrapped.ifBlank { "Done." },
            needsScreenObservation = false,
            continueLoopAfterAction = false,
            steps = listOf(PlannedStep(ActionType.SPEAK_ONLY, "Respond"))
        )
    }

    internal fun parseCommandPlan(rawJson: String): CommandPlan {
        val root = JSONObject(rawJson)
        val reply = root.optCleanString("reply", "On it.")
        val needsScreen = root.optBoolean("needsScreenObservation", false)
        val continueLoop = root.optBoolean("continueLoopAfterAction", false)
        val stepsArray = root.optJSONArray("steps") ?: JSONArray()

        val steps = buildList {
            for (i in 0 until stepsArray.length()) {
                val obj = stepsArray.optJSONObject(i) ?: continue
                val typeName = obj.optCleanString("actionType", ActionType.SPEAK_ONLY.name)
                val actionType = ActionType.entries.firstOrNull { it.name == typeName }
                    ?: ActionType.SPEAK_ONLY
                add(
                    PlannedStep(
                        actionType = actionType,
                        label = obj.optCleanString("label", actionType.name.replace("_", " ")),
                        target = obj.optCleanString("target", ""),
                        textPayload = obj.optCleanString("textPayload", ""),
                        intValue = if (obj.isNull("intValue")) 0 else obj.optInt("intValue", 0),
                        secondaryIntValue = if (obj.isNull("secondaryIntValue")) 0 else obj.optInt("secondaryIntValue", 0),
                        doubleValue = if (obj.isNull("doubleValue")) 0.0 else obj.optDouble("doubleValue", 0.0).let { if (it.isNaN()) 0.0 else it },
                        boolValue = !obj.isNull("boolValue") && obj.optBoolean("boolValue", false),
                        isBackgroundParallel = !obj.isNull("isBackgroundParallel") && obj.optBoolean("isBackgroundParallel", false),
                        isSensitive = !obj.isNull("isSensitive") && obj.optBoolean("isSensitive", false)
                    )
                )
            }
        }

        return CommandPlan(
            reply = reply,
            needsScreenObservation = needsScreen,
            continueLoopAfterAction = continueLoop,
            steps = if (steps.isEmpty()) {
                listOf(PlannedStep(ActionType.SPEAK_ONLY, "Respond"))
            } else {
                steps
            }
        )
    }

    private fun JSONObject.optCleanString(key: String, fallback: String): String {
        if (!has(key) || isNull(key)) return fallback
        return optString(key, fallback).takeUnless { it == "null" } ?: fallback
    }
}
