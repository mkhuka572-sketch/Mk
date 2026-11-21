package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppContainer
import com.example.core.AppLanguages
import com.example.core.ExportUtils
import com.example.data.firebase.GeminiAiService
import com.example.ui.theme.AiPulseLoader
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.OfflineStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class VoiceStudioViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _transcriptionState = MutableStateFlow<TextGenUiState>(TextGenUiState.Idle)
    val transcriptionState: StateFlow<TextGenUiState> = _transcriptionState.asStateFlow()

    fun generateVoiceoverScript(topic: String, languageCode: String, onScriptReady: (String) -> Unit) {
        val lang = AppLanguages.byCode(languageCode)
        viewModelScope.launch {
            _transcriptionState.value = TextGenUiState.Loading
            try {
                val script = container.geminiAiService.generateText(
                    "Write a natural, expressive 45-second voiceover script in ${lang.label} (${lang.nativeName}) about: " +
                        "${topic.ifBlank { "the power of global free creativity" }}. Return only the spoken script."
                )
                onScriptReady(script.trim())
                _transcriptionState.value = TextGenUiState.Idle
            } catch (e: TimeoutCancellationException) {
                _transcriptionState.value = TextGenUiState.Error("Script generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transcriptionState.value = TextGenUiState.Error(e.localizedMessage ?: "Failed to generate script.")
            }
        }
    }

    fun transcribeAudioBytes(bytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            _transcriptionState.value = TextGenUiState.Loading
            try {
                val transcript = container.geminiAiService.analyzeDocumentBytes(
                    bytes = bytes,
                    mimeType = mimeType,
                    instruction = "Transcribe this audio accurately in its original language, then provide a brief English summary."
                )
                _transcriptionState.value = TextGenUiState.Success(transcript)
            } catch (e: TimeoutCancellationException) {
                _transcriptionState.value = TextGenUiState.Error("Audio transcription timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VoiceVM", "Audio transcription failed", e)
                _transcriptionState.value = TextGenUiState.Error(e.localizedMessage ?: "Audio transcription failed.")
            }
        }
    }

    fun recordAudioExport(filename: String, location: String) {
        viewModelScope.launch {
            container.localRepository.recordDownload(filename, "audio/wav", location)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    VoiceStudioViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(
    container: AppContainer,
    isOnline: Boolean,
    initialScript: String = "",
    defaultLanguageCode: String = "en",
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val vm: VoiceStudioViewModel = viewModel(factory = VoiceStudioViewModel.factory(container))
    val transcriptionState by vm.transcriptionState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var textToSpeak by remember(initialScript) { mutableStateOf(initialScript) }
    var selectedLang by remember(defaultLanguageCode) { mutableStateOf(defaultLanguageCode) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }

    var ttsReady by remember { mutableStateOf(false) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var lastExportedWav by remember { mutableStateOf<File?>(null) }
    var isPlayingFile by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var isListeningMic by remember { mutableStateOf(false) }
    var liveTranscript by remember { mutableStateOf("") }

    fun localeFor(code: String): Locale = when (code) {
        "bn" -> Locale("bn", "BD")
        "ar" -> Locale("ar", "SA")
        "es" -> Locale("es", "ES")
        "fr" -> Locale("fr", "FR")
        "hi" -> Locale("hi", "IN")
        else -> Locale.US
    }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                instance?.let { engine ->
                    val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                    availableVoices = voices.filter { v ->
                        !v.isNetworkConnectionRequired
                    }.take(12)
                }
            }
        }
        instance
    }

    LaunchedEffect(ttsReady, selectedLang) {
        if (ttsReady) {
            val loc = localeFor(selectedLang)
            tts.language = loc
            val voices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            val matching = voices.filter { it.locale.language == loc.language }
            availableVoices = (if (matching.isNotEmpty()) matching else voices).take(8)
            selectedVoice = availableVoices.firstOrNull()
        }
    }

    DisposableEffect(Unit) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
        onDispose {
            tts.stop()
            tts.shutdown()
            mediaPlayer?.release()
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val data = GeminiAiService.readBytesFromUri(context, uri)
                    if (data != null) {
                        vm.transcribeAudioBytes(data.first, data.second)
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to read audio file.")
                }
            }
        }
    }

    fun startSpeechDictation() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch { snackbarHostState.showSnackbar("Speech recognition service unavailable on this device.") }
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningMic = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningMic = false }
            override fun onError(error: Int) {
                isListeningMic = false
                recognizer.destroy()
            }
            override fun onResults(results: Bundle?) {
                isListeningMic = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    liveTranscript = if (liveTranscript.isBlank()) spoken else "$liveTranscript $spoken"
                }
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) liveTranscript = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeFor(selectedLang).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechDictation()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for live speech transcription.") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Voice & Audio Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("voice_back_button")) {
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

                // Ethical voice disclosure
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldSuccess.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.38f))
                ) {
                    Text(
                        text = "Uses genuine Android Text-to-Speech & Speech Recognition + Gemini audio transcription. Voice cloning of real people is never performed.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Language selector
                Text("Language", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppLanguages.all) { lang ->
                        FilterChip(
                            selected = selectedLang == lang.code,
                            onClick = { selectedLang = lang.code },
                            label = { Text("${lang.nativeName} (${lang.label})") }
                        )
                    }
                }

                if (availableVoices.isNotEmpty()) {
                    Text("System Voices (${availableVoices.size})", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableVoices) { voice ->
                            FilterChip(
                                selected = selectedVoice?.name == voice.name,
                                onClick = {
                                    selectedVoice = voice
                                    tts.voice = voice
                                },
                                label = { Text(voice.name.takeLast(14)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = textToSpeak,
                    onValueChange = { textToSpeak = it },
                    label = { Text("Text for Speech Synthesis (or script topic)") },
                    placeholder = { Text("Type text to speak aloud, or tap AI Script Helper below...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_tts_input")
                )

                Text("Speed: ${String.format("%.2fx", speechRate)} | Pitch: ${String.format("%.2fx", pitch)}", style = MaterialTheme.typography.labelLarge)
                Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.5f..2.0f)
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.6f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuroraButton(
                        text = if (isSpeaking) "Stop Speaking" else "Speak Aloud",
                        icon = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        enabled = ttsReady && (isSpeaking || textToSpeak.isNotBlank()),
                        testTag = "voice_speak_button",
                        onClick = {
                            if (isSpeaking) {
                                tts.stop()
                                isSpeaking = false
                            } else {
                                tts.setSpeechRate(speechRate)
                                tts.setPitch(pitch)
                                selectedVoice?.let { tts.voice = it }
                                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "px_tts_${System.currentTimeMillis()}")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            if (textToSpeak.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Enter text to export audio.") }
                                return@OutlinedButton
                            }
                            tts.setSpeechRate(speechRate)
                            tts.setPitch(pitch)
                            selectedVoice?.let { tts.voice = it }
                            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val wavFile = File(exportDir, "voice_${System.currentTimeMillis()}.wav")
                            val res = tts.synthesizeToFile(textToSpeak, null, wavFile, "px_export_${System.currentTimeMillis()}")
                            if (res == TextToSpeech.SUCCESS) {
                                lastExportedWav = wavFile
                                scope.launch {
                                    val saved = ExportUtils.exportAudioWavToDownloads(context, wavFile, "ProjectX_Voice")
                                    saved.onSuccess { loc ->
                                        vm.recordAudioExport(wavFile.name, loc)
                                        snackbarHostState.showSnackbar("Audio exported to $loc")
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Export failed")
                                    }
                                }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("TTS synthesis to file failed.") }
                            }
                        },
                        enabled = ttsReady && textToSpeak.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("voice_export_wav_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save WAV")
                    }
                }

                if (lastExportedWav != null && lastExportedWav!!.exists()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (isPlayingFile) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlayingFile = false
                                } else {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(lastExportedWav!!.absolutePath)
                                        prepare()
                                        setOnCompletionListener {
                                            isPlayingFile = false
                                        }
                                        start()
                                    }
                                    mediaPlayer = mp
                                    isPlayingFile = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(if (isPlayingFile) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPlayingFile) "Stop WAV" else "Play Exported WAV")
                        }
                        OutlinedButton(
                            onClick = {
                                ExportUtils.shareFile(context, lastExportedWav!!, "audio/wav", "Share Audio")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Share WAV")
                        }
                    }
                }

                OutlinedButton(
                    onClick = { vm.generateVoiceoverScript(textToSpeak, selectedLang) { textToSpeak = it } },
                    enabled = isOnline && transcriptionState !is TextGenUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_ai_script_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI Voiceover Script Helper (Gemini)")
                }

                // Speech Transcription & Audio Upload Section
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Text("Speech Transcription & Audio Upload", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Dictate live with your microphone (on-device SpeechRecognizer) or upload an audio file (<20 MB) for Gemini AI transcription.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) startSpeechDictation()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_mic_transcribe_button")
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isListeningMic) "Listening..." else "Mic Dictate")
                        }
                        OutlinedButton(
                            onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_upload_audio_button")
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload Audio")
                        }
                    }

                    if (liveTranscript.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Live Mic Transcript:", style = MaterialTheme.typography.labelLarge)
                        Text(liveTranscript, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(liveTranscript))
                                scope.launch { snackbarHostState.showSnackbar("Transcript copied") }
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                            OutlinedButton(onClick = { textToSpeak = liveTranscript }) {
                                Text("Send to TTS")
                            }
                        }
                    }
                }

                when (val state = transcriptionState) {
                    is TextGenUiState.Loading -> AiPulseLoader("Processing audio with Gemini...")
                    is TextGenUiState.Success -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("AI Audio Transcription", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(state.text, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(state.text))
                                scope.launch { snackbarHostState.showSnackbar("Copied transcription") }
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy Transcription")
                            }
                        }
                    }
                    is TextGenUiState.Error -> ErrorBanner(message = state.message)
                    else -> Unit
                }
            }
        }
    }
}
