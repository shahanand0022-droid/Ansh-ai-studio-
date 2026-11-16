package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.RoyalGold

@Composable
fun PrinceLogoEmblem(
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(ElectricCyan.copy(alpha = 0.28f), Color.Transparent),
        center = Offset(w / 2f, h / 2f),
        radius = w * 0.65f,
      ),
      radius = w * 0.65f,
    )

    val crownPath = Path().apply {
      moveTo(w * 0.18f, h * 0.72f)
      lineTo(w * 0.14f, h * 0.34f)
      lineTo(w * 0.35f, h * 0.50f)
      lineTo(w * 0.50f, h * 0.22f)
      lineTo(w * 0.65f, h * 0.50f)
      lineTo(w * 0.86f, h * 0.34f)
      lineTo(w * 0.82f, h * 0.72f)
      close()
    }
    drawPath(
      path = crownPath,
      brush = Brush.linearGradient(listOf(RoyalGold, Color(0xFFFFE28A))),
    )

    drawLine(
      color = RoyalGold,
      start = Offset(w * 0.20f, h * 0.80f),
      end = Offset(w * 0.80f, h * 0.80f),
      strokeWidth = w * 0.08f,
      cap = StrokeCap.Round,
    )

    val prismPath = Path().apply {
      moveTo(w * 0.50f, h * 0.43f)
      lineTo(w * 0.58f, h * 0.57f)
      lineTo(w * 0.50f, h * 0.68f)
      lineTo(w * 0.42f, h * 0.57f)
      close()
    }
    drawPath(path = prismPath, color = ElectricCyan)

    drawCircle(color = ElectricCyan, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.15f))
    drawCircle(color = ElectricCyan, radius = w * 0.035f, center = Offset(w * 0.13f, h * 0.27f))
    drawCircle(color = ElectricCyan, radius = w * 0.035f, center = Offset(w * 0.87f, h * 0.27f))
  }
}

@Composable
fun StudioGlassCard(
  modifier: Modifier = Modifier,
  borderColor: Color = OutlineSubtle,
  containerColor: Color = CardSurface,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier,
      shape = RoundedCornerShape(18.dp),
      color = containerColor,
      border = BorderStroke(1.dp, borderColor),
      tonalElevation = 2.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  } else {
    Surface(
      modifier = modifier,
      shape = RoundedCornerShape(18.dp),
      color = containerColor,
      border = BorderStroke(1.dp, borderColor),
      tonalElevation = 2.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun QuotaProgressBar(
  label: String,
  used: Long,
  limit: Long,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val isUnlimited = limit < 0L
  val progress = if (isUnlimited || limit == 0L) 0.15f else (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        text = if (isUnlimited) "$used / ∞" else "$used / $limit",
        style = MaterialTheme.typography.labelSmall,
        color = accentColor,
      )
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .height(6.dp),
      color = accentColor,
      trackColor = OutlineSubtle.copy(alpha = 0.5f),
    )
  }
}

@Composable
fun SectionHeader(
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
      if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (trailing != null) trailing()
  }
}
