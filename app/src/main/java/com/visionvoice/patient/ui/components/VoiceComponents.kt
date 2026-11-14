package com.visionvoice.patient.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionvoice.patient.ui.theme.AgentOrangeGlass
import com.visionvoice.patient.ui.theme.AgentPulseOrange
import com.visionvoice.patient.ui.theme.AmberContainer
import com.visionvoice.patient.ui.theme.DarkAmber
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.HumanBlueGlass
import com.visionvoice.patient.ui.theme.HumanPulseBlue
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
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
            modifier = Modifier.size(36.dp)
        ) {
            if (isActive) {
                // Secondary outer pulsating wave
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(waveScale2)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f))
                )
                // Primary pulsating wave ring
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .scale(dynamicWaveScale)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.32f))
                )
            }
            // Circular Mic button
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
 * Stitch Design System - Voice Orb Visualizer
 * Idle: 72dp soft amber pill button (#FEF3C7) with Amber icon (#B45309).
 * Listening/Processing: Expands to 96dp spherical orb pulsing #D97706 with concentric ripple.
 */
@Composable
fun VoiceOrbVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean = false,
    transcriptText: String,
    rmsDb: Float = 0f,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = isListening || isSpeaking
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    // Dynamic audio level responsiveness when listening
    val voiceLevelFactor = if (isListening) (rmsDb.coerceIn(0f, 10f) / 10f) * 0.15f else 0f
    val dynamicScale = pulseScale + voiceLevelFactor

    val activeColor = if (isListening) WarmAmber else DeepMedicalTeal

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .size(128.dp)
        ) {
            // Concentric Ripple Ring when active
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .scale(dynamicScale)
                        .clip(CircleShape)
                        .background(activeColor.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(dynamicScale * 0.95f)
                        .clip(CircleShape)
                        .background(activeColor.copy(alpha = 0.35f))
                )
            }

            // Main Orb
            val orbSize = if (isActive) 96.dp else 76.dp
            val orbColor = when {
                isListening -> WarmAmber
                isSpeaking -> DeepMedicalTeal
                else -> AmberContainer
            }
            val iconColor = if (isActive) Color.White else DarkAmber
            val iconVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Mic

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(orbSize)
                    .clip(CircleShape)
                    .background(orbColor)
                    .border(
                        width = if (isActive) 3.dp else 2.dp,
                        color = if (isListening) DarkAmber else if (isSpeaking) DeepMedicalTeal else OutlineSlate,
                        shape = CircleShape
                    )
                    .clickable { onOrbClick() }
                    .semantics {
                        contentDescription = when {
                            isListening -> "Listening active, tap to stop recording"
                            isSpeaking -> "Assistant speaking, tap to stop"
                            else -> "Microphone ready, tap to speak"
                        }
                    }
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(if (isActive) 44.dp else 36.dp)
                )
            }
        }

        // Live Transcript Chip
        if (transcriptText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(2.dp, if (isListening) WarmAmber else OutlineSlate),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (isListening) WarmAmber else DeepMedicalTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isListening) "Listening to you..." else "Captured Response",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isListening) DarkAmber else DeepMedicalTeal,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = transcriptText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = DeepSlateText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stitch Design System - Massive Primary Action Button
 * Fixed 80dp x 80dp circular format. Deep Teal (#0F766E), white icon (36dp glyph),
 * framed with an outer 4dp concentric ring.
 */
@Composable
fun MassiveVoiceActionButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(96.dp)
            .border(
                width = 4.dp,
                color = if (isListening) WarmAmber else DeepMedicalTeal.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isListening) WarmAmber else DeepMedicalTeal)
                .clickable { onClick() }
                .semantics {
                    contentDescription = if (isListening) "Stop voice input" else "Start speaking"
                }
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

/**
 * Stitch Design System - Audio Playback Controls
 * Contained within a #F0FDFA card.
 * 16dp thick progress track in #CBD5E1 with #0F766E fill.
 * Controls have minimum 64dp hit area.
 */
@Composable
fun AudioPlaybackCard(
    title: String,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceTeal,
        border = androidx.compose.foundation.BorderStroke(2.dp, OutlineSlate),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Audio Guidance",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Text(
                    text = if (isPlaying) "Playing..." else "Audio Ready",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isPlaying) WarmAmber else DeepMedicalTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Progress Slider
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = DeepMedicalTeal,
                    activeTrackColor = DeepMedicalTeal,
                    inactiveTrackColor = OutlineSlate
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Controls with generous 64dp hit target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, OutlineSlate, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay audio prompt",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DeepMedicalTeal)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio prompt" else "Play audio prompt",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stitch Design System - Standard Massive Action Button
 * Strictly 64dp to 72dp height, full pill radius (9999px), label-xl (20sp).
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
            .height(68.dp)
            .shadow(4.dp, RoundedCornerShape(9999.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}
