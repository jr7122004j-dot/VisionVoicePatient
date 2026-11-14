package com.visionvoice.patient.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionvoice.patient.ui.theme.AgentOrangeGlass
import com.visionvoice.patient.ui.theme.AgentPulseOrange
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.HumanBlueGlass
import com.visionvoice.patient.ui.theme.HumanPulseBlue
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceTeal
import com.visionvoice.patient.ui.theme.WarmAmber

/**
 * Pulsating Voice Indicator with glassmorphic glow.
 * Circular mic with pulsating wave:
 * Orange wave for AI Assistant (bottom-left), Blue wave for Human (bottom-right).
 */
@Composable
fun PulsatingVoicePill(
    isActive: Boolean,
    isHuman: Boolean,
    label: String,
    rmsDb: Float = 0f,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseWave")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.35f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.55f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale2"
    )

    val audioBoost = if (isActive && isHuman) (rmsDb.coerceIn(0f, 10f) / 10f) * 0.22f else 0f
    val dynamicWaveScale = waveScale1 + audioBoost

    val primaryColor = if (isHuman) HumanPulseBlue else AgentPulseOrange
    val glassColor = if (isHuman) HumanBlueGlass else AgentOrangeGlass

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(if (isActive) glassColor else SurfaceTeal.copy(alpha = 0.5f))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) primaryColor else OutlineSlate.copy(alpha = 0.6f),
                shape = RoundedCornerShape(9999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp)
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .scale(waveScale2)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.18f))
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .scale(dynamicWaveScale)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.35f))
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isActive) primaryColor else OutlineSlate)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isHuman) "Patient Microphone" else "Assistant Voice",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isActive) primaryColor else DeepSlateText.copy(alpha = 0.75f),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Audio Narration Card for reading clinical summary aloud.
 * Simple, accessible Play/Stop and Replay controls without artificial sliders.
 */
@Composable
fun AudioPlaybackCard(
    title: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceTeal,
        border = BorderStroke(1.5.dp, OutlineSlate),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = DeepMedicalTeal,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (isPlaying) "Playing audio..." else "Tap play to listen",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isPlaying) WarmAmber else DeepSlateText.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, OutlineSlate, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay audio summary",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DeepMedicalTeal)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard Action Button with WCAG compliant touch targets.
 */
@Composable
fun LargePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DeepMedicalTeal,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = OutlineSlate,
            disabledContentColor = DeepSlateText.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(3.dp, RoundedCornerShape(9999.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}
