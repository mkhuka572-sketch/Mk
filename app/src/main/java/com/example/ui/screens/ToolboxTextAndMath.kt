package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.GlassCard
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@Composable
fun CalculatorTool() {
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    fun evaluateNow(expr: String) {
        if (expr.isBlank()) {
            resultText = "0"
            return
        }
        val eval = runCatching { MathExpressionParser(expr).parse() }.getOrNull()
        resultText = if (eval != null && !eval.isNaN() && !eval.isInfinite()) {
            if (eval % 1.0 == 0.0 && kotlin.math.abs(eval) < 1e12) eval.toLong().toString()
            else String.format(Locale.US, "%.6f", eval).trimEnd('0').trimEnd('.')
        } else {
            "Error"
        }
    }

    val rows = listOf(
        listOf("C", "(", ")", "/"),
        listOf("sin(", "cos(", "sqrt(", "^"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "%", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = expression.ifBlank { "0" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = resultText,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_result_text")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    if (expression.isNotEmpty()) {
                        expression = expression.dropLast(1)
                        evaluateNow(expression)
                    }
                }
            ) {
                Icon(Icons.Rounded.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("DEL")
            }
        }

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isOp = key in listOf("/", "*", "-", "+", "=", "^")
                    Button(
                        onClick = {
                            when (key) {
                                "C" -> {
                                    expression = ""
                                    resultText = "0"
                                }
                                "=" -> {
                                    evaluateNow(expression)
                                    if (resultText != "Error" && expression.isNotBlank()) {
                                        history = (listOf("$expression = $resultText") + history).take(8)
                                        expression = resultText
                                    }
                                }
                                "%" -> {
                                    expression += "/100"
                                    evaluateNow(expression)
                                }
                                else -> {
                                    expression += key
                                    evaluateNow(expression)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isOp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Text("History", style = MaterialTheme.typography.titleMedium)
            history.forEach { item ->
                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private class MathExpressionParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw IllegalArgumentException("Unexpected char")
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            when {
                eat('+'.code) -> x += parseTerm()
                eat('-'.code) -> x -= parseTerm()
                else -> return x
            }
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            when {
                eat('*'.code) -> x *= parseFactor()
                eat('/'.code) -> x /= parseFactor()
                else -> return x
            }
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()
        var x: Double
        val startPos = pos
        if (eat('('.code)) {
            x = parseExpression()
            eat(')'.code)
        } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
            while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
            x = str.substring(startPos, pos).toDouble()
        } else if (ch in 'a'.code..'z'.code) {
            while (ch in 'a'.code..'z'.code) nextChar()
            val func = str.substring(startPos, pos)
            x = parseFactor()
            x = when (func) {
                "sqrt" -> sqrt(x)
                "sin" -> sin(Math.toRadians(x))
                "cos" -> cos(Math.toRadians(x))
                "tan" -> tan(Math.toRadians(x))
                "log" -> log10(x)
                "ln" -> ln(x)
                else -> throw IllegalArgumentException("Unknown func $func")
            }
        } else {
            throw IllegalArgumentException("Unexpected")
        }
        if (eat('^'.code)) x = x.pow(parseFactor())
        return x
    }
}

@Composable
fun UnitConverterTool() {
    val categories = remember {
        mapOf(
            "Length" to listOf("Meter" to 1.0, "Kilometer" to 1000.0, "Centimeter" to 0.01, "Mile" to 1609.344, "Foot" to 0.3048, "Inch" to 0.0254),
            "Weight" to listOf("Kilogram" to 1.0, "Gram" to 0.001, "Pound" to 0.453592, "Ounce" to 0.0283495, "Ton" to 1000.0),
            "Data" to listOf("Byte" to 1.0, "KB" to 1024.0, "MB" to 1048576.0, "GB" to 1073741824.0, "TB" to 1099511627776.0),
            "Speed" to listOf("m/s" to 1.0, "km/h" to 0.277778, "mph" to 0.44704, "knot" to 0.514444),
            "Area" to listOf("sq m" to 1.0, "sq km" to 1_000_000.0, "sq ft" to 0.092903, "Acre" to 4046.86, "Hectare" to 10000.0),
            "Time" to listOf("Second" to 1.0, "Minute" to 60.0, "Hour" to 3600.0, "Day" to 86400.0),
            "Temperature" to listOf("Celsius" to 1.0, "Fahrenheit" to 1.0, "Kelvin" to 1.0)
        )
    }
    var category by remember { mutableStateOf("Length") }
    val units = categories[category] ?: emptyList()
    var fromIndex by remember(category) { mutableStateOf(0) }
    var toIndex by remember(category) { mutableStateOf(1.coerceAtMost(units.lastIndex)) }
    var inputVal by remember { mutableStateOf("100") }

    val converted = remember(category, fromIndex, toIndex, inputVal) {
        val v = inputVal.toDoubleOrNull() ?: return@remember "Invalid number"
        val fromName = units.getOrNull(fromIndex)?.first ?: ""
        val toName = units.getOrNull(toIndex)?.first ?: ""
        val out = if (category == "Temperature") {
            val celsius = when (fromName) {
                "Fahrenheit" -> (v - 32.0) * 5.0 / 9.0
                "Kelvin" -> v - 273.15
                else -> v
            }
            when (toName) {
                "Fahrenheit" -> celsius * 9.0 / 5.0 + 32.0
                "Kelvin" -> celsius + 273.15
                else -> celsius
            }
        } else {
            val fromFactor = units.getOrNull(fromIndex)?.second ?: 1.0
            val toFactor = units.getOrNull(toIndex)?.second ?: 1.0
            (v * fromFactor) / toFactor
        }
        String.format(Locale.US, "%.4f", out).trimEnd('0').trimEnd('.')
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories.keys.toList()) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) }
                )
            }
        }

        OutlinedTextField(
            value = inputVal,
            onValueChange = { inputVal = it },
            label = { Text("Input value") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unit_input")
        )

        Text("From Unit", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(units.indices.toList()) { idx ->
                FilterChip(
                    selected = fromIndex == idx,
                    onClick = { fromIndex = idx },
                    label = { Text(units[idx].first) }
                )
            }
        }

        Text("To Unit", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(units.indices.toList()) { idx ->
                FilterChip(
                    selected = toIndex == idx,
                    onClick = { toIndex = idx },
                    label = { Text(units[idx].first) }
                )
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = MaterialTheme.colorScheme.secondary
        ) {
            Text("Converted Value", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$converted ${units.getOrNull(toIndex)?.first.orEmpty()}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WordCounterTool() {
    var text by remember { mutableStateOf("") }
    val words = remember(text) {
        if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }
    val charsWithSpaces = text.length
    val charsNoSpaces = text.count { !it.isWhitespace() }
    val sentences = remember(text) {
        if (text.isBlank()) 0 else text.split(Regex("[.!?।]+")).count { it.isNotBlank() }
    }
    val paragraphs = remember(text) {
        if (text.isBlank()) 0 else text.split(Regex("\\n+")).count { it.isNotBlank() }
    }
    val readingMinutes = String.format(Locale.US, "%.1f min", words / 200.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Paste or type text (supports Unicode & Bangla)") },
            minLines = 6,
            maxLines = 12,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("word_counter_input")
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Words", words.toString(), Modifier.weight(1f))
            StatBox("Characters", charsWithSpaces.toString(), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("No Spaces", charsNoSpaces.toString(), Modifier.weight(1f))
            StatBox("Sentences", sentences.toString(), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Paragraphs", paragraphs.toString(), Modifier.weight(1f))
            StatBox("Read Time", readingMinutes, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextFormatterTool(snackbarHostState: SnackbarHostState) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val actions = listOf<Pair<String, (String) -> String>>(
        "UPPERCASE" to { it.uppercase() },
        "lowercase" to { it.lowercase() },
        "Title Case" to { s -> s.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { c -> c.titlecase() } } },
        "Sentence case" to { s -> s.lowercase().replaceFirstChar { it.titlecase() } },
        "Clean Spaces" to { s -> s.trim().replace(Regex("\\s+"), " ") },
        "Dedupe Lines" to { s -> s.lines().distinct().joinToString("\n") },
        "Sort Lines A-Z" to { s -> s.lines().sorted().joinToString("\n") },
        "Slugify" to { s -> s.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-') },
        "Reverse" to { it.reversed() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text to format") },
            minLines = 6,
            maxLines = 12,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("formatter_input")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { (label, transform) ->
                Surface(
                    onClick = { text = transform(text) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                scope.launch { snackbarHostState.showSnackbar("Formatted text copied") }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy Result")
        }
    }
}

@Composable
fun OfflinePromptBuilderTool(
    snackbarHostState: SnackbarHostState,
    onSendToImageStudio: (String) -> Unit
) {
    var subject by remember { mutableStateOf("A futuristic solar-powered observatory") }
    var style by remember { mutableStateOf("Cinematic 3D render") }
    var lighting by remember { mutableStateOf("Volumetric cyan & violet neon") }
    var camera by remember { mutableStateOf("Wide angle 24mm lens, crisp focus") }
    var mood by remember { mutableStateOf("Awe-inspiring, ultra-detailed") }

    val compiledPrompt = remember(subject, style, lighting, camera, mood) {
        listOf(subject, style, lighting, camera, mood).filter { it.isNotBlank() }.joinToString(", ")
    }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = style, onValueChange = { style = it }, label = { Text("Art Medium / Style") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lighting, onValueChange = { lighting = it }, label = { Text("Lighting") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = camera, onValueChange = { camera = it }, label = { Text("Camera / Composition") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mood, onValueChange = { mood = it }, label = { Text("Mood & Details") }, modifier = Modifier.fillMaxWidth())

        GlassCard(modifier = Modifier.fillMaxWidth(), accentColor = MaterialTheme.colorScheme.secondary) {
            Text("Generated Prompt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(compiledPrompt, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(compiledPrompt))
                        scope.launch { snackbarHostState.showSnackbar("Prompt copied") }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                AuroraButton(
                    text = "Use in Image Studio",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { onSendToImageStudio(compiledPrompt) },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}
