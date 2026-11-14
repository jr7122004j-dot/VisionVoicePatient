package com.visionvoice.patient.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.visionvoice.patient.data.model.ChatMessage
import com.visionvoice.patient.data.model.MessageSender
import com.visionvoice.patient.ui.components.AppInfoDialog
import com.visionvoice.patient.ui.components.LanguageOnboardingDialog
import com.visionvoice.patient.ui.components.LargePillButton
import com.visionvoice.patient.ui.components.PulsatingVoicePill
import com.visionvoice.patient.ui.theme.AgentOrangeGlass
import com.visionvoice.patient.ui.theme.AgentPulseOrange
import com.visionvoice.patient.ui.theme.CanvasWarmWhite
import com.visionvoice.patient.ui.theme.ClinicalError
import com.visionvoice.patient.ui.theme.DarkAmber
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.ErrorContainer
import com.visionvoice.patient.ui.theme.HumanBlueGlass
import com.visionvoice.patient.ui.theme.HumanPulseBlue
import com.visionvoice.patient.ui.theme.OnErrorContainer
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
import com.visionvoice.patient.ui.theme.SurfaceTeal
import com.visionvoice.patient.ui.theme.WarmAmber
import com.visionvoice.patient.ui.viewmodel.ScreeningViewModel

/**
 * Screen 2: Voice-First AI Anamnesis
 * Clean unified chat conversation with AI Assistant on left, Human replies on right,
 * Orange pulsating voice wave on bottom-left, Blue pulsating voice wave on bottom-right,
 * and horizontally scrollable quick responses.
 */
