package com.galaxya56.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxya56.ai.ui.theme.Line
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.Surface3
import com.galaxya56.ai.ui.theme.TextMuted

private sealed interface Segment {
    data class Prose(val text: String) : Segment
    data class Code(val language: String, val code: String) : Segment
}

private fun parseSegments(text: String): List<Segment> {
    val out = mutableListOf<Segment>()
    val fence = Regex("```")
    var index = 0
    var position = 0
    val parts = fence.findAll(text).toList()
    while (index + 1 < parts.size) {
        val open = parts[index].range.first
        val close = parts[index + 1].range.first
        if (open > position) out += Segment.Prose(text.substring(position, open))
        val body = text.substring(parts[index].range.last + 1, close).trim('\n')
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val language = if (firstLine.length in 1..14 && !firstLine.contains(' ')) firstLine else ""
        val code = if (language.isBlank()) body else body.removePrefix(firstLine).trimStart('\n')
        out += Segment.Code(language, code)
        position = parts[index + 1].range.last + 1
        index += 2
    }
    if (position < text.length) out += Segment.Prose(text.substring(position))
    return out.ifEmpty { listOf(Segment.Prose(text)) }
}

@Composable
fun MessageContent(text: String) {
    val segments = remember(text) { parseSegments(text) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Prose -> if (segment.text.isNotBlank()) {
                    Text(
                        text = segment.text.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is Segment.Code -> CodeBlock(segment.language, segment.code)
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String) {
    val context = LocalContext.current
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface3)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase().ifBlank { "CODE" },
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { copyToClipboard(context, code) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Code kopieren", tint = TextMuted, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GalaxyA56AI", text))
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
fun Pill(text: String, color: Color = Surface3, contentColor: Color = TextMuted, onClick: (() -> Unit)? = null) {
    Surface(
        color = color,
        shape = RoundedCornerShape(50),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun LinkRow(label: String, url: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { openUrl(context, url) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.OpenInNew, contentDescription = "Im Browser öffnen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LabelledValue(label: String, value: String, warn: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyHint(title: String, body: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun VSpacer(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun HSpacer(width: Int) = Spacer(Modifier.width(width.dp))
