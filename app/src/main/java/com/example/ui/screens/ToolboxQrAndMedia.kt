package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.AppContainer
import com.example.core.ExportUtils
import com.example.data.firebase.GeminiAiService
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.GlassCard
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.random.Random

@Composable
fun QrGeneratorTool(
    container: AppContainer,
    snackbarHostState: SnackbarHostState
) {
    var content by remember { mutableStateOf("https://projectx.creative") }
    var fgColor by remember { mutableIntStateOf(AndroidColor.BLACK) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val colors = listOf(
        "Classic Black" to AndroidColor.BLACK,
        "Electric Violet" to AndroidColor.parseColor("#5B21B6"),
        "Cobalt Blue" to AndroidColor.parseColor("#1D4ED8"),
        "Deep Teal" to AndroidColor.parseColor("#0F766E")
    )

    LaunchedEffect(content, fgColor) {
        if (content.isNotBlank()) {
            qrBitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 640, 640)
                    val w = matrix.width
                    val h = matrix.height
                    val pixels = IntArray(w * h)
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            pixels[y * w + x] = if (matrix.get(x, y)) fgColor else AndroidColor.WHITE
                        }
                    }
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                        setPixels(pixels, 0, w, 0, 0, w, h)
                    }
                }.getOrNull()
            }
        } else {
            qrBitmap = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("URL, text, Wi-Fi, or contact info") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { (label, col) ->
                FilterChip(
                    selected = fgColor == col,
                    onClick = { fgColor = col },
                    label = { Text(label) }
                )
            }
        }

        if (qrBitmap != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "Generated QR code",
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = ExportUtils.exportBitmapToGallery(context, qrBitmap!!, "ProjectX_QR")
                                res.onSuccess { filename ->
                                    container.localRepository.recordDownload(filename, "image/png", "Pictures/ProjectX")
                                    snackbarHostState.showSnackbar("Saved QR PNG: $filename")
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Export failed")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save PNG")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ExportUtils.shareBitmap(context, qrBitmap!!, "MK Assist QR Code")
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
    }
}

@Composable
fun QrScannerTool(
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedValue by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = GeminiAiService.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    val decoded = withContext(Dispatchers.Default) { decodeBitmapQr(bmp) }
                    if (decoded != null) {
                        scannedValue = decoded
                    } else {
                        snackbarHostState.showSnackbar("No QR code found in selected image.")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (hasCameraPermission) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                CameraQrPreview(
                    onQrDetected = { text -> scannedValue = text }
                )
            }
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Camera Access for Live QR Scanning", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Grant camera permission to scan QR codes live, or pick a QR image from your gallery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                AuroraButton(
                    text = "Enable Camera Scanner",
                    icon = Icons.Rounded.CameraAlt,
                    testTag = "qr_grant_camera_button",
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        OutlinedButton(
            onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_scan_gallery_button")
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Scan QR from Gallery Photo")
        }

        if (scannedValue != null) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.secondary
            ) {
                Text("Scanned Result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                Text(scannedValue.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(scannedValue.orEmpty()))
                        scope.launch { snackbarHostState.showSnackbar("Copied QR value") }
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Result")
                }
            }
        }
    }
}

@Composable
private fun CameraQrPreview(
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val reader = MultiFormatReader()
                analysis.setAnalyzer(executor) { imageProxy ->
                    decodeImageProxyQr(imageProxy, reader)?.let(onQrDetected)
                    imageProxy.close()
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
    )
}

private fun decodeImageProxyQr(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false
    )
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

private fun decodeBitmapQr(bitmap: Bitmap): String? {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val source = RGBLuminanceSource(w, h, pixels)
    return try {
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ColorPaletteTool(
    snackbarHostState: SnackbarHostState
) {
    var harmony by remember { mutableStateOf("Futuristic Neon") }
    var palette by remember { mutableStateOf(generateHarmonyColors("Futuristic Neon")) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val harmonies = listOf("Futuristic Neon", "Complementary", "Analogous", "Triadic", "Monochrome")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(harmonies) { mode ->
                FilterChip(
                    selected = harmony == mode,
                    onClick = {
                        harmony = mode
                        palette = generateHarmonyColors(mode)
                    },
                    label = { Text(mode) }
                )
            }
        }

        AuroraButton(
            text = "Generate New Palette",
            icon = Icons.Rounded.Refresh,
            testTag = "palette_generate_button",
            onClick = { palette = generateHarmonyColors(harmony) },
            modifier = Modifier.fillMaxWidth()
        )

        palette.forEach { hex ->
            val colorInt = AndroidColor.parseColor(hex)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboard.setText(AnnotatedString(hex))
                        scope.launch { snackbarHostState.showSnackbar("Copied $hex") }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(hex, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Tap to copy hex", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $hex")
                }
            }
        }
    }
}

private fun generateHarmonyColors(mode: String): List<String> {
    val baseHue = when (mode) {
        "Futuristic Neon" -> Random.nextInt(185, 295).toFloat()
        else -> Random.nextInt(0, 360).toFloat()
    }
    val hues = when (mode) {
        "Complementary" -> listOf(baseHue, baseHue, (baseHue + 180f) % 360f, (baseHue + 180f) % 360f, (baseHue + 15f) % 360f)
        "Analogous" -> listOf(-30f, -15f, 0f, 15f, 30f).map { (baseHue + it + 360f) % 360f }
        "Triadic" -> listOf(baseHue, (baseHue + 120f) % 360f, (baseHue + 240f) % 360f, (baseHue + 15f) % 360f, (baseHue + 135f) % 360f)
        "Monochrome" -> List(5) { baseHue }
        else -> listOf(baseHue, (baseHue + 35f) % 360f, (baseHue + 70f) % 360f, (baseHue + 180f) % 360f, (baseHue + 215f) % 360f)
    }
    val sats = listOf(0.85f, 0.72f, 0.90f, 0.65f, 0.80f)
    val vals = listOf(0.95f, 0.78f, 0.88f, 0.55f, 0.98f)
    return hues.indices.map { i ->
        val hsv = floatArrayOf(hues[i], sats[i], vals[i])
        val rgb = AndroidColor.HSVToColor(hsv)
        String.format("#%06X", 0xFFFFFF and rgb)
    }
}
