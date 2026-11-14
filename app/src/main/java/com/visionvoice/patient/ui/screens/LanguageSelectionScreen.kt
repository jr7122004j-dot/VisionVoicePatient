package com.visionvoice.patient.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeScript: String,
    val regionNote: String
)

val SupportedLanguages = listOf(
    SupportedLanguage("hi", "Hindi", "हिन्दी", "आवाज़ और निर्देश"),
    SupportedLanguage("en", "English", "English (India)", "Clinical standard"),
    SupportedLanguage("bn", "Bengali", "বাংলা", "ভয়েস নির্দেশিকা"),
    SupportedLanguage("te", "Telugu", "తెలుగు", "వాయిస్ మరియు సూచనలు"),
    SupportedLanguage("ta", "Tamil", "தமிழ்", "குரல் வழிகாட்டுதல்"),
    SupportedLanguage("mr", "Marathi", "मराठी", "आवाज आणि सूचना"),
    SupportedLanguage("raj", "Rajasthani", "राजस्थानी", "आवाज़ अर निर्देश")
)

/**
 * Screen 4: Language & Onboarding (Stitch ID: c664f39780854ea6a3192ab31978ae42)
 * High-legibility landing screen allowing low-vision patients to select language
 * and enable auto-narrated voice screening.
 */
@Composable
fun LanguageSelectionScreen(
    viewModel: ScreeningViewModel,
    onNavigateToAnamnesis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profile.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = CanvasWarmWhite,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Clinical Badge
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = SurfaceTeal,
                border = BorderStroke(2.dp, DeepMedicalTeal.copy(alpha = 0.4f)),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = DeepMedicalTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VisionVoice Screening",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = DeepMedicalTeal,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Headline
            Text(
                text = "Welcome to VisionVoice",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = DeepSlateText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Self-administered ophthalmic pre-screening with conversational voice assistance.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = DeepSlateText.copy(alpha = 0.85f),
                    fontSize = 20.sp,
                    lineHeight = 30.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Audio Guidance Toggle Card (WCAG hit zone)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(2.dp, OutlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceTeal)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = DeepMedicalTeal,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Audio Guidance",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = DeepSlateText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Read questions aloud",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = DeepSlateText.copy(alpha = 0.75f)
                                )
                            )
                        }
                    }

                    Switch(
                        checked = profile.audioGuidanceEnabled,
                        onCheckedChange = { viewModel.setAudioGuidance(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DeepMedicalTeal,
                            uncheckedThumbColor = OutlineSlate,
                            uncheckedTrackColor = CanvasWarmWhite
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle spoken audio guidance"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Language Selection Section Header
            Text(
                text = "Select Language",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = DeepSlateText,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // High-Contrast Dropdown Selector
            var dropdownExpanded by remember { mutableStateOf(false) }
            val currentSelectedLang = SupportedLanguages.firstOrNull {
                profile.selectedLanguage.equals(it.displayName, ignoreCase = true) ||
                    profile.selectedLanguage.equals(it.nativeScript, ignoreCase = true)
            } ?: SupportedLanguages[0]

            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceTeal),
                    border = BorderStroke(2.5.dp, DeepMedicalTeal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clickable { dropdownExpanded = true }
                        .semantics {
                            role = Role.DropdownList
                            contentDescription = "Selected language: ${currentSelectedLang.displayName} (${currentSelectedLang.nativeScript}). Tap to change language."
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(DeepMedicalTeal)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = "${currentSelectedLang.displayName} (${currentSelectedLang.nativeScript})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = DeepSlateText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = currentSelectedLang.regionNote,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = DeepSlateText.copy(alpha = 0.75f),
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Open language options",
                            tint = DeepMedicalTeal,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(SurfaceContainerLowest, RoundedCornerShape(20.dp))
                        .border(2.dp, OutlineSlate, RoundedCornerShape(20.dp))
                ) {
                    SupportedLanguages.forEach { lang ->
                        val isItemActive = lang.displayName.equals(currentSelectedLang.displayName, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${lang.displayName} (${lang.nativeScript})",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = if (isItemActive) DeepMedicalTeal else DeepSlateText,
                                                fontWeight = if (isItemActive) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 18.sp
                                            )
                                        )
                                        Text(
                                            text = lang.regionNote,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = DeepSlateText.copy(alpha = 0.65f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    if (isItemActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = DeepMedicalTeal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.setLanguage(lang.displayName)
                                dropdownExpanded = false
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = DeepSlateText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isItemActive) SurfaceTeal else Color.Transparent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Primary 68dp Action CTA
            LargePillButton(
                text = "Begin Voice Screening",
                onClick = onNavigateToAnamnesis,
                containerColor = DeepMedicalTeal
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
