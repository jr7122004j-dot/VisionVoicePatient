package com.visionvoice.patient.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Utility for compressing and decompressing the pre-clinical handoff JSON payload.
 * Uses GZIP + URL-safe Base64 to shrink QR payload size by ~65-75%,
 * enabling rapid, crisp QR matrix scanning on entry-level Android phone cameras.
 */
object QrPayloadCompressor {
    const val PROTOCOL_PREFIX = "VV1:"

    fun compress(json: String): String {
        return try {
            val byteOut = ByteArrayOutputStream()
            GZIPOutputStream(byteOut).use { gzip ->
                gzip.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            val compressedBytes = byteOut.toByteArray()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP or Base64.URL_SAFE)
            "$PROTOCOL_PREFIX$base64"
        } catch (e: Exception) {
            // Graceful fallback to raw JSON
            json
        }
    }

    fun decompress(payload: String): String {
        return try {
            if (!payload.startsWith(PROTOCOL_PREFIX)) return payload
            val base64Data = payload.removePrefix(PROTOCOL_PREFIX)
            val compressedBytes = Base64.decode(base64Data, Base64.NO_WRAP or Base64.URL_SAFE)
            val byteIn = ByteArrayInputStream(compressedBytes)
            GZIPInputStream(byteIn).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        } catch (e: Exception) {
            payload
        }
    }
}
