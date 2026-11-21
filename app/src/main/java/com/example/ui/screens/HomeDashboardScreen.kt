package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppBrand
import com.example.data.local.CreationEntity
import com.example.ui.navigation.Routes
import com.example.ui.navigation.ToolDescriptor
import com.example.ui.navigation.ToolRegistry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OfflineStatusBanner

@Composable
fun HomeDashboardScreen(
    displayName: String,
    isSignedIn: Boolean,
    isOnline: Boolean,
    recentToolIds: List<String>,
    savedCreations: List<CreationEntity>,
    onNavigate: (String, String?) -> Unit,
    onOpenProfileSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else ToolRegistry.allTools.filter { tool ->
            tool.title.lowercase().contains(q) ||
                tool.subtitle.lowercase().contains(q) ||
                tool.category.lowercase().contains(q) ||
                tool.keywords.any { it.contains(q) }
        }
    }

    val recentTools = remember(recentToolIds) {
        recentToolIds.mapNotNull { ToolRegistry.byId(it) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar + Welcome Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppBrand.NAME,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isOnline) EmeraldSuccess.copy(alpha = 0.16f) else AmberWarning.copy(alpha = 0.18f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        tint = if (isOnline) EmeraldSuccess else AmberWarning,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isOnline) "Online" else "Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOnline) EmeraldSuccess else AmberWarning
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Welcome, $displayName • ${if (isSignedIn) "Cloud Sync Active" else "Guest Mode (Free)"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenProfileSettings,
                            modifier = Modifier.testTag("home_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "User profile"
                            )
                        }
                        IconButton(
                            onClick = onOpenProfileSettings,
                            modifier = Modifier.testTag("home_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                }
            }

            item {
                OfflineStatusBanner(isOnline = isOnline)
            }

            // Global Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search AI studios, QR, notes, translator, OCR...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("global_search_input")
                )
            }

            // Search Results when querying
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Search Results (${filteredTools.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (filteredTools.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "No tools matched \"$searchQuery\". Try \"image\", \"bangla\", \"qr\", or \"notes\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredTools, key = { it.id }) { tool ->
                        SearchToolRow(
                            tool = tool,
                            onClick = { onNavigate(tool.route, tool.id) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                // Hero Featured Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF17133B), Color(0xFF0E234E), Color(0xFF0A3248))
                                )
                            )
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_hero_wave),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.55f)
                        )
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = NeonCyan.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "FREE AI WORKSPACE • NO PAYWALLS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Create Anything with Gemini",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Conversational AI, multi-language writing, photo & PDF understanding, image studio, voice & storyboards.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    onClick = { onNavigate(Routes.CHAT, "ai_chat") },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    modifier = Modifier.testTag("hero_open_chat_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.ChatBubble,
                                            contentDescription = null,
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "AI Chat",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { onNavigate(Routes.IMAGE_STUDIO, "ai_image") },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                    modifier = Modifier.testTag("hero_open_image_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Image Studio",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Creative Studios Quick Grid (Image, Video, Voice, Writing, Photo, Chat)
                item {
                    SectionHeader(
                        title = "Creative AI Studios",
                        actionLabel = "All Studios",
                        onAction = { onNavigate(Routes.STUDIOS, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StudioShortcutCard(
                                title = "Image Studio",
                                subtitle = "Generate & Analyze",
                                icon = Icons.Rounded.Image,
                                accent = ElectricViolet,
                                testTag = "shortcut_image_studio",
                                onClick = { onNavigate(Routes.IMAGE_STUDIO, "ai_image") },
                                modifier = Modifier.weight(1f)
                            )
                            StudioShortcutCard(
                                title = "Video Studio",
                                subtitle = "Storyboards & Prompts",
                                icon = Icons.Rounded.MovieCreation,
                                accent = ElectricBlue,
                                testTag = "shortcut_video_studio",
                                onClick = { onNavigate(Routes.VIDEO_STUDIO, "ai_video") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StudioShortcutCard(
                                title = "Voice Studio",
                                subtitle = "TTS & Transcription",
                                icon = Icons.Rounded.GraphicEq,
                                accent = NeonCyan,
                                testTag = "shortcut_voice_studio",
                                onClick = { onNavigate(Routes.VOICE_STUDIO, "ai_voice") },
                                modifier = Modifier.weight(1f)
                            )
                            StudioShortcutCard(
                                title = "Writing Studio",
                                subtitle = "11 Modes • 6+ Langs",
                                icon = Icons.Rounded.Create,
                                accent = Color(0xFFA855F7),
                                testTag = "shortcut_writing_studio",
                                onClick = { onNavigate(Routes.WRITING_STUDIO, "ai_writing") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StudioShortcutCard(
                                title = "Photo Vision",
                                subtitle = "OCR & Doc Q&A",
                                icon = Icons.Rounded.ImageSearch,
                                accent = EmeraldSuccess,
                                testTag = "shortcut_photo_studio",
                                onClick = { onNavigate(Routes.PHOTO_STUDIO, "ai_photo") },
                                modifier = Modifier.weight(1f)
                            )
                            StudioShortcutCard(
                                title = "AI Chat",
                                subtitle = "Bangla & English",
                                icon = Icons.Rounded.AutoAwesome,
                                accent = ElectricBlue,
                                testTag = "shortcut_ai_chat",
                                onClick = { onNavigate(Routes.CHAT, "ai_chat") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Recently Used Tools
                if (recentTools.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recently Used Tools")
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentTools, key = { it.id }) { tool ->
                                GlassCard(
                                    onClick = { onNavigate(tool.route, tool.id) },
                                    modifier = Modifier.width(172.dp),
                                    accentColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.title,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tool.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Offline Toolbox Highlights
                item {
                    SectionHeader(
                        title = "Offline Creative Toolbox",
                        actionLabel = "Open All 9",
                        onAction = { onNavigate(Routes.TOOLBOX, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ToolRegistry.offlineTools, key = { it.id }) { tool ->
                            GlassCard(
                                onClick = { onNavigate(tool.route, tool.id) },
                                modifier = Modifier.width(164.dp),
                                accentColor = MaterialTheme.colorScheme.tertiary
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.title,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = EmeraldSuccess.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "Offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldSuccess,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tool.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Saved Creations Preview
                item {
                    SectionHeader(
                        title = "Saved Creations (${savedCreations.size})",
                        actionLabel = "Open Vault",
                        onAction = { onNavigate(Routes.VAULT, "vault") }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (savedCreations.isEmpty()) {
                        GlassCard(
                            onClick = { onNavigate(Routes.WRITING_STUDIO, "ai_writing") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Your Saved Creations Vault is empty.",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Generate writing, images, storyboards, or photo summaries and tap Save to store them here offline & optionally sync with Google.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(savedCreations.take(6), key = { it.id }) { item ->
                                GlassCard(
                                    onClick = { onNavigate(Routes.VAULT, "vault") },
                                    modifier = Modifier.width(210.dp)
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
                                        if (item.isFavorite) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = "Favorite",
                                                tint = AmberWarning,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
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
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StudioShortcutCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        accentColor = accent,
        modifier = modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchToolRow(
    tool: ToolDescriptor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open ${tool.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
