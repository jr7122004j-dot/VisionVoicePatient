package com.visionvoice.patient.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionvoice.patient.ui.components.LargePillButton
import com.visionvoice.patient.ui.theme.CanvasWarmWhite
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
import com.visionvoice.patient.ui.theme.SurfaceTeal
import com.visionvoice.patient.ui.viewmodel.ScreeningViewModel
import com.visionvoice.patient.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screen 3: Secure QR Code Handoff (Stitch ID: 704b512a7be043829b7d9754eec48ea6)
 * High-contrast QR code container with 4dp Deep Medical Teal border, 24px quiet zone,
 * human-readable 6-character backup code, and offline clinic handoff instructions.
 */
@Composable
fun QrHandoffScreen(
    viewModel: ScreeningViewModel,
    onNavigateBack: () -> Unit,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profile.collectAsState()
    val scrollState = rememberScrollState()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(true) }

    LaunchedEffect(profile) {
        withContext(Dispatchers.Default) {
            val payload = profile.toHandoffPayload()
            val bitmap = QrCodeGenerator.generateQrBitmap(payload, size = 640)
            qrBitmap = bitmap
            isGenerating = false
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
                        contentDescription = "Back to summary",
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Clinic Handoff",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = DeepSlateText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Secure Ophthalmic Transfer",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DeepSlateText.copy(alpha = 0.75f)
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
            // Header Instruction
            Text(
                text = "Show to Clinic Staff",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = DeepSlateText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Hold this screen for the ophthalmic provider to scan. All triage symptoms are encrypted in this code.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = DeepSlateText.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // High-Contrast QR Code Container
            // Pure #FFFFFF canvas, 4dp solid #0F766E border, 24dp safe quiet zone
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceContainerLowest,
                border = BorderStroke(4.dp, DeepMedicalTeal),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(310.dp)
                    .semantics {
                        contentDescription = "High-contrast QR code for clinical transfer with reference code ${profile.backupCode}"
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp) // 24dp safe quiet zone
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = DeepMedicalTeal,
                            strokeWidth = 4.dp
                        )
                    } else if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Clinical handoff QR code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Error generating QR code",
                            style = MaterialTheme.typography.bodyMedium.copy(color = DeepSlateText)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Human-Readable 6-Character Backup Code in 24px bold monospace
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceTeal,
                border = BorderStroke(2.dp, DeepMedicalTeal.copy(alpha = 0.5f)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "MANUAL BACKUP CODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = DeepMedicalTeal,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.backupCode,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlateText
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Offline & Security Badges
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(2.dp, OutlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = DeepMedicalTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "100% Offline Compatible",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = DeepSlateText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = "This QR code embeds the full clinical assessment. No internet connection is needed for the clinic scanner to read it.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DeepSlateText.copy(alpha = 0.8f),
                            lineHeight = 26.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Done / Return Home CTA
            LargePillButton(
                text = "Done • Return to Home",
                onClick = onReturnHome,
                containerColor = DeepMedicalTeal
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(9999.dp),
                border = BorderStroke(2.dp, DeepMedicalTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = "Review Summary Details",
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
