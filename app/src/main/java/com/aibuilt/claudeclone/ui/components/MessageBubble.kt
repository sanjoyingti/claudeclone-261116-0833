package com.aibuilt.claudeclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aibuilt.claudeclone.model.ChatMessage
import com.aibuilt.claudeclone.model.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Segment {
    data class Prose(val text: String) : Segment
    data class Code(val text: String) : Segment
}

private fun splitCode(text: String): List<Segment> {
    val out = mutableListOf<Segment>()
    var index = 0
    while (index < text.length) {
        val fence = text.indexOf("```", index)
        if (fence < 0) {
            out.add(Segment.Prose(text.substring(index)))
            break
        }
        if (fence > index) out.add(Segment.Prose(text.substring(index, fence)))
        val close = text.indexOf("```", fence + 3)
        if (close < 0) {
            out.add(Segment.Code(text.substring(fence + 3).trimStart('\n')))
            break
        }
        out.add(Segment.Code(text.substring(fence + 3, close).trimStart('\n')))
        index = close + 3
    }
    return out.filter { segment ->
        when (segment) {
            is Segment.Code -> true
            is Segment.Prose -> segment.text.isNotBlank()
        }
    }
}

private fun timeLabel(stamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stamp))

@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    canRegenerate: Boolean
) {
    val isUser = message.role == Role.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.attachment != null) {
                        Text(
                            text = "📎 ${message.attachment.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (message.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                if (message.streaming && message.text.isBlank()) {
                    StreamingDots(Modifier.padding(vertical = 8.dp))
                } else if (message.error) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = message.errorDetail.ifBlank { "Request failed." },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, Modifier.size(16.dp))
                                Text("  Retry")
                            }
                        }
                    }
                } else {
                    SelectionContainer {
                        Column {
                            splitCode(message.text).forEach { segment ->
                                when (segment) {
                                    is Segment.Prose -> Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    is Segment.Code -> CodeBlock(segment.text, onCopy)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = timeLabel(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!message.streaming && message.text.isNotBlank()) {
                IconButton(onClick = { onCopy(message.text) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isUser && canRegenerate && !message.streaming) {
                IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Regenerate",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, onCopy: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onCopy(code) }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
