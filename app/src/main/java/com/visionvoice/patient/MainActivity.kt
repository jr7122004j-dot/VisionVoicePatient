package com.visionvoice.patient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visionvoice.patient.ui.screens.PreClinicalSummaryScreen
import com.visionvoice.patient.ui.screens.QrHandoffScreen
import com.visionvoice.patient.ui.screens.VoiceAnamnesisScreen
import com.visionvoice.patient.ui.theme.VisionVoicePatientTheme
import com.visionvoice.patient.ui.viewmodel.ScreeningViewModel

object VisionVoiceRoutes {
    const val LANGUAGE_SELECTION = "language_selection"
    const val VOICE_ANAMNESIS = "voice_anamnesis"
    const val PRE_CLINICAL_SUMMARY = "pre_clinical_summary"
    const val QR_HANDOFF = "qr_handoff"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionVoicePatientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VisionVoiceAppNavigation()
                }
            }
        }
    }
}

@Composable
fun VisionVoiceAppNavigation(
    viewModel: ScreeningViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = VisionVoiceRoutes.VOICE_ANAMNESIS
    ) {
        composable(VisionVoiceRoutes.VOICE_ANAMNESIS) {
            VoiceAnamnesisScreen(
                viewModel = viewModel,
                showBackButton = false,
                onNavigateToSummary = {
                    navController.navigate(VisionVoiceRoutes.PRE_CLINICAL_SUMMARY)
                }
            )
        }

        composable(VisionVoiceRoutes.PRE_CLINICAL_SUMMARY) {
            PreClinicalSummaryScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToQr = {
                    navController.navigate(VisionVoiceRoutes.QR_HANDOFF)
                }
            )
        }

        composable(VisionVoiceRoutes.QR_HANDOFF) {
            QrHandoffScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onReturnHome = {
                    viewModel.resetScreening()
                    navController.navigate(VisionVoiceRoutes.VOICE_ANAMNESIS) {
                        popUpTo(VisionVoiceRoutes.VOICE_ANAMNESIS) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}
