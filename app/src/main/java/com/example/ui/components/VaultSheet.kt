package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CommandLogEntity
import com.example.data.local.MemoryNoteEntity
import com.example.data.local.QuickContactEntity
import com.example.data.local.VoiceRoutineEntity
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.DeepCockpit
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.SmokedGlass
import com.example.ui.theme.StealthEmerald
import com.example.ui.theme.VoidObsidian
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultBottomSheet(
    logs: List<CommandLogEntity>,
    memories: List<MemoryNoteEntity>,
    routines: List<VoiceRoutineEntity>,
    contacts: List<QuickContactEntity>,
    onDismiss: () -> Unit,
    onReplayCommand: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onClearLogs: () -> Unit,
    onAddMemory: (String) -> Unit,
    onTogglePinMemory: (MemoryNoteEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onRunRoutine: (VoiceRoutineEntity) -> Unit,
    onSaveRoutine: (String, String, String) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onPickSystemContact: () -> Unit,
    onAddManualContact: (String, String, String, String) -> Unit,
    onDeleteContact: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepCockpit,
        contentColor = HudTextPrimary
    ) {
        VaultWorkspaceContent(
            logs = logs,
            memories = memories,
            routines = routines,
            contacts = contacts,
            onClose = onDismiss,
            onReplayCommand = {
                onReplayCommand(it)
                onDismiss()
            },
            onDeleteLog = onDeleteLog,
            onClearLogs = onClearLogs,
            onAddMemory = onAddMemory,
            onTogglePinMemory = onTogglePinMemory,
            onDeleteMemory = onDeleteMemory,
            onRunRoutine = {
                onRunRoutine(it)
                onDismiss()
            },
            onSaveRoutine = onSaveRoutine,
            onDeleteRoutine = onDeleteRoutine,
            onPickSystemContact = onPickSystemContact,
            onAddManualContact = onAddManualContact,
            onDeleteContact = onDeleteContact,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
        )
    }
}

