package com.visionvoice.patient.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.visionvoice.patient.data.model.MessageSender
import com.visionvoice.patient.ui.components.AudioPlaybackCard
import com.visionvoice.patient.ui.components.LargePillButton
import com.visionvoice.patient.ui.components.MassiveVoiceActionButton
import com.visionvoice.patient.ui.components.VoiceOrbVisualizer
import com.visionvoice.patient.ui.theme.CanvasWarmWhite
import com.visionvoice.patient.ui.theme.ClinicalError
import com.visionvoice.patient.ui.theme.DarkAmber
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.ErrorContainer
import com.visionvoice.patient.ui.theme.OnErrorContainer
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
import com.visionvoice.patient.ui.theme.SurfaceTeal
import com.visionvoice.patient.ui.theme.WarmAmber
import com.visionvoice.patient.ui.viewmodel.ScreeningViewModel

/**
 * Screen 2: Voice-First AI Anamnesis (Stitch ID: 8730e38a3d6d4c3f9876fcb082fe3be2)
 * 10-turn adaptive conversational anamnesis powered by Gemini AI, Voice Orb Visualizer,
 * native SpeechRecognizer (STT) and TextToSpeech (TTS), and tactile quick response chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAnamnesisScreen(
    viewModel: ScreeningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTurn by viewModel.currentTurnIndex.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val transcript by viewModel.voiceTranscript.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()
    val profile by viewModel.profile.collectAsState()

    val totalTurns = viewModel.tenTurnQuestions.size
    val currentTitle = viewModel.getCurrentTurnTitle()
    val currentHints = viewModel.getCurrentTurnHints()

    // Retrieve the active question prompt (either latest from Gemini or the turn template)
    val activePromptText = profile.chatHistory
        .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
        ?: viewModel.getCurrentTurnPrompt()

    val scrollState = rememberScrollState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.toggleListening()
        }
    }

    val handleVoiceClick: () -> Unit = {
        if (hasAudioPermission) {
            viewModel.toggleListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
                        contentDescription = "Back to language selection",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Voice Anamnesis",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Turn ${currentTurn + 1} of $totalTurns",
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
            // 10-Step Progress Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(totalTurns) { idx ->
                    val isPast = idx < currentTurn
                    val isCurrent = idx == currentTurn
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(
                                when {
                                    isPast -> DeepMedicalTeal
                                    isCurrent -> WarmAmber
                                    else -> OutlineSlate.copy(alpha = 0.5f)
                                }
                            )
                    )
                }
            }

            // Current Clinical Question Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(2.dp, OutlineSlate),
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
                            color = SurfaceTeal,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = DeepMedicalTeal,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        if (isAiThinking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = WarmAmber,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Analyzing...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = DarkAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = activePromptText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = DeepSlateText,
                            fontSize = 22.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Guidance Playback Widget
            AudioPlaybackCard(
                title = "Assistant Speech",
                isPlaying = isSpeaking || isPlayingAudio,
                progress = audioProgress,
                onTogglePlay = { viewModel.toggleAudioPlayback() },
                onReplay = { viewModel.replayAudio() },
                onSeek = { viewModel.seekAudio(it) }
            )

            // Voice Error / Permission Alert if present
            if (voiceError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ErrorContainer,
                    border = BorderStroke(1.dp, ClinicalError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = OnErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = voiceError ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OnErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice Orb Visualizer & Real-time Transcript
            VoiceOrbVisualizer(
                isListening = isListening,
                isSpeaking = isSpeaking,
                transcriptText = transcript,
                onOrbClick = handleVoiceClick
            )

            // Massive 80x80dp Circular Voice Action Button
            MassiveVoiceActionButton(
                isListening = isListening,
                onClick = handleVoiceClick,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = when {
                    isAiThinking -> "Thinking & formulating next question..."
                    isSpeaking -> "Assistant is speaking..."
                    isListening -> "Listening... Tap to submit answer"
                    else -> "Tap microphone to speak"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    color = when {
                        isAiThinking || isListening -> DarkAmber
                        isSpeaking -> DeepMedicalTeal
                        else -> DeepSlateText
                    },
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Suggested Voice Response Chips (Tactile fallbacks for low-vision)
            Text(
                text = "Or tap a quick response:",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = DeepSlateText,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                currentHints.forEach { hint ->
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = SurfaceContainerLowest,
                        border = BorderStroke(2.dp, DeepMedicalTeal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable { viewModel.selectHintResponse(hint) }
                            .semantics {
                                contentDescription = "Quick reply: $hint"
                            }
                    ) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = DeepSlateText,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Navigation CTA
            LargePillButton(
                text = if (currentTurn >= totalTurns - 1) "Complete & View Clinical Summary" else "Review Summary So Far",
                onClick = onNavigateToSummary,
                containerColor = DeepMedicalTeal
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
