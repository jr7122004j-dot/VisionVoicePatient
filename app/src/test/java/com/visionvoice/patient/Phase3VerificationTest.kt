package com.visionvoice.patient

import com.visionvoice.patient.data.model.FunctionalDominance
import com.visionvoice.patient.data.model.OccupationalDemand
import com.visionvoice.patient.data.model.UrgencyLevel
import com.visionvoice.patient.data.model.VisionProfile
import com.visionvoice.patient.util.QrPayloadCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3VerificationTest {

    @Test
    fun testVisionProfileDefaultsAndAdditionalInfo() {
        val profile = VisionProfile(
            additionalInformation = listOf("Prior branch injury 5 years ago", "Gritty sensation in evening"),
            functionalDominance = FunctionalDominance.NEAR_DOMINANT,
            occupationalDemand = OccupationalDemand(occupation = "Tailor", primaryDistance = "35 cm"),
            urgencyLevel = UrgencyLevel.ROUTINE
        )

        assertEquals(2, profile.additionalInformation.size)
        assertEquals("Prior branch injury 5 years ago", profile.additionalInformation[0])
        assertEquals(FunctionalDominance.NEAR_DOMINANT, profile.functionalDominance)
        assertEquals("Tailor", profile.occupationalDemand.occupation)
    }

    @Test
    fun testQrPayloadCompressorRoundTrip() {
        val profile = VisionProfile(
            additionalInformation = listOf("Diabetes for 5 years", "Evening glare"),
            refractiveSymptoms = listOf("Difficulty threading needle", "Frontal headaches"),
            pathologyIndicators = listOf("Occasional floaters")
        )

        val rawPayload = profile.toHandoffPayload()
        val compressed = profile.toCompressedHandoffPayload()

        assertTrue("Compressed payload must start with VV1: prefix", compressed.startsWith("VV1:"))
        assertTrue("Compressed size (${compressed.length}) should be significantly less than raw size (${rawPayload.length})",
            compressed.length < rawPayload.length)

        val decompressed = QrPayloadCompressor.decompress(compressed)
        assertNotNull(decompressed)
        assertTrue(decompressed.contains(profile.id))
        assertTrue(decompressed.contains(profile.backupCode))
        assertTrue(decompressed.contains("Diabetes for 5 years"))
    }

    @Test
    fun testGeminiAnamnesisTurnTitles() {
        val service = com.visionvoice.patient.ai.GeminiAnamnesisService("test-key")
        assertEquals(10, service.turnTitles.size)
        assertEquals("Occupation & Primary Needs", service.turnTitles[0])
        assertEquals("Summary & Completion", service.turnTitles[9])
    }
}
