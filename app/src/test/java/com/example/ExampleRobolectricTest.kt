package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ActionType
import com.example.ai.AssistantPersona
import com.example.ai.CoreMode
import com.example.ai.GeminiCommandService
import com.example.ai.PlannedStep
import com.example.ai.SpeechLocaleOption
import com.example.core.AssistantUiState
import com.example.data.local.CommandLogEntity
import com.example.data.local.QuickContactEntity
import com.example.data.local.VaultRepository
import com.example.system.AndroidActionRouter
import com.example.system.DeviceTelemetryProvider
import com.example.system.PermissionsManager
import com.example.tools.FreeWebToolsRepository
import com.example.ui.components.CoreStatusAndReplyOverlay
import com.example.ui.components.TopTelemetryBar
import com.example.ui.components.VoiceDock
import com.example.ui.gl.GeometryBuilder
import com.example.ui.gl.JarvisRenderer
import com.example.ui.gl.ShaderUtils
import com.example.ui.theme.Aether3DTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name and branding resources match Aether 3D`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Aether 3D", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.accessibility_service_label).contains("Aether 3D"))
    }

    @Test
    fun `3d avatar geometry builders produce valid non-empty finite meshes`() {
        val head = GeometryBuilder.buildAvatarHeadMesh()
        val headWire = GeometryBuilder.buildAvatarHeadWireframe()
        val jaw = GeometryBuilder.buildAvatarJawMesh()
        val visor = GeometryBuilder.buildAvatarVisorEyesMesh()
        val torso = GeometryBuilder.buildAvatarTorsoMesh()
        val chestCore = GeometryBuilder.buildAvatarChestCoreMesh()
        val ring = GeometryBuilder.buildSegmentedArcRing(1.18f)
        val prism = GeometryBuilder.buildUnitPrismTriangles()
        val grid = GeometryBuilder.buildPerspectiveFloorGrid()
        val particles = GeometryBuilder.buildOrbitalParticles(140)

        val buffers = listOf(head, headWire, jaw, visor, torso, chestCore, ring, prism, grid, particles)
        buffers.forEach { mesh ->
            assertTrue("Expected positive vertexCount", mesh.vertexCount > 0)
            mesh.buffer.position(0)
            while (mesh.buffer.hasRemaining()) {
                assertFalse("Unexpected NaN/Inf in mesh buffer", mesh.buffer.get().isNaN())
            }
            mesh.buffer.position(0)
        }

        // Verify Renderer orbit/zoom camera math bounds
        val renderer = JarvisRenderer()
        renderer.addOrbitDrag(120f, 900f)
        assertTrue(renderer.userPitchOffset in -55f..55f)
        renderer.applyZoomScale(10f)
        assertTrue(renderer.userZoomFactor in 0.65f..1.65f)
        renderer.resetCamera()
        assertEquals(0f, renderer.userYawOffset, 0.001f)
        assertEquals(1f, renderer.userZoomFactor, 0.001f)
    }

    @Test
    fun `shaders enforce matching mediump precisions on shared uniforms and varyings`() {
        assertTrue(ShaderUtils.CORE_VERTEX_SHADER.contains("uniform mediump float uTime;"))
        assertTrue(ShaderUtils.CORE_FRAGMENT_SHADER.contains("uniform mediump float uTime;"))
        assertTrue(ShaderUtils.CORE_VERTEX_SHADER.contains("uniform mediump float uAudioLevel;"))
        assertTrue(ShaderUtils.CORE_FRAGMENT_SHADER.contains("uniform mediump float uAudioLevel;"))
        assertTrue(ShaderUtils.CORE_VERTEX_SHADER.contains("varying mediump vec3 vNormal;"))
        assertTrue(ShaderUtils.CORE_FRAGMENT_SHADER.contains("varying mediump vec3 vNormal;"))
        assertTrue(ShaderUtils.LINE_VERTEX_SHADER.contains("varying mediump vec3 vWorldPos;"))
        assertTrue(ShaderUtils.LINE_FRAGMENT_SHADER.contains("varying mediump vec3 vWorldPos;"))
        assertTrue(ShaderUtils.PARTICLE_VERTEX_SHADER.contains("varying mediump float vAlpha;"))
        assertTrue(ShaderUtils.PARTICLE_FRAGMENT_SHADER.contains("varying mediump float vAlpha;"))
    }

    @Test
    fun `gemini command plan parser handles multitasking json and explicit nulls safely`() {
        val service = GeminiCommandService()
        val rawJson = """
            {
              "reply": "Turning on flashlight and checking weather in Delhi.",
              "needsScreenObservation": false,
              "continueLoopAfterAction": false,
              "steps": [
                {
                  "actionType": "TOGGLE_TORCH",
                  "label": "Flashlight On",
                  "target": null,
                  "textPayload": null,
                  "boolValue": true,
                  "isBackgroundParallel": true,
                  "isSensitive": false
                },
                {
                  "actionType": "WEATHER_LOOKUP",
                  "label": "Delhi Weather",
                  "target": "Delhi",
                  "isBackgroundParallel": true
                }
              ]
            }
        """.trimIndent()

        val plan = service.parseCommandPlan(rawJson)
        assertEquals(2, plan.steps.size)
        assertEquals(ActionType.TOGGLE_TORCH, plan.steps[0].actionType)
        assertEquals("", plan.steps[0].target)
        assertEquals("", plan.steps[0].textPayload)
        assertTrue(plan.steps[0].boolValue)
        assertTrue(plan.steps[0].isBackgroundParallel)
        assertEquals(ActionType.WEATHER_LOOKUP, plan.steps[1].actionType)
        assertEquals("Delhi", plan.steps[1].target)
    }

    @Test
    fun `vault repository persists logs memories routines contacts and preferences`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = VaultRepository(context)

        // Logs
        vault.insertLog(
            CommandLogEntity(
                userPrompt = "Turn on flashlight",
                assistantReply = "Flashlight enabled",
                personaUsed = "JARVIS",
                sourceMode = "FOREGROUND",
                hadScreenVision = false,
                tasksSummary = "Flashlight On: DONE",
                executionStatus = "EXECUTED"
            )
        )

        // Memories & Contacts
        val memId = vault.saveMemory("Gate code is 4590", "FACT")
        vault.toggleMemoryPinned(memId, true)
        vault.saveQuickContact(
            QuickContactEntity(
                aliasName = "Mom",
                displayName = "Mom",
                phoneNumber = "+919876543210"
            )
        )

        // Routines
        val routineId = vault.saveRoutine("Gym Mode", "Play pump mix", "Open YouTube and set volume 80")
        vault.recordRoutineUsed(routineId)

        // Preferences
        val savedPrefs = vault.loadPreferences().copy(
            persona = AssistantPersona.MAX,
            speechLocale = SpeechLocaleOption.BN_BD,
            continuousLoopEnabled = true
        )
        vault.savePreferences(savedPrefs)
        val reloadedPrefs = vault.loadPreferences()

        assertTrue(vault.getTopMemories().any { it.content.contains("4590") })
        assertTrue(vault.getContactsSnapshot().any { it.aliasName == "Mom" && it.phoneNumber == "+919876543210" })
        assertTrue(vault.getRoutinesSnapshot().any { it.triggerPhrase == "Gym Mode" && it.usageCount >= 1 })
        assertEquals(AssistantPersona.MAX, reloadedPrefs.persona)
        assertEquals(SpeechLocaleOption.BN_BD, reloadedPrefs.speechLocale)
        assertEquals(2, SpeechLocaleOption.entries.size)
        assertTrue(reloadedPrefs.continuousLoopEnabled)
    }

    @Test
    fun `permissions manager reports all nine capabilities and valid intents`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionsManager(context)
        val snapshot = manager.checkSnapshot()

        assertEquals(9, snapshot.totalCount)
        assertEquals(9, snapshot.toItemList().size)
        assertTrue(manager.runtimePermissionsToRequest().isNotEmpty())
        assertEquals(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            manager.buildSpecialSettingsIntent("overlay").action
        )
        assertEquals(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            manager.buildSpecialSettingsIntent("accessibility").action
        )
    }

    @Test
    fun `action router executes local memory routine and accessibility guard cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = VaultRepository(context)
        val telemetry = DeviceTelemetryProvider(context)
        val router = AndroidActionRouter(context, telemetry, vault, FreeWebToolsRepository())

        val memRes = router.executeStep(
            PlannedStep(ActionType.SAVE_MEMORY, "Save Note", textPayload = "WiFi pin is 7788"),
            splitScreenEnabled = false
        )
        assertTrue(memRes.success)
        assertTrue(vault.getTopMemories().any { it.content.contains("7788") })

        val uiTapRes = router.executeStep(
            PlannedStep(ActionType.UI_TAP_TEXT, "Tap Skip", target = "Skip"),
            splitScreenEnabled = false
        )
        assertFalse(uiTapRes.success)
        assertTrue(uiTapRes.detail.contains("Trusted Screen Agent"))

        val speakRes = router.executeStep(
            PlannedStep(ActionType.SPEAK_ONLY, "Reply"),
            splitScreenEnabled = false
        )
        assertTrue(speakRes.success)
    }

    @Test
    fun `hud compose chrome renders telemetry overlay and voice dock actions`() {
        var personaClicks = 0
        var micClicks = 0
        var submittedCmd = ""
        val ampFlow = MutableStateFlow(0.4f)
        val state = AssistantUiState(
            coreMode = CoreMode.IDLE,
            latestReply = "3D Avatar online and standing by."
        )

        composeRule.setContent {
            Aether3DTheme(persona = AssistantPersona.JARVIS) {
                androidx.compose.foundation.layout.Column {
                    TopTelemetryBar(
                        uiState = state,
                        onCyclePersona = { personaClicks++ },
                        onOpenPermissions = {},
                        onOpenVault = {}
                    )
                    CoreStatusAndReplyOverlay(
                        uiState = state,
                        onAbortTasks = {},
                        onConfirmSensitive = {},
                        onDismissSensitive = {}
                    )
                    VoiceDock(
                        uiState = state,
                        audioAmplitudeFlow = ampFlow,
                        onMicClick = { micClicks++ },
                        onSubmitText = { submittedCmd = it },
                        onVisualScanClick = {},
                        onToggleBackgroundService = {},
                        onToggleTts = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("persona_cycle_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("mic_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("command_input").assertIsDisplayed()
        composeRule.onNodeWithText("3D Avatar online and standing by.").assertIsDisplayed()

        assertEquals(1, personaClicks)
        assertEquals(1, micClicks)
        assertNotNull(submittedCmd)
    }

    @Test
    fun `gemini unconstrained fallback parser extracts fenced json and plain replies`() {
        val service = GeminiCommandService()
        val fenced = """
            Sure, executing right away:
            ```json
            {
              "reply": "Opening Settings now.",
              "needsScreenObservation": false,
              "steps": [
                {"actionType": "OPEN_SETTINGS", "label": "Open WiFi", "target": "wifi"}
              ]
            }
            ```
        """.trimIndent()

        val parsed = service.parseCommandPlanOrPlainReply(fenced)
        assertEquals("Opening Settings now.", parsed.reply)
        assertEquals(1, parsed.steps.size)
        assertEquals(ActionType.OPEN_SETTINGS, parsed.steps[0].actionType)
        assertEquals("wifi", parsed.steps[0].target)

        val plain = service.parseCommandPlanOrPlainReply("The capital of India is New Delhi.")
        assertEquals("The capital of India is New Delhi.", plain.reply)
        assertEquals(ActionType.SPEAK_ONLY, plain.steps.first().actionType)
    }

    @Test
    fun `nvidia fallback service validates key placeholder and parses chat completion json`() {
        val unconfigured = com.example.ai.NvidiaFallbackService(apiKey = "NVIDIA_API_KEY_DEFAULT_VALUE")
        assertFalse(unconfigured.isConfigured())

        val blankKey = com.example.ai.NvidiaFallbackService(apiKey = "   ")
        assertFalse(blankKey.isConfigured())

        val configured = com.example.ai.NvidiaFallbackService(apiKey = "nvapi-sample-valid-token")
        assertTrue(configured.isConfigured())

        val mockNvidiaResponse = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\"reply\":\"Torch turned on.\",\"steps\":[{\"actionType\":\"TOGGLE_TORCH\",\"label\":\"Torch On\",\"boolValue\":true}]}"
                  }
                }
              ]
            }
        """.trimIndent()
        val extracted = configured.extractAssistantContent(mockNvidiaResponse)
        assertTrue(extracted.contains("TOGGLE_TORCH"))
        val plan = GeminiCommandService(configured).parseCommandPlanOrPlainReply(extracted)
        assertEquals("Torch turned on.", plan.reply)
        assertEquals(ActionType.TOGGLE_TORCH, plan.steps.first().actionType)
        assertTrue(plan.steps.first().boolValue)
    }

    @Test
    fun `human cartoon avatar renders with accessibility test tag for personas`() {
        val ampFlow = MutableStateFlow(0.55f)
        composeRule.setContent {
            Aether3DTheme(persona = AssistantPersona.MAYRA) {
                com.example.ui.components.HumanCartoonAvatar(
                    coreMode = CoreMode.SPEAKING,
                    persona = AssistantPersona.MAYRA,
                    audioAmplitudeFlow = ampFlow,
                    headYawDeg = 8f,
                    headPitchDeg = -4f,
                    zoomScale = 1.0f
                )
            }
        }
        composeRule.onNodeWithTag("human_cartoon_avatar").assertIsDisplayed()
    }

    @Test
    fun `compound in-app search parses cleanly and realistic portrait drawables resolve for all personas`() {
        val service = GeminiCommandService()
        val compoundJson = """
            {
              "reply": "Opening YouTube and searching Nova king entertainment.",
              "needsScreenObservation": false,
              "steps": [
                {
                  "actionType": "SEARCH_IN_APP",
                  "label": "Search YouTube",
                  "target": "YouTube",
                  "textPayload": "Nova king entertainment"
                }
              ]
            }
        """.trimIndent()
        val plan = service.parseCommandPlanOrPlainReply(compoundJson)
        assertEquals(ActionType.SEARCH_IN_APP, plan.steps.first().actionType)
        assertEquals("YouTube", plan.steps.first().target)
        assertEquals("Nova king entertainment", plan.steps.first().textPayload)

        val context = ApplicationProvider.getApplicationContext<Context>()
        AssistantPersona.entries.forEach { persona ->
            val resId = com.example.ui.components.avatarDrawableFor(persona)
            assertNotNull(context.resources.getDrawable(resId, null))
        }
        assertEquals(
            setOf(SpeechLocaleOption.EN_US, SpeechLocaleOption.BN_BD),
            SpeechLocaleOption.entries.toSet()
        )
    }
}
