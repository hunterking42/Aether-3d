package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CoreStatusAndReplyOverlay
import com.example.ui.components.PermissionsWizardSheet
import com.example.ui.components.TacticalCornerReticles
import com.example.ui.components.TopTelemetryBar
import com.example.ui.components.VaultBottomSheet
import com.example.ui.components.VaultWorkspaceContent
import com.example.ui.components.VoiceDock
import com.example.ui.gl.Aether3DViewport
import com.example.ui.theme.DeepCockpit
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.SmokedGlassElevated
import com.example.ui.theme.VoidObsidian
import com.example.ui.viewmodel.JarvisViewModel

@Composable
fun AetherCockpitScreen(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.historyLogs.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val contacts by viewModel.quickContacts.collectAsStateWithLifecycle()

    var showPermissionsWizard by remember { mutableStateOf(false) }
    var showVaultSheet by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePromptDraft by remember { mutableStateOf("") }

    // Open Permissions Wizard on first launch if not all permissions are active
    LaunchedEffect(Unit) {
        if (!uiState.preferences.hasCompletedOnboardingPrompt && !uiState.permissionSnapshot.allEnabled) {
            showPermissionsWizard = true
            viewModel.markOnboardingSeen()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionsAndTelemetry()
        if (uiState.backgroundServiceActive) {
            runCatching { com.example.service.VoiceAgentService.start(context) }
        }
    }

    val batchPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndTelemetry()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            imagePromptDraft = "Analyze this image and tell me what action to take."
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            viewModel.importPickedContact(uri)
        }
    }

    BackHandler(enabled = showPermissionsWizard || showVaultSheet || pendingImageUri != null) {
        when {
            pendingImageUri != null -> pendingImageUri = null
            showPermissionsWizard -> showPermissionsWizard = false
            showVaultSheet -> showVaultSheet = false
        }
    }

    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
    ) {
        val isExpandedTablet = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Layer 1: Full-Bleed Hardware OpenGL ES 3D Core (zero recomposition flow)
                Aether3DViewport(
                    coreMode = uiState.coreMode,
                    persona = uiState.preferences.persona,
                    audioAmplitudeFlow = viewModel.audioAmplitude,
                    onCoreTapped = {
                        if (!uiState.permissionSnapshot.micGranted) {
                            batchPermissionLauncher.launch(
                                viewModel.permissionsManager.runtimePermissionsToRequest()
                            )
                        } else {
                            viewModel.onMicTapped()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Layer 2: Tactical Corner Reticles
                TacticalCornerReticles(accentColor = accent)

                // Layer 3: Safe-Area Compose HUD Chrome
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    TopTelemetryBar(
                        uiState = uiState,
                        onCyclePersona = { viewModel.cyclePersona() },
                        onOpenPermissions = { showPermissionsWizard = true },
                        onOpenVault = { showVaultSheet = !showVaultSheet }
                    )

                    Spacer(Modifier.weight(1f))

                    CoreStatusAndReplyOverlay(
                        uiState = uiState,
                        onAbortTasks = { viewModel.abortAllTasks() },
                        onConfirmSensitive = { viewModel.confirmSensitiveSteps() },
                        onDismissSensitive = { viewModel.dismissSensitiveSteps() }
                    )

                    Spacer(Modifier.height(6.dp))

                    VoiceDock(
                        uiState = uiState,
                        audioAmplitudeFlow = viewModel.audioAmplitude,
                        onMicClick = {
                            if (!uiState.permissionSnapshot.micGranted) {
                                batchPermissionLauncher.launch(
                                    viewModel.permissionsManager.runtimePermissionsToRequest()
                                )
                            } else {
                                viewModel.onMicTapped()
                            }
                        },
                        onSubmitText = { viewModel.submitTextCommand(it) },
                        onVisualScanClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onToggleBackgroundService = {
                            if (!uiState.permissionSnapshot.micGranted || !uiState.permissionSnapshot.notificationsGranted) {
                                showPermissionsWizard = true
                            } else {
                                viewModel.toggleBackgroundService()
                            }
                        },
                        onToggleTts = { viewModel.toggleTts() }
                    )
                }
            }

            if (isExpandedTablet && showVaultSheet) {
                Surface(
                    color = DeepCockpit,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    VaultWorkspaceContent(
                        logs = logs,
                        memories = memories,
                        routines = routines,
                        contacts = contacts,
                        onClose = { showVaultSheet = false },
                        onReplayCommand = { viewModel.submitTextCommand(it) },
                        onDeleteLog = { viewModel.deleteLog(it) },
                        onClearLogs = { viewModel.clearLogs() },
                        onAddMemory = { viewModel.addMemory(it) },
                        onTogglePinMemory = { viewModel.toggleMemoryPinned(it) },
                        onDeleteMemory = { viewModel.deleteMemory(it) },
                        onRunRoutine = { viewModel.runRoutine(it) },
                        onSaveRoutine = { t, d, p -> viewModel.saveRoutine(t, d, p) },
                        onDeleteRoutine = { viewModel.deleteRoutine(it) },
                        onPickSystemContact = { contactPickerLauncher.launch(null) },
                        onAddManualContact = { a, d, p, e -> viewModel.addQuickContact(a, d, p, e) },
                        onDeleteContact = { viewModel.deleteQuickContact(it) }
                    )
                }
            }
        }

        if (!isExpandedTablet && showVaultSheet) {
            VaultBottomSheet(
                logs = logs,
                memories = memories,
                routines = routines,
                contacts = contacts,
                onDismiss = { showVaultSheet = false },
                onReplayCommand = { viewModel.submitTextCommand(it) },
                onDeleteLog = { viewModel.deleteLog(it) },
                onClearLogs = { viewModel.clearLogs() },
                onAddMemory = { viewModel.addMemory(it) },
                onTogglePinMemory = { viewModel.toggleMemoryPinned(it) },
                onDeleteMemory = { viewModel.deleteMemory(it) },
                onRunRoutine = { viewModel.runRoutine(it) },
                onSaveRoutine = { t, d, p -> viewModel.saveRoutine(t, d, p) },
                onDeleteRoutine = { viewModel.deleteRoutine(it) },
                onPickSystemContact = { contactPickerLauncher.launch(null) },
                onAddManualContact = { a, d, p, e -> viewModel.addQuickContact(a, d, p, e) },
                onDeleteContact = { viewModel.deleteQuickContact(it) }
            )
        }

        if (showPermissionsWizard) {
            PermissionsWizardSheet(
                uiState = uiState,
                onDismiss = { showPermissionsWizard = false },
                onRequestAllRuntimePermissions = {
                    batchPermissionLauncher.launch(
                        viewModel.permissionsManager.runtimePermissionsToRequest()
                    )
                },
                onLaunchSpecialSettings = { permId ->
                    runCatching {
                        context.startActivity(
                            viewModel.permissionsManager.buildSpecialSettingsIntent(permId)
                        )
                    }
                },
                onToggleAutonomousMode = { viewModel.toggleAutonomousMode() },
                onToggleContinuousLoop = { viewModel.toggleContinuousLoop() },
                onToggleSplitScreen = { viewModel.toggleSplitScreenLaunch() },
                onSelectLocale = { viewModel.setSpeechLocale(it) }
            )
        }

        pendingImageUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingImageUri = null },
                containerColor = SmokedGlassElevated,
                titleContentColor = HudTextPrimary,
                textContentColor = HudTextPrimary,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Visual Scan with Gemini", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Column {
                        Text(
                            text = "Ask Aether what to read, summarize, or execute from this image:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imagePromptDraft,
                            onValueChange = { imagePromptDraft = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.submitImageCommand(uri, imagePromptDraft)
                            pendingImageUri = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = VoidObsidian)
                    ) {
                        Text("Analyze")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImageUri = null }) {
                        Text("Cancel", color = HudTextPrimary)
                    }
                }
            )
        }
    }
}
