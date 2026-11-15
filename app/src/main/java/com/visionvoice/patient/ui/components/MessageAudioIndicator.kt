package com.visionvoice.patient.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Dynamic Audio Waveform Bars (Oscillating Frequency Bars)
 * 4 vertical slender rounded bars with staggered oscillating heights.
 */
@Composable
fun MessageWaveformBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "msgWaveformTransition")

    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, delayMillis = 80, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 40, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )

    val factors = listOf(h1, h2, h3, h4)

    Canvas(modifier = modifier.size(width = 18.dp, height = 15.dp)) {
        val totalWidth = size.width
        val maxHeight = size.height
        val barWidth = 2.5.dp.toPx()
        val spacing = (totalWidth - (barWidth * factors.size)) / (factors.size - 1)

        factors.forEachIndexed { i, factor ->
            val barHeight = (maxHeight * factor).coerceAtLeast(3.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.65f + (0.35f * factor)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/**
 * Message Audio Indicator in the bottom-right of a chat bubble.
 * Displays a sleek speaker icon when idle, and seamlessly crossfades into
 * dynamic audio waveform bars while the message is being read aloud.
 */
@Composable
fun MessageAudioIndicator(
    isPlaying: Boolean,
    waveformColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .semantics {
                contentDescription = if (isPlaying) "Playing audio" else "Read aloud"
            }
    ) {
        Crossfade(
            targetState = isPlaying,
            label = "speaker_waveform_crossfade"
        ) { playing ->
            if (playing) {
                MessageWaveformBars(color = waveformColor)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
