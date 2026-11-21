package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ActiveTaskItem
import com.example.ai.CoreMode
import com.example.ai.TaskStatus
import com.example.core.AssistantUiState
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.SmokedGlass
import com.example.ui.theme.SmokedGlassElevated
import com.example.ui.theme.SolarGold
import com.example.ui.theme.StealthEmerald
import com.example.ui.theme.VoidObsidian

@Composable
fun TacticalCornerReticles(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val len = 26.dp.toPx()
        val inset = 14.dp.toPx()
        val stroke = 1.6.dp.toPx()
        val c = accentColor.copy(alpha = 0.42f)

        // Top-left
        drawLine(c, Offset(inset, inset), Offset(inset + len, inset), stroke)
        drawLine(c, Offset(inset, inset), Offset(inset, inset + len), stroke)
        // Top-right
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), stroke)
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), stroke)
        // Bottom-left
        drawLine(c, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), stroke)
        drawLine(c, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), stroke)
        // Bottom-right
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), stroke)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), stroke)
    }
}

@Composable
fun TopTelemetryBar(
    uiState: AssistantUiState,
    onCyclePersona: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val perms = uiState.permissionSnapshot

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Persona + Live Status Telemetry
        Surface(
            color = SmokedGlass,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("persona_cycle_button")
                .clickable(onClick = onCyclePersona)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when (uiState.coreMode) {
                                CoreMode.ERROR -> AlertCoral
                                CoreMode.THINKING, CoreMode.OBSERVING_SCREEN -> SolarGold
                                CoreMode.MULTITASKING -> StealthEmerald
                                else -> accent
                            },
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = uiState.preferences.persona.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = HudTextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.telemetry.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                            contentDescription = "Battery level",
                            tint = HudTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${uiState.telemetry.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = "Network status",
                            tint = HudTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = uiState.telemetry.networkLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }
                }
            }
        }

        // Right: Permissions Readiness Pill + SQLite Vault Button
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allGranted = perms.allEnabled
            val permColor = if (allGranted) StealthEmerald else SolarGold
            Surface(
                color = SmokedGlass,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, permColor.copy(alpha = 0.55f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("permissions_button")
                    .clickable(onClick = onOpenPermissions)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (allGranted) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                        contentDescription = "Permissions Hub",
                        tint = permColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${perms.grantedCount}/${perms.totalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = permColor
                    )
                }
            }

            Surface(
                color = SmokedGlass,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vault_button")
                    .clickable(onClick = onOpenVault)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderSpecial,
                        contentDescription = "Open SQLite Vault",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "VAULT",
                        style = MaterialTheme.typography.labelMedium,
                        color = HudTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun CoreStatusAndReplyOverlay(
    uiState: AssistantUiState,
    onAbortTasks: () -> Unit,
    onConfirmSensitive: () -> Unit,
    onDismissSensitive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Core Mode Status Badge
        Surface(
            color = SmokedGlass,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
        ) {
            Text(
                text = uiState.coreMode.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        // Live Partial Transcript
        AnimatedVisibility(
            visible = uiState.partialTranscript.isNotBlank(),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Surface(
                color = SmokedGlassElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "\"${uiState.partialTranscript}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Guarded Sensitive Confirmation Card
        AnimatedVisibility(visible = uiState.pendingConfirmationSteps.isNotEmpty()) {
            Surface(
                color = SmokedGlassElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.75f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "GUARDED CONFIRMATION REQUIRED",
                        style = MaterialTheme.typography.labelMedium,
                        color = SolarGold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.pendingConfirmationReply,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onConfirmSensitive,
                            colors = ButtonDefaults.buttonColors(containerColor = StealthEmerald, contentColor = VoidObsidian),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_sensitive_button")
                        ) {
                            Text("Confirm & Run")
                        }
                        OutlinedButton(
                            onClick = onDismissSensitive,
                            border = BorderStroke(1.dp, AlertCoral),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_sensitive_button")
                        ) {
                            Text("Cancel", color = AlertCoral)
                        }
                    }
                }
            }
        }

        // Live Multitasking Queue Card
        AnimatedVisibility(visible = uiState.activeTasks.isNotEmpty()) {
            Surface(
                color = SmokedGlass,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TASK PIPELINE (${uiState.activeTasks.count { it.status == TaskStatus.DONE }}/${uiState.activeTasks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                        if (uiState.coreMode == CoreMode.MULTITASKING || uiState.coreMode == CoreMode.THINKING) {
                            IconButton(
                                onClick = onAbortTasks,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("abort_tasks_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Abort active tasks",
                                    tint = AlertCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    uiState.activeTasks.take(4).forEach { task ->
                        TaskRowItem(task = task, accent = accent)
                    }
                }
            }
        }

        // Latest Response / Error Card
        val errorMsg = uiState.errorMessage
        Surface(
            color = SmokedGlass,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (errorMsg != null) AlertCoral.copy(alpha = 0.7f) else accent.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, VoidObsidian.copy(alpha = 0.25f))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertCoral,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    if (uiState.lastUserPrompt.isNotBlank()) {
                        Text(
                            text = "YOU: ${uiState.lastUserPrompt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    val displayText = when (uiState.coreMode) {
                        CoreMode.THINKING -> "Thinking with Gemini..."
                        CoreMode.OBSERVING_SCREEN -> "Reading active screen..."
                        else -> uiState.latestReply
                    }
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = HudTextPrimary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRowItem(task: ActiveTaskItem, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (task.status) {
            TaskStatus.RUNNING -> CircularProgressIndicator(
                color = accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            TaskStatus.DONE -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Task done",
                tint = StealthEmerald,
                modifier = Modifier.size(14.dp)
            )
            TaskStatus.FAILED -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Task failed",
                tint = AlertCoral,
                modifier = Modifier.size(14.dp)
            )
            TaskStatus.NEEDS_CONFIRMATION -> Icon(
                imageVector = Icons.Filled.HourglassTop,
                contentDescription = "Task awaiting confirmation",
                tint = SolarGold,
                modifier = Modifier.size(14.dp)
            )
            TaskStatus.QUEUED -> Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = "Task queued",
                tint = HudTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = task.step.label,
            style = MaterialTheme.typography.labelMedium,
            color = HudTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (task.detail.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = task.detail,
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
