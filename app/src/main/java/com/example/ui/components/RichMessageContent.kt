package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.RoyalGold

private sealed interface RichSegment {
  data class TextBlock(val text: String) : RichSegment
  data class CodeBlock(val language: String, val code: String) : RichSegment
  data class MathBlock(val expression: String) : RichSegment
}

@Composable
fun RichMessageContent(
  rawText: String,
  textColor: Color,
  onCopiedCode: () -> Unit = {},
) {
  val segments = remember(rawText) { parseRichSegments(rawText) }
  val clipboard = LocalClipboardManager.current

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    segments.forEach { seg ->
      when (seg) {
        is RichSegment.TextBlock -> {
          seg.text.lines().forEach { line ->
            val trimmed = line.trimStart()
            when {
              trimmed.startsWith("### ") -> Text(
                text = formatInlineMarkdown(trimmed.removePrefix("### ")),
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan,
              )
              trimmed.startsWith("## ") -> Text(
                text = formatInlineMarkdown(trimmed.removePrefix("## ")),
                style = MaterialTheme.typography.titleMedium,
                color = RoyalGold,
              )
              trimmed.startsWith("# ") -> Text(
                text = formatInlineMarkdown(trimmed.removePrefix("# ")),
                style = MaterialTheme.typography.titleLarge,
                color = RoyalGold,
              )
              trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
              ) {
                Text("•  ", color = ElectricCyan, fontWeight = FontWeight.Bold)
                Text(
                  text = formatInlineMarkdown(trimmed.drop(2)),
                  style = MaterialTheme.typography.bodyMedium,
                  color = textColor,
                )
              }
              trimmed.isNotEmpty() -> Text(
                text = formatInlineMarkdown(line),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
              )
              else -> Spacer(Modifier.height(2.dp))
            }
          }
        }

        is RichSegment.CodeBlock -> {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianBg)
              .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
              .padding(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = seg.language.ifBlank { "code" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
              )
              IconButton(
                onClick = {
                  clipboard.setText(AnnotatedString(seg.code))
                  onCopiedCode()
                },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "Copy code",
                  tint = ElectricCyan,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = seg.code,
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              lineHeight = 18.sp,
              color = Color(0xFFE2E8F0),
            )
          }
        }

        is RichSegment.MathBlock -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(RoyalGold.copy(alpha = 0.10f))
              .border(1.dp, RoyalGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Functions,
              contentDescription = "Math formula",
              tint = RoyalGold,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = seg.expression,
              fontFamily = FontFamily.Monospace,
              fontSize = 14.sp,
              color = RoyalGold,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}

private fun formatInlineMarkdown(line: String): AnnotatedString = buildAnnotatedString {
  var idx = 0
  while (idx < line.length) {
    when {
      line.startsWith("**", idx) -> {
        val end = line.indexOf("**", idx + 2)
        if (end != -1) {
          withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(line.substring(idx + 2, end))
          }
          idx = end + 2
        } else {
          append(line[idx])
          idx++
        }
      }
      line[idx] == '`' -> {
        val end = line.indexOf('`', idx + 1)
        if (end != -1) {
          withStyle(
            SpanStyle(
              fontFamily = FontFamily.Monospace,
              color = ElectricCyan,
              background = OutlineSubtle.copy(alpha = 0.45f),
            ),
          ) {
            append(" ${line.substring(idx + 1, end)} ")
          }
          idx = end + 1
        } else {
          append(line[idx])
          idx++
        }
      }
      else -> {
        append(line[idx])
        idx++
      }
    }
  }
}

private fun parseRichSegments(raw: String): List<RichSegment> {
  val result = mutableListOf<RichSegment>()
  val codeFenceRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
  var lastIndex = 0
  for (match in codeFenceRegex.findAll(raw)) {
    if (match.range.first > lastIndex) {
      val before = raw.substring(lastIndex, match.range.first)
      splitMathBlocks(before, result)
    }
    val lang = match.groupValues[1]
    val code = match.groupValues[2].trimEnd()
    result.add(RichSegment.CodeBlock(lang, code))
    lastIndex = match.range.last + 1
  }
  if (lastIndex < raw.length) {
    splitMathBlocks(raw.substring(lastIndex), result)
  }
  if (result.isEmpty() && raw.isNotBlank()) {
    result.add(RichSegment.TextBlock(raw))
  }
  return result
}

private fun splitMathBlocks(text: String, out: MutableList<RichSegment>) {
  val mathRegex = Regex("\\$\\$([\\s\\S]*?)\\$\\$")
  var cursor = 0
  for (match in mathRegex.findAll(text)) {
    if (match.range.first > cursor) {
      val chunk = text.substring(cursor, match.range.first).trim()
      if (chunk.isNotEmpty()) out.add(RichSegment.TextBlock(chunk))
    }
    val expr = match.groupValues[1].trim()
    if (expr.isNotEmpty()) out.add(RichSegment.MathBlock(expr))
    cursor = match.range.last + 1
  }
  if (cursor < text.length) {
    val tail = text.substring(cursor).trim()
    if (tail.isNotEmpty()) out.add(RichSegment.TextBlock(tail))
  }
}
