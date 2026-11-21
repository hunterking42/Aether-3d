package com.example.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "NvidiaFallbackService"
private const val NVIDIA_ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
private const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.3-70b-instruct"
private const val PLACEHOLDER_VALUE = "NVIDIA_API_KEY_DEFAULT_VALUE"

class NvidiaFallbackService(
    private val apiKey: String = BuildConfig.NVIDIA_API_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    fun isConfigured(): Boolean {
        val trimmed = apiKey.trim()
        return trimmed.isNotEmpty() &&
            trimmed != PLACEHOLDER_VALUE &&
            !trimmed.equals("null", ignoreCase = true)
    }

    suspend fun generateCommandJson(
        systemPersonaPrompt: String,
        fullPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("NVIDIA_API_KEY is not configured in Secrets panel.")
            )
        }
        callChatCompletion(
            systemPrompt = "$systemPersonaPrompt You MUST respond with a single valid JSON object matching {\"reply\": string, \"needsScreenObservation\": boolean, \"continueLoopAfterAction\": boolean, \"steps\": [{\"actionType\": string, \"label\": string, \"target\": string, \"textPayload\": string, \"intValue\": integer, \"secondaryIntValue\": integer, \"doubleValue\": number, \"boolValue\": boolean, \"isBackgroundParallel\": boolean, \"isSensitive\": boolean}]}. Use only valid ActionType names from the instructions.",
            userPrompt = fullPrompt,
            temperature = 0.25,
            maxTokens = 1024
        )
    }

    suspend fun synthesizeSummary(
        systemPersonaPrompt: String,
        prompt: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        callChatCompletion(
            systemPrompt = "$systemPersonaPrompt Respond cleanly in 2-3 concise spoken sentences.",
            userPrompt = prompt,
            temperature = 0.45,
            maxTokens = 320
        ).getOrNull()
    }

    private fun callChatCompletion(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): Result<String> {
        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val payload = JSONObject().apply {
                put("model", DEFAULT_NVIDIA_MODEL)
                put("messages", messages)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
            }.toString()

            val request = Request.Builder()
                .url(NVIDIA_ENDPOINT)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiErr = extractApiError(rawBody, response.code)
                    Log.e(TAG, "NVIDIA NIM HTTP ${response.code}: $apiErr")
                    return Result.failure(IllegalStateException("NVIDIA fallback error (${response.code}): $apiErr"))
                }
                val content = extractAssistantContent(rawBody)
                if (content.isBlank()) {
                    return Result.failure(IllegalStateException("NVIDIA NIM returned an empty response."))
                }
                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "NVIDIA NIM call failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "NVIDIA NIM request failed"))
        }
    }

    internal fun extractAssistantContent(rawResponseJson: String): String {
        val root = JSONObject(rawResponseJson)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val firstChoice = choices.optJSONObject(0) ?: return ""
        val message = firstChoice.optJSONObject("message") ?: return ""
        return message.optString("content", "").trim()
    }

    internal fun extractApiError(rawBody: String, httpCode: Int): String {
        if (rawBody.isBlank()) return "HTTP $httpCode"
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("detail").takeIf { it.isNotBlank() }
                ?: obj.optString("title").takeIf { it.isNotBlank() }
                ?: "HTTP $httpCode"
        }.getOrDefault("HTTP $httpCode")
    }
}
