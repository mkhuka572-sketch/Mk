package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.math.max

class GeminiAiService {

    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun streamText(prompt: String): Flow<String> {
        return generalModel.generateContentStream(prompt)
            .map { chunk -> chunk.text.orEmpty() }
    }

    suspend fun generateText(prompt: String): String = withTimeout(60_000L) {
        val response = generalModel.generateContent(prompt)
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty response.")
    }

    fun streamChat(
        history: List<Pair<String, String>>,
        userMessage: String,
        attachedBitmap: Bitmap? = null,
        languageHint: String = "Auto (match user's language, including Bengali and English)"
    ): Flow<String> {
        val systemInstruction = content("user") {
            text(
                "You are MK Assist AI Assistant, a helpful, globally accessible creative assistant. " +
                    "Preferred language guidance: $languageHint. " +
                    "Respond fluently and accurately in whatever language the user writes (such as English, Bengali/বাংলা, Arabic, Spanish, French, or Hindi). " +
                    "Be clear, concise, and helpful."
            )
        }
        val modelAck = content("model") {
            text("Understood. I am ready to help in any language.")
        }

        val chatHistory: List<Content> = listOf(systemInstruction, modelAck) + history.takeLast(16).map { (role, msg) ->
            content(if (role == "user") "user" else "model") { text(msg) }
        }

        val chat = generalModel.startChat(history = chatHistory)
        val outgoing = content("user") {
            if (attachedBitmap != null) {
                image(downscaleBitmap(attachedBitmap))
            }
            text(userMessage)
        }
        return chat.sendMessageStream(outgoing).map { it.text.orEmpty() }
    }

    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        instruction: String
    ): String = withTimeout(60_000L) {
        val scaled = downscaleBitmap(bitmap)
        val response = generalModel.generateContent(
            content {
                image(scaled)
                text(instruction)
            }
        )
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No analysis was returned for this image.")
    }

    suspend fun analyzeDocumentBytes(
        bytes: ByteArray,
        mimeType: String,
        instruction: String
    ): String = withTimeout(60_000L) {
        if (bytes.size > MAX_INLINE_BYTES) {
            throw IllegalArgumentException("File exceeds 20 MB inline limit. Please pick a smaller file.")
        }
        val response = generalModel.generateContent(
            content {
                inlineData(bytes, mimeType)
                text(instruction)
            }
        )
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No analysis was returned for this file.")
    }

    suspend fun generateImage(
        prompt: String,
        referenceBitmap: Bitmap? = null
    ): Bitmap = withTimeout(60_000L) {
        val response = if (referenceBitmap != null) {
            val scaled = downscaleBitmap(referenceBitmap)
            imageGenModel.generateContent(
                content {
                    image(scaled)
                    text(prompt)
                }
            )
        } else {
            imageGenModel.generateContent(prompt)
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        imagePart?.image
            ?: throw IllegalStateException("Image model did not return an image. Check Firebase Blaze billing or quota limits.")
    }

    companion object {
        const val MAX_INLINE_BYTES = 20 * 1024 * 1024

        fun downscaleBitmap(bitmap: Bitmap, maxDimension: Int = 1440): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            val longest = max(w, h)
            if (longest <= maxDimension) return bitmap
            val scale = maxDimension.toFloat() / longest.toFloat()
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }

        suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }

        suspend fun readBytesFromUri(context: Context, uri: Uri): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(data)
                    if (read == -1) break
                    total += read
                    if (total > MAX_INLINE_BYTES) {
                        throw IllegalArgumentException("Selected file is larger than 20 MB.")
                    }
                    buffer.write(data, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext null
            bytes to mimeType
        }
    }
}
