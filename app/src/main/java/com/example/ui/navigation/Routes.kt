package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val STUDIOS = "studios"
    const val TOOLBOX = "toolbox"
    const val VAULT = "vault"

    const val IMAGE_STUDIO = "image_studio"
    const val VIDEO_STUDIO = "video_studio"
    const val VOICE_STUDIO = "voice_studio"
    const val WRITING_STUDIO = "writing_studio"
    const val PHOTO_STUDIO = "photo_studio"
    const val PROFILE_SETTINGS = "profile_settings"
    const val TOOLBOX_TOOL = "toolbox_tool/{toolId}"

    fun toolboxTool(toolId: String) = "toolbox_tool/$toolId"
}

data class ToolDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val route: String,
    val icon: ImageVector,
    val isOfflineReady: Boolean,
    val keywords: List<String> = emptyList()
)

object ToolRegistry {
    val allTools: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            id = "ai_chat",
            title = "AI Chat Assistant",
            subtitle = "Multi-session Gemini chat in Bangla, English & more",
            category = "AI Studio",
            route = Routes.CHAT,
            icon = Icons.Rounded.ChatBubble,
            isOfflineReady = false,
            keywords = listOf("chat", "assistant", "bangla", "gemini", "conversation")
        ),
        ToolDescriptor(
            id = "ai_image",
            title = "AI Image Studio",
            subtitle = "Generate, remix, enhance prompts & analyze images",
            category = "AI Studio",
            route = Routes.IMAGE_STUDIO,
            icon = Icons.Rounded.Image,
            isOfflineReady = false,
            keywords = listOf("image", "art", "picture", "draw", "generate", "prompt")
        ),
        ToolDescriptor(
            id = "ai_video",
            title = "AI Video Studio",
            subtitle = "Storyboards, shot planners, video prompts & ideas",
            category = "AI Studio",
            route = Routes.VIDEO_STUDIO,
            icon = Icons.Rounded.MovieCreation,
            isOfflineReady = false,
            keywords = listOf("video", "storyboard", "reel", "youtube", "script", "motion")
        ),
        ToolDescriptor(
            id = "ai_voice",
            title = "AI Voice Studio",
            subtitle = "Multi-language TTS, audio export & speech transcription",
            category = "AI Studio",
            route = Routes.VOICE_STUDIO,
            icon = Icons.Rounded.GraphicEq,
            isOfflineReady = true,
            keywords = listOf("voice", "tts", "audio", "speech", "transcribe", "wav")
        ),
        ToolDescriptor(
            id = "ai_writing",
            title = "AI Writing Studio",
            subtitle = "Captions, emails, stories, translation & grammar",
            category = "AI Studio",
            route = Routes.WRITING_STUDIO,
            icon = Icons.Rounded.Create,
            isOfflineReady = false,
            keywords = listOf("write", "caption", "facebook", "instagram", "youtube", "email", "translate", "grammar")
        ),
        ToolDescriptor(
            id = "ai_photo",
            title = "AI Photo Understanding",
            subtitle = "OCR text extraction, photo Q&A & doc summaries",
            category = "AI Studio",
            route = Routes.PHOTO_STUDIO,
            icon = Icons.Rounded.ImageSearch,
            isOfflineReady = false,
            keywords = listOf("photo", "ocr", "scan", "document", "pdf", "screenshot", "extract")
        ),
        ToolDescriptor(
            id = "tool_qr_gen",
            title = "QR Code Generator",
            subtitle = "Create custom QR PNGs offline",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("qr_gen"),
            icon = Icons.Rounded.QrCode2,
            isOfflineReady = true,
            keywords = listOf("qr", "barcode", "link", "share")
        ),
        ToolDescriptor(
            id = "tool_qr_scan",
            title = "QR Code Scanner",
            subtitle = "Live camera & gallery QR scanner",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("qr_scan"),
            icon = Icons.Rounded.QrCodeScanner,
            isOfflineReady = true,
            keywords = listOf("scan", "camera", "qr")
        ),
        ToolDescriptor(
            id = "tool_calculator",
            title = "Calculator",
            subtitle = "Scientific expression calculator with history",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("calculator"),
            icon = Icons.Rounded.Calculate,
            isOfflineReady = true,
            keywords = listOf("math", "calc", "scientific")
        ),
        ToolDescriptor(
            id = "tool_unit",
            title = "Unit Converter",
            subtitle = "Length, weight, temp, data, speed & area",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("unit"),
            icon = Icons.Rounded.Straighten,
            isOfflineReady = true,
            keywords = listOf("convert", "units", "metric", "imperial")
        ),
        ToolDescriptor(
            id = "tool_word_count",
            title = "Word & Char Counter",
            subtitle = "Counts words, characters & reading time",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("word_count"),
            icon = Icons.Rounded.TextFields,
            isOfflineReady = true,
            keywords = listOf("word", "character", "counter", "reading time")
        ),
        ToolDescriptor(
            id = "tool_formatter",
            title = "Text Formatter",
            subtitle = "Case transform, clean spaces, sort & slugify",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("formatter"),
            icon = Icons.Rounded.TextFormat,
            isOfflineReady = true,
            keywords = listOf("format", "case", "uppercase", "slug", "sort")
        ),
        ToolDescriptor(
            id = "tool_palette",
            title = "Color Palette Generator",
            subtitle = "Harmonic color schemes with hex copy",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("palette"),
            icon = Icons.Rounded.ColorLens,
            isOfflineReady = true,
            keywords = listOf("color", "palette", "hex", "design")
        ),
        ToolDescriptor(
            id = "tool_prompt",
            title = "Prompt Generator",
            subtitle = "Offline structured creative prompt builder",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("prompt"),
            icon = Icons.Rounded.TipsAndUpdates,
            isOfflineReady = true,
            keywords = listOf("prompt", "template", "creative", "idea")
        ),
        ToolDescriptor(
            id = "tool_notes",
            title = "Notes",
            subtitle = "Offline-first pinned creative notes",
            category = "Offline Toolbox",
            route = Routes.toolboxTool("notes"),
            icon = Icons.Rounded.Notes,
            isOfflineReady = true,
            keywords = listOf("notes", "memo", "draft", "journal")
        ),
        ToolDescriptor(
            id = "vault",
            title = "Saved Creations & Vault",
            subtitle = "Favorites, exports & download history",
            category = "Library",
            route = Routes.VAULT,
            icon = Icons.Rounded.FolderSpecial,
            isOfflineReady = true,
            keywords = listOf("saved", "vault", "favorites", "downloads", "history")
        )
    )

    val aiStudios = allTools.filter { it.category == "AI Studio" }
    val offlineTools = allTools.filter { it.category == "Offline Toolbox" }

    fun byId(id: String): ToolDescriptor? = allTools.firstOrNull { it.id == id }
}

data class TopTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val mainTopTabs = listOf(
    TopTab(Routes.HOME, "Home", Icons.Rounded.Home),
    TopTab(Routes.CHAT, "Chat", Icons.Rounded.ChatBubble),
    TopTab(Routes.STUDIOS, "Studios", Icons.Rounded.AutoAwesome),
    TopTab(Routes.TOOLBOX, "Toolbox", Icons.Rounded.Description),
    TopTab(Routes.VAULT, "Vault", Icons.Rounded.FolderSpecial)
)
