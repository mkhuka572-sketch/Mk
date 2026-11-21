package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppContainer
import com.example.core.ExportUtils
import com.example.data.firebase.GeminiAiService
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.ui.theme.AiPulseLoader
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.OfflineStatusBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ChatViewModel(
    private val container: AppContainer
) : ViewModel() {

    val sessions: StateFlow<List<ChatSessionEntity>> =
        container.localRepository.observeChatSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> =
        _activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else container.localRepository.observeChatMessages(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _error.value = null
    }

    fun startNewSession(language: String) {
        viewModelScope.launch {
            val session = container.localRepository.createChatSession("New Chat", language)
            _activeSessionId.value = session.id
            _error.value = null
        }
    }

    fun clearActiveSession() {
        val id = _activeSessionId.value ?: return
        viewModelScope.launch {
            container.localRepository.clearChatMessages(id)
            val existing = sessions.value.firstOrNull { it.id == id }
            if (existing != null) {
                val updated = existing.copy(
                    lastMessagePreview = "",
                    messageCount = 0,
                    updatedAtMillis = System.currentTimeMillis()
                )
                container.localRepository.upsertChatSession(updated)
                container.syncChatSessionIfSignedIn(updated)
            }
            _error.value = null
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            container.localRepository.deleteChatSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = sessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun sendMessage(
        promptText: String,
        attachedBitmap: Bitmap?,
        languageCode: String
    ) {
        val cleanText = promptText.trim()
        if (cleanText.isEmpty() && attachedBitmap == null) return
        if (_isLoading.value) return

        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            _streamingText.value = ""

            try {
                val currentId = _activeSessionId.value ?: run {
                    val title = cleanText.take(36).ifBlank { "Image Chat" }
                    val created = container.localRepository.createChatSession(title, languageCode)
                    _activeSessionId.value = created.id
                    created.id
                }

                val existingHistory = container.localRepository.getChatMessagesOnce(currentId)
                    .map { it.role to it.text }

                val savedImagePath = if (attachedBitmap != null) {
                    ExportUtils.saveBitmapToInternalFile(
                        container.appContext,
                        GeminiAiService.downscaleBitmap(attachedBitmap),
                        "chat_img"
                    )
                } else null

                val effectivePrompt = cleanText.ifBlank { "Describe and analyze this image." }
                container.localRepository.addChatMessage(
                    sessionId = currentId,
                    role = "user",
                    text = effectivePrompt,
                    imagePath = savedImagePath
                )

                val buffer = StringBuilder()
                withTimeout(60_000L) {
                    container.geminiAiService.streamChat(
                        history = existingHistory,
                        userMessage = effectivePrompt,
                        attachedBitmap = attachedBitmap,
                        languageHint = languageCode
                    ).collect { chunk ->
                        buffer.append(chunk)
                        _streamingText.value = buffer.toString()
                    }
                }

                val reply = buffer.toString().trim()
                if (reply.isNotBlank()) {
                    container.localRepository.addChatMessage(
                        sessionId = currentId,
                        role = "model",
                        text = reply
                    )
                    val currentSession = sessions.value.firstOrNull { it.id == currentId }
                    val updatedSession = (currentSession ?: ChatSessionEntity(
                        id = currentId,
                        title = effectivePrompt.take(40)
                    )).copy(
                        title = if (existingHistory.isEmpty()) effectivePrompt.take(42) else (currentSession?.title ?: "Chat"),
                        language = languageCode,
                        lastMessagePreview = reply.take(120),
                        messageCount = existingHistory.size + 2,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    container.localRepository.upsertChatSession(updatedSession)
                    container.syncChatSessionIfSignedIn(updatedSession)
                } else {
                    _error.value = "Gemini returned an empty response. Please try again."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("ChatVM", "Chat timed out", e)
                _error.value = "Request timed out after 60 seconds. Check your connection and retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ChatVM", "Chat failed", e)
                _error.value = e.localizedMessage ?: "Unable to reach Gemini right now."
            } finally {
                _streamingText.value = null
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(container) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStudioScreen(
    container: AppContainer,
    isOnline: Boolean,
    languageCode: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeSessionId by vm.activeSessionId.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streamingText by vm.streamingText.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var chatLang by remember(languageCode) { mutableStateOf(languageCode) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = GeminiAiService.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    attachedBitmap = GeminiAiService.downscaleBitmap(bmp, 1280)
                } else {
                    snackbarHostState.showSnackbar("Could not decode selected image.")
                }
            }
        }
    }

    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Chat Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sessions.firstOrNull { it.id == activeSessionId }?.title ?: "New Conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("chat_history_button")
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "Chat sessions")
                    }
                    IconButton(
                        onClick = { vm.startNewSession(chatLang) },
                        modifier = Modifier.testTag("chat_new_button")
                    ) {
                        Icon(Icons.Rounded.AddComment, contentDescription = "New chat")
                    }
                    IconButton(
                        onClick = { vm.clearActiveSession() },
                        modifier = Modifier.testTag("chat_clear_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear conversation")
                    }
                }
            }

            OfflineStatusBanner(isOnline = isOnline)

            // Language quick chips (Bangla, English, Auto)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    "en" to "English",
                    "bn" to "বাংলা (Bangla)",
                    "ar" to "العربية",
                    "es" to "Español",
                    "fr" to "Français",
                    "hi" to "हिन्दी"
                )
                items(options) { (code, label) ->
                    FilterChip(
                        selected = chatLang == code,
                        onClick = { chatLang = code },
                        label = { Text(label) }
                    )
                }
            }

            // Messages area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty() && streamingText == null) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "Ask Anything Globally",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Chat naturally in Bangla, English, Arabic, Spanish, French, or Hindi. Attach photos for visual Q&A.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            val starters = listOf(
                                "বাংলায় একটি সৃজনশীল গল্পের আইডিয়া দাও",
                                "Write a 30-second tech reel hook in English",
                                "Explain quantum computing simply"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                starters.forEach { starter ->
                                    Surface(
                                        onClick = {
                                            if (isOnline) {
                                                vm.sendMessage(starter, null, chatLang)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Internet required for Gemini AI Chat.")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = starter,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isUser) 18.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 18.dp
                            ),
                            color = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.widthIn(max = 560.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (isUser) "You" else "Gemini Assistant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!isUser) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(msg.text))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Response copied")
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy response",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (streamingText != null) {
                    item {
                        if (streamingText.isNullOrBlank()) {
                            AiPulseLoader("Gemini is thinking...")
                        } else {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Gemini Assistant (streaming...)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = streamingText.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    item {
                        ErrorBanner(
                            message = error.orEmpty(),
                            onDismiss = { vm.dismissError() }
                        )
                    }
                }
            }

            // Attached image preview
            if (attachedBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Image(
                            bitmap = attachedBitmap!!.asImageBitmap(),
                            contentDescription = "Attached photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick = { attachedBitmap = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove image",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Image attached for Gemini Vision",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Composer bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("chat_attach_photo_button")
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach photo")
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message Gemini (English, বাংলা...)") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                IconButton(
                    onClick = {
                        if (!isOnline) {
                            scope.launch {
                                snackbarHostState.showSnackbar("You are offline. Connect to internet to chat with Gemini.")
                            }
                            return@IconButton
                        }
                        val toSend = input
                        val bmp = attachedBitmap
                        input = ""
                        attachedBitmap = null
                        vm.sendMessage(toSend, bmp, chatLang)
                    },
                    enabled = !isLoading && (input.isNotBlank() || attachedBitmap != null),
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message",
                        tint = if (!isLoading && (input.isNotBlank() || attachedBitmap != null)) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversation Sessions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        vm.startNewSession(chatLang)
                        showHistorySheet = false
                    }) {
                        Text("+ New Chat")
                    }
                }
                if (sessions.isEmpty()) {
                    Text(
                        "No saved sessions yet. Send a message to start one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sessions.forEach { session ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectSession(session.id)
                                    showHistorySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        session.lastMessagePreview.ifBlank { "${session.messageCount} messages" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { vm.deleteSession(session.id) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