@Composable
fun VaultWorkspaceContent(
    logs: List<CommandLogEntity>,
    memories: List<MemoryNoteEntity>,
    routines: List<VoiceRoutineEntity>,
    contacts: List<QuickContactEntity>,
    onClose: (() -> Unit)?,
    onReplayCommand: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onClearLogs: () -> Unit,
    onAddMemory: (String) -> Unit,
    onTogglePinMemory: (MemoryNoteEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onRunRoutine: (VoiceRoutineEntity) -> Unit,
    onSaveRoutine: (String, String, String) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onPickSystemContact: () -> Unit,
    onAddManualContact: (String, String, String, String) -> Unit,
    onDeleteContact: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("History", "Memories", "Routines", "Contacts")

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOCAL SQLITE VAULT",
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            if (onClose != null) {
                TextButton(onClick = onClose) {
                    Text("Close", color = HudTextSecondary)
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DeepCockpit,
            contentColor = accent
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> HistoryTab(
                logs = logs,
                accent = accent,
                onReplay = onReplayCommand,
                onDelete = onDeleteLog,
                onClearAll = onClearLogs
            )
            1 -> MemoriesTab(
                memories = memories,
                accent = accent,
                onAdd = onAddMemory,
                onTogglePin = onTogglePinMemory,
                onDelete = onDeleteMemory
            )
            2 -> RoutinesTab(
                routines = routines,
                accent = accent,
                onRun = onRunRoutine,
                onSave = onSaveRoutine,
                onDelete = onDeleteRoutine
            )
            3 -> ContactsTab(
                contacts = contacts,
                accent = accent,
                onPickSystemContact = onPickSystemContact,
                onAddManualContact = onAddManualContact,
                onDeleteContact = onDeleteContact
            )
        }
    }
}

@Composable
private fun HistoryTab(
    logs: List<CommandLogEntity>,
    accent: androidx.compose.ui.graphics.Color,
    onReplay: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(logs, query) {
        if (query.isBlank()) logs else logs.filter {
            it.userPrompt.contains(query, ignoreCase = true) ||
                it.assistantReply.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search command history...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (logs.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClearAll) {
                    Text("Clear", color = AlertCoral)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyVaultHint(
                icon = Icons.Filled.History,
                message = "No voice commands logged yet. Speak or type a command to populate your local SQLite history."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { log ->
                    Surface(
                        color = SmokedGlass,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${log.personaUsed} · ${log.sourceMode}${if (log.hadScreenVision) " · VISION" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                                Text(
                                    text = formatTimestamp(log.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudTextSecondary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = log.userPrompt,
                                style = MaterialTheme.typography.titleSmall,
                                color = HudTextPrimary
                            )
                            Text(
                                text = log.assistantReply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (log.tasksSummary.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = log.tasksSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StealthEmerald
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { onReplay(log.userPrompt) }) {
                                    Icon(Icons.Filled.Replay, contentDescription = "Replay command", tint = accent)
                                }
                                IconButton(onClick = { onDelete(log.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete log", tint = AlertCoral)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoriesTab(
    memories: List<MemoryNoteEntity>,
    accent: androidx.compose.ui.graphics.Color,
    onAdd: (String) -> Unit,
    onTogglePin: (MemoryNoteEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Add private memory note...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAdd(draft)
                        draft = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = VoidObsidian)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Save memory")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (memories.isEmpty()) {
            EmptyVaultHint(
                icon = Icons.Filled.Psychology,
                message = "Say \"Remember my locker code is 4590\" or add notes here for persistent on-device AI context."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.id }) { item ->
                    Surface(
                        color = SmokedGlass,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HudTextPrimary
                                )
                            }
                            IconButton(onClick = { onTogglePin(item) }) {
                                Icon(
                                    imageVector = if (item.isPinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Pin memory",
                                    tint = accent
                                )
                            }
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete memory", tint = AlertCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesTab(
    routines: List<VoiceRoutineEntity>,
    accent: androidx.compose.ui.graphics.Color,
    onRun: (VoiceRoutineEntity) -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = trigger,
            onValueChange = { trigger = it },
            placeholder = { Text("Trigger phrase (e.g., Gym Mode)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text("Multi-step action prompt...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (trigger.isNotBlank() && prompt.isNotBlank()) {
                        onSave(trigger, "Custom voice macro", prompt)
                        trigger = ""
                        prompt = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = VoidObsidian)
            ) {
                Text("Save")
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routines, key = { it.id }) { routine ->
                Surface(
                    color = SmokedGlass,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRun(routine) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.triggerPhrase,
                                style = MaterialTheme.typography.titleSmall,
                                color = accent
                            )
                            Text(
                                text = routine.instructionPrompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextPrimary
                            )
                            Text(
                                text = "Runs: ${routine.usageCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                        IconButton(onClick = { onRun(routine) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run routine", tint = StealthEmerald)
                        }
                        IconButton(onClick = { onDelete(routine.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete routine", tint = AlertCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(
    contacts: List<QuickContactEntity>,
    accent: androidx.compose.ui.graphics.Color,
    onPickSystemContact: () -> Unit,
    onAddManualContact: (String, String, String, String) -> Unit,
    onDeleteContact: (Long) -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onPickSystemContact,
            border = BorderStroke(1.dp, accent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_contact_button")
        ) {
            Icon(Icons.Filled.Contacts, contentDescription = "Import contact", tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("Import from Android Contacts (Zero Permission)", color = accent)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                placeholder = { Text("Alias (Mom)") },
                singleLine = true,
                modifier = Modifier.weight(0.9f)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = { Text("+91...") },
                singleLine = true,
                modifier = Modifier.weight(1.1f)
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    if (alias.isNotBlank() && phone.isNotBlank()) {
                        onAddManualContact(alias, alias, phone, "")
                        alias = ""
                        phone = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = VoidObsidian)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact alias")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (contacts.isEmpty()) {
            EmptyVaultHint(
                icon = Icons.Filled.Contacts,
                message = "Add quick aliases like 'Mom' or 'Boss' so voice commands can dial or message effortlessly."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contacts, key = { it.id }) { contact ->
                    Surface(
                        color = SmokedGlass,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${contact.aliasName} (${contact.displayName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HudTextPrimary
                                )
                                Text(
                                    text = contact.phoneNumber.ifBlank { contact.emailAddress },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                            }
                            IconButton(onClick = { onDeleteContact(contact.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete contact", tint = AlertCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HudTextSecondary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = HudTextSecondary
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(millis))
}
