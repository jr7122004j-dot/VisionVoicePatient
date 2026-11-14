package com.visionvoice.patient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.visionvoice.patient.ui.screens.SupportedLanguages
import com.visionvoice.patient.ui.theme.DeepMedicalTeal
import com.visionvoice.patient.ui.theme.DeepSlateText
import com.visionvoice.patient.ui.theme.OutlineSlate
import com.visionvoice.patient.ui.theme.SurfaceContainerLowest
import com.visionvoice.patient.ui.theme.SurfaceTeal

/**
 * Modern Glassmorphic Onboarding & Language Dialog
 *
 * Requirements:
 * 1. Rounded corner with pure white fill background (28.dp corners).
 * 2. Glass-like translucent backdrop surrounding the popup card.
 * 3. Text size matching message bubble styling (15.sp, 21.sp line-height).
 * 4. Fresh, modern aesthetic with accessible tactile controls.
 */
@Composable
fun LanguageOnboardingDialog(
    initialLanguage: String,
    initialAudioGuidance: Boolean,
    canDismiss: Boolean = false,
    onDismiss: () -> Unit = {},
    onConfirm: (selectedLanguage: String, audioGuidance: Boolean) -> Unit
) {
    var selectedLanguageName by remember { mutableStateOf(initialLanguage) }
    var audioGuidanceEnabled by remember { mutableStateOf(initialAudioGuidance) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val currentSupportedLanguage = remember(selectedLanguageName) {
        SupportedLanguages.find { it.displayName.equals(selectedLanguageName, ignoreCase = true) }
            ?: SupportedLanguages.first()
    }

    Dialog(
        onDismissRequest = {
            if (canDismiss) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss
        )
    ) {
        // Translucent glass-like backdrop surrounding the popup
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x660F766E), // Frosted Deep Medical Teal highlight
                            Color(0x990A1926), // Translucent deep slate
                            Color(0xCC050D15)  // Ambient edge vignette
                        ),
                        radius = 1200f
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canDismiss
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            // Popup card: Pure white fill with rounded corners (28.dp)
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // Prevent backdrop dismissal when clicking card body
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header Bar: Icon badge + Title + Optional Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceTeal,
                            border = BorderStroke(1.dp, DeepMedicalTeal.copy(alpha = 0.35f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = DeepMedicalTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Language & Audio",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSlateText
                                )
                            )
                            Text(
                                text = "Personalize your voice screening",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DeepMedicalTeal
                                )
                            )
                        }

                        if (canDismiss) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close dialog",
                                    tint = DeepSlateText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Intro description (15.sp matching message bubbles)
                    Text(
                        text = "Choose your preferred language and voice guidance. VisionVoice AI will adapt all spoken questions accordingly.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = DeepSlateText.copy(alpha = 0.8f)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 1: Preferred Language Selector
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepSlateText
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isDropdownExpanded) 180f else 0f,
                        label = "chevron_rotation"
                    )

                    // Integrated Expandable Language Selector (Seamless Accordion matching Card UI)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(
                                width = if (isDropdownExpanded) 1.5.dp else 1.dp,
                                color = if (isDropdownExpanded) DeepMedicalTeal else OutlineSlate,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .animateContentSize()
                    ) {
                        // Header / Trigger row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDropdownExpanded) SurfaceTeal else Color(0xFFF8FAFC))
                                .clickable { isDropdownExpanded = !isDropdownExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .semantics {
                                    contentDescription = "Language selector: ${currentSupportedLanguage.displayName}"
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentSupportedLanguage.nativeScript,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = DeepMedicalTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "•  ${currentSupportedLanguage.displayName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = DeepSlateText,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (isDropdownExpanded) "Close language options" else "Open language options",
                                tint = DeepMedicalTeal,
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(chevronRotation)
                            )
                        }

                        // Expanded options list
                        AnimatedVisibility(
                            visible = isDropdownExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(OutlineSlate.copy(alpha = 0.6f))
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    SupportedLanguages.forEachIndexed { index, lang ->
                                        val isSelected = lang.displayName.equals(selectedLanguageName, ignoreCase = true)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) SurfaceTeal else Color.White
                                                )
                                                .clickable {
                                                    selectedLanguageName = lang.displayName
                                                    isDropdownExpanded = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = lang.nativeScript,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) DeepMedicalTeal else DeepSlateText,
                                                        fontSize = 16.sp
                                                    )
                                                )
                                                Text(
                                                    text = "${lang.displayName} • ${lang.regionNote}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (isSelected) DeepMedicalTeal.copy(alpha = 0.85f) else DeepSlateText.copy(alpha = 0.65f),
                                                        fontSize = 13.sp
                                                    )
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = DeepMedicalTeal,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        if (index < SupportedLanguages.lastIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(0.5.dp)
                                                    .padding(horizontal = 16.dp)
                                                    .background(OutlineSlate.copy(alpha = 0.35f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 2: Audio Guidance Switch
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, OutlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = DeepMedicalTeal,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Spoken Audio Guidance",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DeepSlateText
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Speak questions automatically for low-vision assistance",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = DeepSlateText.copy(alpha = 0.7f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = audioGuidanceEnabled,
                                onCheckedChange = { audioGuidanceEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = DeepMedicalTeal,
                                    uncheckedThumbColor = DeepSlateText.copy(alpha = 0.6f),
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Section 3: Primary CTA Button (Begin Voice Screening)
                    Button(
                        onClick = {
                            onConfirm(selectedLanguageName, audioGuidanceEnabled)
                        },
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepMedicalTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Begin Voice Screening",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
