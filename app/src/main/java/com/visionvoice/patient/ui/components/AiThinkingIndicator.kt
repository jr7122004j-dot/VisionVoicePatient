package com.visionvoice.patient.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.visionvoice.patient.ui.theme.DarkAmber

/**
 * Liquid Floating Pearls (Staggered Breathing Dots)
 * 3 smooth amber dots that gently float upward in a staggered wave with breathing scale and luminous alpha.
 */
@Composable
fun LiquidFloatingDotsAnimation(
    modifier: Modifier = Modifier,
    color: Color = DarkAmber
) {
    val transition = rememberInfiniteTransition(label = "dotsTransition")

    val offset1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val offset2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val offset3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    val offsets = listOf(offset1, offset2, offset3)

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        offsets.forEach { offset ->
            val factor = (-offset / 7f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(0.85f + (0.35f * factor))
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.45f + (0.55f * factor)))
            )
        }
    }
}

/**
 * AI Assistant Thinking Indicator without an outer box.
 * Simply displays the floating liquid pearls directly in the chat layout.
 */
@Composable
fun AiThinkingBubble(
    modifier: Modifier = Modifier,
    color: Color = DarkAmber
) {
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "AI Assistant is formulating response"
            },
        contentAlignment = Alignment.CenterStart
    ) {
        LiquidFloatingDotsAnimation(color = color)
    }
}
