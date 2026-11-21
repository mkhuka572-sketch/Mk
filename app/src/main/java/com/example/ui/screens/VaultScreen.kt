package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppContainer
import com.example.core.ExportUtils
import com.example.data.local.CreationEntity
import com.example.data.local.DownloadHistoryEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun VaultScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val creations by container.localRepository.observeCreations().collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by container.localRepository.observeDownloads().collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var inspectItem by remember { mutableStateOf<CreationEntity?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val filters = listOf(
        "ALL" to "All (${creations.size})",
        "FAVORITES" to "Favorites",
        "IMAGE" to "Images",
        "WRITING" to "Writing",
        "VIDEO_PLAN" to "Storyboards",
        "PHOTO_QA" to "Vision",
        "DOWNLOADS" to "Downloads (${downloads.size})"
    )

    val filteredCreations = remember(creations, selectedFilter, search) {
        val q = search.trim().lowercase()
        creations.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                else -> item.type == selectedFilter
            }
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.content.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Saved Vault & History",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Offline-first library of your creations, favorites, and exported files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search saved creations...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("vault_search_input")
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedFilter == "DOWNLOADS") {
                if (downloads.isEmpty()) {
                    EmptyVaultCard("No downloads yet", "Exported images, QR codes, and WAV audio files will appear here.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(downloads, key = { it.id }) { dl ->
                            DownloadRow(
                                entry = dl,
                                onDelete = {
                                    scope.launch { container.localRepository.deleteDownload(dl.id) }
                                }
                            )
                        }
                    }
                }
            } else {
                if (filteredCreations.isEmpty()) {
                    EmptyVaultCard(
                        "No saved creations here",
                        "Save outputs from AI Writing, Image Studio, Video Planner, or Photo Vision to organize them here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCreations, key = { it.id }) { item ->
                            CreationVaultCard(
                                item = item,
                                onOpen = { inspectItem = item },
                                onToggleFavorite = {
                                    scope.launch { container.toggleCreationFavoriteAndSync(item) }
                                },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(item.content))
                                    scope.launch { snackbarHostState.showSnackbar("Copied content") }
                                },
                                onShare = {
                                    ExportUtils.shareText(context, item.title, item.content)
                                },
                                onDelete = {
                                    scope.launch {
                                        container.deleteCreationAndSync(item.id)
                                        snackbarHostState.showSnackbar("Removed from Vault")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    inspectItem?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectItem = null },
            title = { Text(item.title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${item.type} • ${DateFormat.getDateTimeInstance().format(Date(item.updatedAtMillis))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(item.content, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(item.content))
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                    inspectItem = null
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectItem = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun EmptyVaultCard(title: String, subtitle: String) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Rounded.FolderSpecial,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CreationVaultCard(
    item: CreationEntity,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var thumb by remember(item.filePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.filePath) {
        val p = item.filePath
        if (!p.isNullOrBlank()) {
            thumb = withContext(Dispatchers.IO) {
                val f = File(p)
                if (f.exists()) BitmapFactory.decodeFile(p) else null
            }
        }
    }

    GlassCard(
        onClick = onOpen,
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
                    text = item.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Row {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (thumb != null) {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = thumb!!.asImageBitmap(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadHistoryEntity, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(entry.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.mediaType} • ${entry.locationLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove download record", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
