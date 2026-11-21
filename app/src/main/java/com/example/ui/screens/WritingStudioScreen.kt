package com.example.ui.screens

import android.util.Log
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.core.AppLanguages
import com.example.core.ExportUtils
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
import kotlinx.coroutines.withTimeout

enum class WritingMode(val label: String, val instruction: String) {
    SOCIAL_CAPTIONS("Social Captions", "Write 5 engaging social media captions with hooks and emojis"),
    FACEBOOK_POST("Facebook Post", "Write a compelling, shareable Facebook post with a strong opening and call-to-action"),
    INSTAGRAM_IDEAS("Instagram Ideas", "Generate 5 Instagram carousel/reel content ideas with visual hooks and captions"),
    YOUTUBE_META("YouTube Title & Desc", "Create 5 clickable YouTube titles, an SEO-rich description, and relevant tags"),
    PRO_EMAIL("Professional Email", "Write a polished, clear, professional email with subject line"),
    CREATIVE_STORY("Creative Story", "Write an imaginative, vivid short creative story"),
    TRANSLATION("Translation", "Translate accurately while preserving tone and natural phrasing"),
    GRAMMAR("Grammar Fix", "Fix grammar, spelling, punctuation, and clarity while explaining key improvements"),
    SUMMARIZE("Summarize", "Summarize the input clearly into key takeaways and bullet points"),
    REWRITE("Rewrite", "Rewrite the text to make it more engaging, fluent, and impactful"),
    HASHTAGS("Hashtag Suggestions", "Generate 20 well-balanced niche and trending hashtags grouped by reach")
}

sealed interface TextGenUiState {
    data object Idle : TextGenUiState
    data object Loading : TextGenUiState
    data class Streaming(val text: String) : TextGenUiState
    data class Success(val text: String) : TextGenUiState
    data class Error(val message: String) : TextGenUiState
}

class WritingStudioViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextGenUiState>(TextGenUiState.Idle)
    val uiState: StateFlow<TextGenUiState> = _uiState.asStateFlow()

    fun generate(mode: WritingMode, topicOrText: String, targetLanguageCode: String, tone: String) {
        val clean = topicOrText.trim()
        if (clean.isEmpty()) {
            _uiState.value = TextGenUiState.Error("Please enter a topic or text first.")
            return
        }
        val lang = AppLanguages.byCode(targetLanguageCode)
        val prompt = """
            Task: ${mode.instruction}
            Output Language: ${lang.label} (${lang.nativeName})
            Tone: $tone
            
            User Input:
            $clean
        """.trimIndent()

        viewModelScope.launch {
            _uiState.value = TextGenUiState.Loading
            val buffer = StringBuilder()
            try {
                withTimeout(60_000L) {
                    container.geminiAiService.streamText(prompt).collect { chunk ->
                        buffer.append(chunk)
                        _uiState.value = TextGenUiState.Streaming(buffer.toString())
                    }
                }
                val result = buffer.toString().trim()
                if (result.isNotBlank()) {
                    _uiState.value = TextGenUiState.Success(result)
                } else {
                    _uiState.value = TextGenUiState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("WritingVM", "Timeout", e)
                _uiState.value = TextGenUiState.Error("Generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WritingVM", "Failed", e)
                _uiState.value = TextGenUiState.Error(e.localizedMessage ?: "Writing generation failed.")
            }
        }
    }

    fun saveResult(title: String, content: String, prompt: String, langCode: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            container.saveCreationAndSyncIfSignedIn(
                type = "WRITING",
                title = title,
                content = content,
                prompt = prompt,
                language = langCode
            )
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WritingStudioViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingStudioScreen(
    container: AppContainer,
    isOnline: Boolean,
    defaultLanguageCode: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSendToVoice: (String) -> Unit
) {
    val vm: WritingStudioViewModel = viewModel(factory = WritingStudioViewModel.factory(container))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(WritingMode.SOCIAL_CAPTIONS) }
    var selectedLang by remember(defaultLanguageCode) { mutableStateOf(defaultLanguageCode) }
    var selectedTone by remember { mutableStateOf("Engaging") }
    var inputText by remember { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tones = listOf("Engaging", "Professional", "Friendly", "Poetic", "Concise", "Persuasive")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Writing Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("writing_back_button")) {
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

                Text("Writing Tool", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WritingMode.entries) { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Text("Target Language", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppLanguages.all) { lang ->
                        FilterChip(
                            selected = selectedLang == lang.code,
                            onClick = { selectedLang = lang.code },
                            label = { Text("${lang.nativeName} (${lang.label})") }
                        )
                    }
                }

                Text("Tone", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tones) { tone ->
                        FilterChip(
                            selected = selectedTone == tone,
                            onClick = { selectedTone = tone },
                            label = { Text(tone) }
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Topic, prompt, or text to transform") },
                    placeholder = { Text("e.g., Launch post for our free community art project...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("writing_input")
                )

                AuroraButton(
                    text = "Generate ${selectedMode.label}",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = isOnline && uiState !is TextGenUiState.Loading,
                    testTag = "writing_generate_button",
                    onClick = { vm.generate(selectedMode, inputText, selectedLang, selectedTone) },
                    modifier = Modifier.fillMaxWidth()
                )

                when (val state = uiState) {
                    is TextGenUiState.Loading -> AiPulseLoader("Writing with Gemini...")
                    is TextGenUiState.Streaming -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Generating...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            Text(state.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    is TextGenUiState.Success -> {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "${selectedMode.label} Output",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(state.text))
                                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        vm.saveResult(
                                            title = "${selectedMode.label}: ${inputText.take(28)}",
                                            content = state.text,
                                            prompt = inputText,
                                            langCode = selectedLang
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
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { ExportUtils.shareText(context, selectedMode.label, state.text) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                                OutlinedButton(
                                    onClick = { onSendToVoice(state.text) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Speak")
                                }
                            }
                        }
                    }
                    is TextGenUiState.Error -> {
                        ErrorBanner(
                            message = state.message,
                            onRetry = { vm.generate(selectedMode, inputText, selectedLang, selectedTone) }
                        )
                    }
                    TextGenUiState.Idle -> Unit
                }
            }
        }
    }
}
