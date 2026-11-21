package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppContainer
import com.example.data.local.NoteEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.GlassCard
import kotlinx.coroutines.launch

@Composable
fun NotesTool(
    container: AppContainer,
    snackbarHostState: SnackbarHostState
) {
    val notes by container.localRepository.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    var search by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val filtered = remember(notes, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) notes
        else notes.filter {
            it.title.lowercase().contains(q) ||
                it.body.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showEditor = true
                },
                modifier = Modifier.testTag("notes_add_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create note")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search offline notes...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No notes yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to jot down ideas, scripts, or drafts offline. Signed-in users also sync notes to Firestore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        GlassCard(
                            onClick = {
                                editingNote = note
                                showEditor = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = note.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                container.saveNoteAndSync(
                                                    title = note.title,
                                                    body = note.body,
                                                    category = note.category,
                                                    isPinned = !note.isPinned,
                                                    isFavorite = note.isFavorite,
                                                    existingId = note.id,
                                                    createdAtMillis = note.createdAtMillis
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PushPin,
                                            contentDescription = "Pin note",
                                            tint = if (note.isPinned) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                container.deleteNoteAndSync(note.id)
                                                snackbarHostState.showSnackbar("Note deleted")
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete note",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                note.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        var title by remember(editingNote) { mutableStateOf(editingNote?.title.orEmpty()) }
        var body by remember(editingNote) { mutableStateOf(editingNote?.body.orEmpty()) }
        var category by remember(editingNote) { mutableStateOf(editingNote?.category ?: "Ideas") }

        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingNote == null) "New Note" else "Edit Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            container.saveNoteAndSync(
                                title = title.ifBlank { "Untitled Note" },
                                body = body,
                                category = category.ifBlank { "General" },
                                isPinned = editingNote?.isPinned ?: false,
                                isFavorite = editingNote?.isFavorite ?: false,
                                existingId = editingNote?.id,
                                createdAtMillis = editingNote?.createdAtMillis
                            )
                            showEditor = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
