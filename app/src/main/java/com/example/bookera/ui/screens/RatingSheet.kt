package com.example.bookera.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Storage retains the existing 0..5 scale; UI offers a precise, comfortable 1..10 scale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingSheet(title: String, storedRating: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(if (storedRating > 0f) (storedRating * 2f).coerceIn(1f, 10f) else 8f) }
    val haptic = LocalHapticFeedback.current
    val tint by animateColorAsState(if (value < 5) Color(0xFFE88471) else if (value < 8) Electric else Color(0xFF477D32), label = "ratingColor")
    fun select(next: Float) {
        val rounded = next.toInt().toFloat().coerceIn(1f, 10f)
        if (rounded != value) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        value = rounded
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HomePaper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Как вам книга?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
            Surface(color = tint.copy(alpha = .12f), shape = CircleShape, modifier = Modifier.padding(top = 24.dp).size(112.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(value.toInt(), label = "rating") { number -> Text(number.toString(), fontSize = 56.sp, fontWeight = FontWeight.Black, color = tint) }
                }
            }
            Text(when (value.toInt()) { in 1..3 -> "Не моя история"; in 4..6 -> "Есть хорошие моменты"; in 7..8 -> "Хочется рекомендовать"; else -> "В самое сердце" },
                color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
            Slider(value, ::select, valueRange = 1f..10f, steps = 8, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint))
            (0..1).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { column ->
                    val score = row * 5 + column
                    FilledTonalButton(onClick = { select(score.toFloat()) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (score == value.toInt()) tint else MaterialTheme.colorScheme.surface,
                            contentColor = if (score == value.toInt()) Color.White else HomeInk)) { Text(score.toString(), fontWeight = FontWeight.Bold) }
                }
            }; Spacer(Modifier.height(8.dp)) }
            Button(onClick = { onSave(value / 2f) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp)) { Text("Сохранить оценку", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { onSave(0f) }) { Text("Убрать оценку") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
