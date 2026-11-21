package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppContainer
import com.example.ui.navigation.ToolRegistry
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard

@Composable
fun ToolboxScreen(
    onOpenTool: (toolId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = ToolRegistry.offlineTools
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Creative Toolbox",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EmeraldSuccess.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.OfflineBolt,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "100% Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "9 fast, privacy-first utilities that work anytime—even in airplane mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools, key = { it.id }) { tool ->
                    val shortId = when (tool.id) {
                        "tool_qr_gen" -> "qr_gen"
                        "tool_qr_scan" -> "qr_scan"
                        "tool_calculator" -> "calculator"
                        "tool_unit" -> "unit"
                        "tool_word_count" -> "word_count"
                        "tool_formatter" -> "formatter"
                        "tool_palette" -> "palette"
                        "tool_prompt" -> "prompt"
                        else -> "notes"
                    }
                    GlassCard(
                        onClick = { onOpenTool(shortId) },
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toolbox_card_$shortId")
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tool.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxToolHostScreen(
    toolId: String,
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onSendPromptToImageStudio: (String) -> Unit,
    onBack: () -> Unit
) {
    val title = when (toolId) {
        "qr_gen" -> "QR Code Generator"
        "qr_scan" -> "QR Code Scanner"
        "calculator" -> "Scientific Calculator"
        "unit" -> "Unit Converter"
        "word_count" -> "Word & Char Counter"
        "formatter" -> "Text Formatter"
        "palette" -> "Color Palette Generator"
        "prompt" -> "Offline Prompt Builder"
        "notes" -> "Creative Notes"
        else -> "Toolbox"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("toolbox_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                when (toolId) {
                    "qr_gen" -> QrGeneratorTool(container, snackbarHostState)
                    "qr_scan" -> QrScannerTool(snackbarHostState)
                    "calculator" -> CalculatorTool()
                    "unit" -> UnitConverterTool()
                    "word_count" -> WordCounterTool()
                    "formatter" -> TextFormatterTool(snackbarHostState)
                    "palette" -> ColorPaletteTool(snackbarHostState)
                    "prompt" -> OfflinePromptBuilderTool(snackbarHostState, onSendPromptToImageStudio)
                    "notes" -> NotesTool(container, snackbarHostState)
                    else -> CalculatorTool()
                }
            }
        }
    }
}