@Composable
fun VoiceAnamnesisScreen(
    viewModel: ScreeningViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToSummary: () -> Unit,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTurn by viewModel.currentTurnIndex.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val transcript by viewModel.voiceTranscript.collectAsState()
    val rmsDb by viewModel.voiceRmsDb.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()
    val hasRecordedAudio by viewModel.hasRecordedAudio.collectAsState()
    val isPatientAudioPlaying by viewModel.isPatientAudioPlaying.collectAsState()
    val recordedAudioDurationMs by viewModel.recordedAudioDurationMs.collectAsState()
    val isConnectingToAi by viewModel.isConnectingToAi.collectAsState()
    val aiConnectionStatus by viewModel.aiConnectionStatus.collectAsState()
    val aiConnectionError by viewModel.aiConnectionError.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val showOnboardingDialog by viewModel.showOnboardingDialog.collectAsState()
    val isOnboardingCompleted = viewModel.isOnboardingCompleted

    val totalTurns = viewModel.turnTitles.size

    val listState = rememberLazyListState()
    var showAppInfoDialog by remember { mutableStateOf(false) }

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

    // Start anamnesis session upon entering the screen
    LaunchedEffect(Unit) {
        viewModel.startAnamnesis()
    }

    // Auto-scroll chat to latest message or thinking state
    LaunchedEffect(profile.chatHistory.size, transcript, isAiThinking) {
        val totalItems = profile.chatHistory.size +
            (if (isListening && transcript.isNotBlank()) 1 else 0) +
            (if (isAiThinking) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val handleVoiceClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            val currentLangObj = remember(profile.selectedLanguage) {
                SupportedLanguages.find { it.displayName.equals(profile.selectedLanguage, ignoreCase = true) }
                    ?: SupportedLanguages.first()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Left: Vision Voice Pill (clickable, opens info dialog)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBackButton) {
                        IconButton(
                            onClick = {
                                viewModel.stopAudio()
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceTeal)
                                .border(1.dp, DeepMedicalTeal.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DeepMedicalTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = SurfaceTeal,
                        border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .clickable { showAppInfoDialog = true }
                            .semantics {
                                contentDescription = "About Vision Voice"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 5.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.2f)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Vision Voice",
                                        tint = DeepMedicalTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Vision Voice",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = DeepMedicalTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }

                // Right: Language Selector Pill
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = SurfaceTeal,
                    border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .clickable {
                            viewModel.openLanguageSettingsDialog()
                        }
                        .semantics {
                            contentDescription = "Language settings: ${currentLangObj.displayName}"
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = DeepSlateText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentLangObj.code == "en") "English (India)" else "${currentLangObj.displayName} (${currentLangObj.nativeScript})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = DeepSlateText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Change language",
                            tint = DeepSlateText.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Sleek Continuous Progress Bar matching design mockup
            val progressFraction = ((currentTurn + 1).toFloat() / totalTurns.toFloat()).coerceIn(0.05f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                label = "screening_progress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(OutlineSlate.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(WarmAmber)
                )
            }

            // Error alert if present
            if (voiceError != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorContainer,
                    border = BorderStroke(1.dp, ClinicalError),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = OnErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = voiceError ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearVoiceError() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = OnErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 1. Single Box with Rounded Corners for AI Assistant & Human Chat
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(1.5.dp, OutlineSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Chat Message History (Assistant on left, Human on right)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val displayMessages = profile.chatHistory

                        items(displayMessages) { msg ->
                            val isAssistant = msg.sender == MessageSender.AI_ASSISTANT
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isAssistant) 4.dp else 16.dp,
                                        bottomEnd = if (isAssistant) 16.dp else 4.dp
                                    ),
                                    color = if (isAssistant) SurfaceTeal else DeepMedicalTeal,
                                    border = if (isAssistant) BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.25f)) else null,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = if (isAssistant) "AI Assistant" else "You",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isAssistant) DeepMedicalTeal else Color.White.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isAssistant) DeepSlateText else Color.White,
                                                fontSize = 15.sp,
                                                lineHeight = 21.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Real-time streaming speech preview
                        if (isListening && transcript.isNotBlank()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = HumanBlueGlass,
                                        border = BorderStroke(1.5.dp, HumanPulseBlue),
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "Listening...",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = HumanPulseBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = transcript,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = DeepSlateText,
                                                    fontStyle = FontStyle.Italic,
                                                    fontSize = 15.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // AI Thinking Indicator
                        if (isAiThinking) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SurfaceTeal,
                                        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.5f)),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = DarkAmber,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Analyzing & formulating next question...",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = DarkAmber,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 30-second Connecting to AI Popup Indicator
                    if (isConnectingToAi) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = WarmAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, WarmAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = DarkAmber,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = aiConnectionStatus ?: "Connecting to AI assistant...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = DarkAmber,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Internet Connectivity Error Banner with Retry
                    if (aiConnectionError != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ErrorContainer,
                            border = BorderStroke(1.dp, ClinicalError),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = OnErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = aiConnectionError ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OnErrorContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(
                                    onClick = { viewModel.retryConnection() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = ClinicalError
                                    )
                                ) {
                                    Text(
                                        text = "Retry",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissAiConnectionError() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss error",
                                        tint = OnErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Bar inside Chat Box: Circular Mic indicators
                    // 2. Agent speaking: circular mic on bottom-left with pulsating orange wave & glass effect
                    // 3. Human speaking: circular mic on bottom-right with pulsating blue wave
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainerLowest)
                            .border(BorderStroke(1.dp, OutlineSlate.copy(alpha = 0.4f)))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (hasRecordedAudio) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceTeal,
                                border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    // Play / Pause Circle Button
                                    IconButton(
                                        onClick = { viewModel.toggleRecordedAudioPlayback() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(DeepMedicalTeal)
                                    ) {
                                        Icon(
                                            imageVector = if (isPatientAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPatientAudioPlaying) "Pause My Audio" else "Play My Audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Status / Duration text
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.toggleRecordedAudioPlayback() }
                                    ) {
                                        Text(
                                            text = if (isPatientAudioPlaying) "Playing Your Audio..." else "Play My Audio",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = DeepMedicalTeal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                        val durSec = (recordedAudioDurationMs / 1000).coerceAtLeast(1)
                                        Text(
                                            text = if (transcript.isNotBlank()) "\"$transcript\"" else "Recorded voice • 0:${durSec.toString().padStart(2, '0')}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = DeepSlateText.copy(alpha = 0.75f),
                                                fontSize = 11.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    // Re-record Button
                                    IconButton(
                                        onClick = { viewModel.discardRecordedAudio() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(1.dp, OutlineSlate, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Record Again",
                                            tint = DeepSlateText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Send Button
                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = DeepMedicalTeal,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9999.dp))
                                            .clickable { viewModel.submitCurrentResponse() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Send",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Send to Assistant",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Left: Agent Speaking Indicator (Orange glass wave)
                                PulsatingVoicePill(
                                    isActive = isSpeaking || isPlayingAudio,
                                    isHuman = false,
                                    label = if (isSpeaking || isPlayingAudio) "Agent Speaking" else "Hear Voice",
                                    onClick = { viewModel.replayAudio() }
                                )

                                // Right: Human Speaking Indicator (Blue glass wave)
                                PulsatingVoicePill(
                                    isActive = isListening,
                                    isHuman = true,
                                    label = if (isListening) "Listening..." else "Tap to Speak",
                                    rmsDb = rmsDb,
                                    onClick = handleVoiceClick
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Review Summary So Far / Complete
            LargePillButton(
                text = if (currentTurn >= totalTurns - 1) "Complete & View Clinical Summary" else "Review Summary So Far",
                onClick = {
                    viewModel.stopAudio()
                    onNavigateToSummary()
                },
                containerColor = DeepMedicalTeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
    }

    if (showOnboardingDialog) {
        LanguageOnboardingDialog(
            initialLanguage = profile.selectedLanguage,
            initialAudioGuidance = profile.audioGuidanceEnabled,
            canDismiss = isOnboardingCompleted,
            onDismiss = {
                viewModel.dismissLanguageSettingsDialog()
            },
            onConfirm = { chosenLang, audioEnabled ->
                if (!isOnboardingCompleted) {
                    viewModel.completeOnboarding(chosenLang, audioEnabled)
                } else {
                    viewModel.updateLanguageSettings(chosenLang, audioEnabled)
                }
            }
        )
    }

    if (showAppInfoDialog) {
        AppInfoDialog(
            onDismiss = { showAppInfoDialog = false }
        )
    }
}
