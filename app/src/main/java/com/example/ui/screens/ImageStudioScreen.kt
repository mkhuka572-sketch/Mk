package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ImageSearch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.OfflineStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data class Loading(val label: String) : ImageStudioUiState
    data class GeneratedSuccess(val bitmap: Bitmap, val usedPrompt: String) : ImageStudioUiState
    data class AnalysisSuccess(val analysis: String, val usedPrompt: String) : ImageStudioUiState
    data class Error(val message: String, val allowPromptFallback: Boolean = true) : ImageStudioUiState
}

class ImageStudioViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    val imageHistory: StateFlow<List<CreationEntity>> = container.localRepository.observeCreations()
        .map { list -> list.filter { it.type == "IMAGE" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enhancePrompt(rawPrompt: String, style: String, onEnhanced: (String) -> Unit) {
        if (rawPrompt.isBlank()) {
            _uiState.value = ImageStudioUiState.Error("Enter a short idea first to enhance it.", false)
            return
        }
        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading("Enhancing prompt with Gemini...")
            try {
                val enhanced = container.geminiAiService.generateText(
                    "Enhance this image generation prompt into a vivid, detailed single-paragraph visual prompt in $style style. " +
                        "Include lighting, composition, lens/medium, and mood. Return only the enhanced prompt.\n\nIdea: $rawPrompt"
                )
                onEnhanced(enhanced.trim())
                _uiState.value = ImageStudioUiState.Idle
            } catch (e: TimeoutCancellationException) {
                _uiState.value = ImageStudioUiState.Error("Prompt enhancement timed out.", false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ImageStudioVM", "Enhance failed", e)
                _uiState.value = ImageStudioUiState.Error(e.localizedMessage ?: "Failed to enhance prompt.", false)
            }
        }
    }

    fun generateImage(prompt: String, style: String, referenceBitmap: Bitmap?) {
        val clean = prompt.trim()
        if (clean.isEmpty()) {
            _uiState.value = ImageStudioUiState.Error("Please enter a prompt.", false)
            return
        }
        val styledPrompt = if (style == "None") clean else "$clean, $style aesthetic, crisp detail"
        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading("Synthesizing image with Gemini Nano Banana (30-60s)...")
            try {
                val bitmap = container.geminiAiService.generateImage(styledPrompt, referenceBitmap)
                _uiState.value = ImageStudioUiState.GeneratedSuccess(bitmap, styledPrompt)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = ImageStudioUiState.Error(
                    "Image synthesis timed out after 60 seconds. You can retry or run AI Prompt Craft / Analysis instead."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ImageStudioVM", "Image generation failed", e)
                _uiState.value = ImageStudioUiState.Error(
                    "Image model unavailable or quota/billing limit reached: ${e.localizedMessage ?: "Generation failed"}. " +
                        "Note: Gemini image generation requires Firebase Blaze billing. You can still use AI Image Analysis & Prompt Craft below!"
                )
            }
        }
    }

    fun analyzeImageOrCraftPrompt(prompt: String, referenceBitmap: Bitmap?) {
        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading("Running AI visual analysis & art direction...")
            try {
                val text = if (referenceBitmap != null) {
                    container.geminiAiService.analyzeBitmap(
                        referenceBitmap,
                        "Analyze this image's composition, lighting, color palette, and subject. " +
                            if (prompt.isNotBlank()) "Also address: $prompt" else "Also suggest 3 creative remix prompts."
                    )
                } else {
                    container.geminiAiService.generateText(
                        "Create a complete Art Direction & Visual Prompt Blueprint for: \"$prompt\". " +
                            "Include: 1) Refined Generation Prompt, 2) Negative Prompt, 3) Color Palette Hexes, 4) Lighting & Composition."
                    )
                }
                _uiState.value = ImageStudioUiState.AnalysisSuccess(text, prompt)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = ImageStudioUiState.Error("Analysis timed out.", false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ImageStudioVM", "Analysis failed", e)
                _uiState.value = ImageStudioUiState.Error(e.localizedMessage ?: "Analysis failed.", false)
            }
        }
    }

    fun saveGeneratedImage(bitmap: Bitmap, prompt: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val path = ExportUtils.saveBitmapToInternalFile(container.appContext, bitmap, "gen_img")
            container.saveCreationAndSyncIfSignedIn(
                type = "IMAGE",
                title = prompt.take(40).ifBlank { "AI Artwork" },
                content = prompt,
                prompt = prompt,
                filePath = path
            )
            onDone(path)
        }
    }

    fun recordDownload(filename: String, location: String) {
        viewModelScope.launch {
            container.localRepository.recordDownload(filename, "image/png", location)
        }
    }

    fun saveTextAnalysis(title: String, analysis: String, prompt: String, onDone: () -> Unit) {
        viewModelScope.launch {
            container.saveCreationAndSyncIfSignedIn(
                type = "IMAGE",
                title = title,
                content = analysis,
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
                    ImageStudioViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    container: AppContainer,
    isOnline: Boolean,
    initialPrompt: String = "",
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val vm: ImageStudioViewModel = viewModel(factory = ImageStudioViewModel.factory(container))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val history by vm.imageHistory.collectAsStateWithLifecycle()

    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var selectedStyle by remember { mutableStateOf("Cyberpunk Neon") }
    var uploadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val styles = listOf(
        "Cyberpunk Neon",
        "Photorealistic 8K",
        "3D Isometric",
        "Watercolor Ink",
        "Minimalist Vector",
        "Studio Product",
        "Anime Concept",
        "None"
    )

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = GeminiAiService.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    uploadedBitmap = GeminiAiService.downscaleBitmap(bmp, 1280)
                } else {
                    snackbarHostState.showSnackbar("Unable to load selected image.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Image Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("image_back_button")) {
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

                // Style Selection
                Text("Creative Style", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(styles) { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style) }
                        )
                    }
                }

                // Optional Reference Image Upload
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("image_upload_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uploadedBitmap == null) "Upload Image (Optional)" else "Change Image")
                    }
                    if (uploadedBitmap != null) {
                        TextButton(onClick = { uploadedBitmap = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }

                if (uploadedBitmap != null) {
                    Image(
                        bitmap = uploadedBitmap!!.asImageBitmap(),
                        contentDescription = "Uploaded reference image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe your image or edit instruction") },
                    placeholder = { Text("e.g., Floating glass library over neon ocean at twilight") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            vm.enhancePrompt(prompt, selectedStyle) { enhanced ->
                                prompt = enhanced
                            }
                        },
                        enabled = isOnline && prompt.isNotBlank() && uiState !is ImageStudioUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("image_enhance_button")
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enhance Prompt")
                    }

                    OutlinedButton(
                        onClick = { vm.analyzeImageOrCraftPrompt(prompt, uploadedBitmap) },
                        enabled = isOnline && (prompt.isNotBlank() || uploadedBitmap != null) && uiState !is ImageStudioUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("image_analyze_button")
                    ) {
                        Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uploadedBitmap != null) "Analyze Photo" else "Prompt Craft")
                    }
                }

                AuroraButton(
                    text = if (uploadedBitmap != null) "Remix / Edit Image with AI" else "Generate Image with AI",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = isOnline && prompt.isNotBlank() && uiState !is ImageStudioUiState.Loading,
                    testTag = "image_generate_button",
                    onClick = { vm.generateImage(prompt, selectedStyle, uploadedBitmap) },
                    modifier = Modifier.fillMaxWidth()
                )

                when (val state = uiState) {
                    is ImageStudioUiState.Loading -> AiPulseLoader(state.label)
                    is ImageStudioUiState.GeneratedSuccess -> {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "AI generated artwork",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        vm.saveGeneratedImage(state.bitmap, state.usedPrompt) {
                                            scope.launch { snackbarHostState.showSnackbar("Saved to Vault & History") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = ExportUtils.exportBitmapToGallery(
                                                context,
                                                state.bitmap,
                                                "ProjectX_Art"
                                            )
                                            res.onSuccess { name ->
                                                vm.recordDownload(name, "Pictures/ProjectX")
                                                snackbarHostState.showSnackbar("Downloaded: $name")
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar(err.localizedMessage ?: "Download failed")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            ExportUtils.shareBitmap(context, state.bitmap, "MK Assist Artwork")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                    is ImageStudioUiState.AnalysisSuccess -> {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text("AI Visual Analysis & Prompt Blueprint", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.analysis, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.saveTextAnalysis(
                                        title = "Art Blueprint: ${state.usedPrompt.take(24)}",
                                        analysis = state.analysis,
                                        prompt = state.usedPrompt
                                    ) {
                                        scope.launch { snackbarHostState.showSnackbar("Saved to Vault") }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Blueprint")
                            }
                        }
                    }
                    is ImageStudioUiState.Error -> {
                        ErrorBanner(
                            message = state.message,
                            onRetry = if (state.allowPromptFallback && isOnline) {
                                { vm.analyzeImageOrCraftPrompt(prompt, uploadedBitmap) }
                            } else null
                        )
                    }
                    ImageStudioUiState.Idle -> Unit
                }

                // Image History
                if (history.isNotEmpty()) {
                    Text("Image Studio History (${history.size})", style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(history.take(10), key = { it.id }) { item ->
                            var thumb by remember(item.filePath) { mutableStateOf<Bitmap?>(null) }
                            androidx.compose.runtime.LaunchedEffect(item.filePath) {
                                val path = item.filePath
                                if (!path.isNullOrBlank()) {
                                    thumb = withContext(Dispatchers.IO) {
                                        val f = File(path)
                                        if (f.exists()) BitmapFactory.decodeFile(path) else null
                                    }
                                }
                            }
                            GlassCard(
                                modifier = Modifier
                                    .width(156.dp)
                                    .clickable { prompt = item.prompt.ifBlank { item.title } }
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb!!.asImageBitmap(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(92.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
