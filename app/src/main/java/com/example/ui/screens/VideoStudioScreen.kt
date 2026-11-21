package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppContainer
import com.example.core.ExportUtils
import com.example.data.firebase.GeminiAiService
import com.example.data.local.CreationEntity
import com.example.ui.theme.AiPulseLoader
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.OfflineStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class VideoStudioViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextGenUiState>(TextGenUiState.Idle)
    val uiState: StateFlow<TextGenUiState> = _uiState.asStateFlow()

    val videoHistory: StateFlow<List<CreationEntity>> = container.localRepository.observeCreations()
        .map { list -> list.filter { it.type == "VIDEO_PLAN" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateStoryboard(
        workflow: String,
        concept: String,
        durationSec: String,
        aspectRatio: String,
        referenceBitmap: Bitmap?
    ) {
        val clean = concept.trim()
        if (clean.isEmpty() && referenceBitmap == null) {
            _uiState.value = TextGenUiState.Error("Enter a video concept or attach a keyframe photo.")
            return
        }
        val prompt = """
            Workflow: $workflow
            Target Duration: $durationSec seconds | Aspect Ratio: $aspectRatio
            Concept: ${clean.ifBlank { "Animate the attached keyframe photo into a cinematic short clip." }}
            
            Produce a production-ready Video Plan containing:
            1. Title & Hook (first 3 seconds)
            2. Shot-by-Shot Storyboard (Timecode, Visual Action, Camera Motion, Lighting)
            3. Ready-to-Use AI Video Generation Prompts (optimized for Veo / Runway / Sora)
            4. Voiceover & Sound Design Cues
        """.trimIndent()

        viewModelScope.launch {
            _uiState.value = TextGenUiState.Loading
            try {
                if (referenceBitmap != null) {
                    val result = container.geminiAiService.analyzeBitmap(referenceBitmap, prompt)
                    _uiState.value = TextGenUiState.Success(result)
                } else {
                    val buffer = StringBuilder()
                    withTimeout(60_000L) {
                        container.geminiAiService.streamText(prompt).collect { chunk ->
                            buffer.append(chunk)
                            _uiState.value = TextGenUiState.Streaming(buffer.toString())
                        }
                    }
                    val result = buffer.toString().trim()
                    _uiState.value = if (result.isNotBlank()) {
                        TextGenUiState.Success(result)
                    } else {
                        TextGenUiState.Error("Gemini returned an empty storyboard.")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = TextGenUiState.Error("Video storyboard generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VideoVM", "Storyboard failed", e)
                _uiState.value = TextGenUiState.Error(e.localizedMessage ?: "Failed to generate video plan.")
            }
        }
    }

    fun generateVideoIdeas(niche: String) {
        val clean = niche.trim().ifBlank { "Global technology and creative lifestyle" }
        viewModelScope.launch {
            _uiState.value = TextGenUiState.Loading
            try {
                val result = container.geminiAiService.generateText(
                    "Generate 6 high-retention short & long-form video ideas for \"$clean\". " +
                        "For each idea include: Title, Opening Hook, Visual Style, and Recommended Prompt."
                )
                _uiState.value = TextGenUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = TextGenUiState.Error("Idea generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TextGenUiState.Error(e.localizedMessage ?: "Idea generation failed.")
            }
        }
    }

    fun saveProject(title: String, content: String, prompt: String, onDone: () -> Unit) {
        viewModelScope.launch {
            container.saveCreationAndSyncIfSignedIn(
                type = "VIDEO_PLAN",
                title = title,
                content = content,
                prompt = prompt
            )
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    VideoStudioViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStudioScreen(
    container: AppContainer,
    isOnline: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val vm: VideoStudioViewModel = viewModel(factory = VideoStudioViewModel.factory(container))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val history by vm.videoHistory.collectAsStateWithLifecycle()

    var workflow by remember { mutableStateOf("Text-to-Video Plan") }
    var concept by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("15") }
    var aspect by remember { mutableStateOf("9:16 Reels/Shorts") }
    var keyframeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = GeminiAiService.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    keyframeBitmap = GeminiAiService.downscaleBitmap(bmp, 1280)
                    workflow = "Image-to-Video Motion Plan"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Video Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("video_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OfflineStatusBanner(isOnline = isOnline)

                // Honest transparency card (never pretend static images are rendered videos)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElectricBlue.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = ElectricBlue)
                        Column {
                            Text(
                                text = "Video Planning & Storyboard Mode",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Direct MP4 video rendering is not available on this free mobile client. Use this workspace to craft frame-by-frame storyboards, Image-to-Video motion scripts, and copyable Veo/Runway prompts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Text-to-Video Plan", "Image-to-Video Motion Plan")) { mode ->
                        FilterChip(
                            selected = workflow == mode,
                            onClick = { workflow = mode },
                            label = { Text(mode) }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("9:16 Reels/Shorts", "16:9 YouTube", "1:1 Square")) { ratio ->
                        FilterChip(
                            selected = aspect == ratio,
                            onClick = { aspect = ratio },
                            label = { Text(ratio) }
                        )
                    }
                    items(listOf("15", "30", "60")) { sec ->
                        FilterChip(
                            selected = duration == sec,
                            onClick = { duration = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("video_pick_keyframe_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (keyframeBitmap == null) "Attach Keyframe Image" else "Change Keyframe")
                    }
                    if (keyframeBitmap != null) {
                        TextButton(onClick = { keyframeBitmap = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Clear")
                        }
                    }
                }

                if (keyframeBitmap != null) {
                    Image(
                        bitmap = keyframeBitmap!!.asImageBitmap(),
                        contentDescription = "Keyframe image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                OutlinedTextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Video concept, topic, or motion direction") },
                    placeholder = { Text("e.g., Drone flyover of Dhaka sunrise transitioning into futuristic tech studio") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_concept_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.generateVideoIdeas(concept) },
                        enabled = isOnline && uiState !is TextGenUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("video_ideas_button")
                    ) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Video Ideas")
                    }

                    AuroraButton(
                        text = "Build Storyboard",
                        icon = Icons.Rounded.AutoAwesome,
                        enabled = isOnline && uiState !is TextGenUiState.Loading,
                        testTag = "video_generate_button",
                        onClick = {
                            vm.generateStoryboard(workflow, concept, duration, aspect, keyframeBitmap)
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                }

                when (val state = uiState) {
                    is TextGenUiState.Loading -> AiPulseLoader("Planning shots & prompts...")
                    is TextGenUiState.Streaming -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(state.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    is TextGenUiState.Success -> {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text("Storyboard & Video Prompts", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.text, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(state.text))
                                        scope.launch { snackbarHostState.showSnackbar("Copied storyboard") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        vm.saveProject(
                                            title = concept.take(36).ifBlank { "Video Storyboard" },
                                            content = state.text,
                                            prompt = concept
                                        ) {
                                            scope.launch { snackbarHostState.showSnackbar("Saved to Vault") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = { ExportUtils.shareText(context, "MK Assist Storyboard", state.text) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Export")
                                }
                            }
                        }
                    }
                    is TextGenUiState.Error -> ErrorBanner(message = state.message)
                    TextGenUiState.Idle -> Unit
                }

                if (history.isNotEmpty()) {
                    Text("Saved Video Projects (${history.size})", style = MaterialTheme.typography.titleMedium)
                    history.take(5).forEach { project ->
                        GlassCard(
                            onClick = { concept = project.prompt.ifBlank { project.title } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                project.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
