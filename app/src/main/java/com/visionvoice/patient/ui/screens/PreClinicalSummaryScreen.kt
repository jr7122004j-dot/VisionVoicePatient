package com.visionvoice.patient.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionvoice.patient.data.model.UrgencyLevel
import com.visionvoice.patient.ui.components.AudioPlaybackCard
import com.visionvoice.patient.ui.components.LargePillButton
import com.visionvoice.patient.ui.theme.AmberContainer
import com.visionvoice.patient.ui.theme.CanvasWarmWhite
import com.visionvoice.patient.ui.theme.ClinicalError
import com.visionvoice.patient.ui.theme.DarkAmber
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SuccessEmerald
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
import com.visionvoice.patient.ui.theme.SurfaceTeal
import com.visionvoice.patient.ui.theme.WarmAmber
import com.visionvoice.patient.ui.viewmodel.ScreeningViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen 1: Pre-Clinical Summary (Stitch ID: dbc89635f1e84b4ba4a73a4e2225eb31)
 * Comprehensive triage evaluation synthesized from patient 10-turn voice anamnesis,
 * presenting clinical urgency tier, functional vision dominance, 6 dimensions, and additional information.
 */
@Composable
fun PreClinicalSummaryScreen(
    viewModel: ScreeningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profile.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val scrollState = rememberScrollState()

    val formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        .format(Date(profile.timestamp))

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerLowest)
                        .border(2.dp, OutlineSlate, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to anamnesis",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Clinical Summary",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Ref: ${profile.backupCode}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DeepMedicalTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Triage Urgency Banner Card
            val (badgeBg, badgeText, badgeIcon) = when (profile.urgencyLevel) {
                UrgencyLevel.ROUTINE -> Triple(
                    SuccessEmerald.copy(alpha = 0.15f),
                    SuccessEmerald,
                    Icons.Default.CheckCircle
                )
                UrgencyLevel.PRIORITY -> Triple(
                    WarmAmber.copy(alpha = 0.18f),
                    DarkAmber,
                    Icons.Default.Warning
                )
                UrgencyLevel.URGENT -> Triple(
                    ClinicalError.copy(alpha = 0.18f),
                    ClinicalError,
                    Icons.Default.Warning
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(2.dp, badgeText),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = badgeBg,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = badgeText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = profile.urgencyLevel.title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = badgeText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = DeepSlateText.copy(alpha = 0.65f),
                                fontSize = 14.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "AI Pre-Clinical Triage",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = profile.aiTriageAssessment,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = DeepSlateText,
                            lineHeight = 30.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Functional Dominance Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceTeal,
                        border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = DeepMedicalTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Functional Vision Priority",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = DeepMedicalTeal,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${profile.functionalDominance.label} (${profile.functionalDominance.workingDistance})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = DeepSlateText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Summary Playback Card
            AudioPlaybackCard(
                title = "Summary Audio Narration",
                isPlaying = isPlayingAudio,
                progress = audioProgress,
                onTogglePlay = { viewModel.toggleAudioPlayback() },
                onReplay = { viewModel.replayAudio() },
                onSeek = { viewModel.seekAudio(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Clinical Findings Detail Card (6 Dimensions + Additional Information)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(2.dp, OutlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Patient-Reported Findings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    SummaryItemRow(
                        icon = Icons.Default.Work,
                        title = "Occupational Demands",
                        value = "${profile.occupationalDemand.occupation} • ${profile.occupationalDemand.primaryDistance}"
                    )

                    SummaryItemRow(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = "Primary & Refractive Symptoms",
                        value = (profile.refractiveSymptoms.ifEmpty { profile.symptoms }).joinToString(", ")
                    )

                    SummaryItemRow(
                        icon = Icons.Default.Warning,
                        title = "Environmental & Light Hazards",
                        value = (profile.environmentalHazards + listOf("Screen: ${profile.digitalAndLighting.dailyScreenHours}")).joinToString(", ")
                    )

                    SummaryItemRow(
                        icon = Icons.Default.HealthAndSafety,
                        title = "Systemic Medical History",
                        value = profile.systemicHistory.relevantNotes
                    )

                    if (profile.pathologyIndicators.isNotEmpty()) {
                        SummaryItemRow(
                            icon = Icons.Default.Warning,
                            title = "Pathology Red Flags",
                            value = profile.pathologyIndicators.joinToString(", ")
                        )
                    }

                    // Turn 9: Additional Information
                    if (profile.additionalInformation.isNotEmpty()) {
                        SummaryItemRow(
                            icon = Icons.Default.Info,
                            title = "Additional Information",
                            value = profile.additionalInformation.joinToString(" • ")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary CTA: Generate Secure QR Handoff
            LargePillButton(
                text = "Generate Secure QR Handoff",
                onClick = onNavigateToQr,
                containerColor = DeepMedicalTeal
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Retake screening button
            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(9999.dp),
                border = BorderStroke(2.dp, DeepMedicalTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = "Edit or Retake Anamnesis",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = DeepMedicalTeal,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SummaryItemRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceTeal)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeepMedicalTeal,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = DeepSlateText.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = DeepSlateText,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
