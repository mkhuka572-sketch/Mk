package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import com.example.data.firebase.GeminiAiService
import com.example.ui.theme.AiPulseLoader
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.OfflineStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PhotoUiState {
    data object Idle : PhotoUiState
    data object Loading : PhotoUiState
    data class Success(val result: String) : PhotoUiState
    data class Error(val message: String) : PhotoUiState
}

class PhotoViewModel(
    private val container: AppContainer
) : ViewModel() {
    private val _uiState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    fun analyzePhoto(bitmap: Bitmap, question: String) {
        viewModelScope.launch {
            _uiState.value = PhotoUiState.Loading
            try {
                val answer = container.geminiAiService.analyzeBitmap(
                    bitmap = bitmap,
                    instruction = question.ifBlank { "Describe this photo in detail and extract any visible text." }
                )
                _uiState.value = PhotoUiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = PhotoUiState.Error("Analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PhotoVM", "Photo analysis failed", e)
                _uiState.value = PhotoUiState.Error(e.localizedMessage ?: "Unable to analyze photo.")
            }
        }
    }

    fun analyzeDocument(bytes: ByteArray, mimeType: String, instruction: String) {
        viewModelScope.launch {
            _uiState.value = PhotoUiState.Loading
            try {
                val answer = container.geminiAiService.analyzeDocumentBytes(
                    bytes = bytes,
                    mimeType = mimeType,
                    instruction = instruction.ifBlank { "Summarize this document clearly and extract key points." }
                )
                _uiState.value = PhotoUiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = PhotoUiState.Error("Document analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PhotoVM", "Doc analysis failed", e)
                _uiState.value = PhotoUiState.Error(e.localizedMessage ?: "Unable to analyze document.")
            }
        }
    }

    fun saveAnalysis(title: String, content: String, prompt: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            container.saveCreationAndSyncIfSignedIn(
                type = "PHOTO_QA",
                title = title,
                content = content,
                prompt = prompt
            )
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhotoViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUnderstandingScreen(
    container: AppContainer,
    isOnline: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val vm: PhotoViewModel = viewModel(factory = PhotoViewModel.factory(container))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var docBytes by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var docLabel by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("Extract all visible text and explain what is shown.") }

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
                    selectedBitmap = GeminiAiService.downscaleBitmap(bmp, 1440)
                    docBytes = null
                    docLabel = null
                } else {
                    snackbarHostState.showSnackbar("Failed to load selected photo.")
                }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val result = GeminiAiService.readBytesFromUri(context, uri)
                    if (result != null) {
                        docBytes = result
                        docLabel = "Document selected (${result.second}, ${result.first.size / 1024} KB)"
                        selectedBitmap = null
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to read document.")
                }
            }
        }
    }

    val quickActions = listOf(
        "Extract Text (OCR)" to "Extract all text from this image/document verbatim, preserving structure.",
        "Summarize Document" to "Provide a concise bulleted summary of key facts, dates, and action items.",
        "Explain Screenshot" to "Explain what this UI/screenshot shows and suggest next steps.",
        "Describe & Tag" to "Describe this visual scene in detail and list 10 descriptive tags."
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Photo & Doc Vision") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("photo_back_button")) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_pick_image_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            documentPicker.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_pick_doc_button")
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick PDF / Doc")
                    }
                }

                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else if (docLabel != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(docLabel!!, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ready for Gemini multimodal document analysis (under 20 MB limit).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No photo or document selected yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Upload a photo, screenshot, receipt, or PDF (under 20 MB) to extract text (OCR), ask questions, or summarize.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Quick Presets", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickActions) { (label, prompt) ->
                        FilterChip(
                            selected = question == prompt,
                            onClick = { question = prompt },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask a question or instruction") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("photo_question_input")
                )

                val hasInput = selectedBitmap != null || docBytes != null
                AuroraButton(
                    text = "Analyze with Gemini Vision",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = isOnline && hasInput && uiState !is PhotoUiState.Loading,
                    testTag = "photo_analyze_button",
                    onClick = {
                        val bmp = selectedBitmap
                        val doc = docBytes
                        if (bmp != null) {
                            vm.analyzePhoto(bmp, question)
                        } else if (doc != null) {
                            vm.analyzeDocument(doc.first, doc.second, question)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                when (val state = uiState) {
                    is PhotoUiState.Loading -> AiPulseLoader("Analyzing visual & document content...")
                    is PhotoUiState.Success -> {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text("Vision Analysis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            Text(state.result, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(state.result))
                                        scope.launch { snackbarHostState.showSnackbar("Copied analysis") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        vm.saveAnalysis("Photo/Doc Insight", state.result, question) {
                                            scope.launch { snackbarHostState.showSnackbar("Saved to Vault") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }
                    is PhotoUiState.Error -> ErrorBanner(message = state.message)
                    PhotoUiState.Idle -> Unit
                }
            }
        }
    }
}
