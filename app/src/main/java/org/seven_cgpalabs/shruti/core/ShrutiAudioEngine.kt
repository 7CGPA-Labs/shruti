package org.seven_cgpalabs.shruti.core

import android.util.Log

/**
 * Core Audio & Neural Runtime Engine for S.H.R.U.T.I.
 * Interfaces with native C++ Vulkan compute backend (llama.cpp / GGML)
 * and lockless circular audio ring buffers.
 */
class ShrutiAudioEngine {

    companion object {
        private const val TAG = "ShrutiAudioEngine"
        const val SPEAKER_DIM = 192
        const val PROSODY_DIM = 64
        const val INTENT_DIM = 512
        const val SAMPLE_RATE = 16000

        init {
            try {
                System.loadLibrary("shruti_native_engine")
                Log.i(TAG, "Native library 'shruti_native_engine' loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library 'shruti_native_engine'", e)
            }
        }
    }

    enum class SpeakerPreset(val id: String, val displayName: String, val description: String) {
        ADITI("aditi", "Aditi", "Warm, patient, polite (Delivery & Logistics debriefs)"),
        AGASTYA("agastya", "Agastya", "Firm, measured, authoritative (Spam & Telemarketing alerts)"),
        PRIYA("priya", "Priya", "Neutral, gentle, clear (Kannada / Regional debriefs)"),
        KABIR("kabir", "Kabir", "Friendly, relaxed, conversational (Everyday standard debriefs)")
    }

    private var isInitialized = false
    private var isVulkanActive = false

    fun initialize(modelPath: String = ""): Boolean {
        val ringInit = nativeInitEngine()
        isVulkanActive = nativeIsVulkanSupported()
        Log.i(TAG, "Vulkan 1.1+ GPU support detected: $isVulkanActive")

        if (modelPath.isNotEmpty()) {
            val backendInit = nativeInitVulkanBackend(modelPath)
            Log.i(TAG, "Vulkan backend initialized: $backendInit for model $modelPath")
        }

        isInitialized = ringInit
        return ringInit
    }

    fun isVulkanSupported(): Boolean {
        return nativeIsVulkanSupported()
    }

    fun pushAudioFrame(pcmData: ShortArray, sampleCount: Int): Int {
        return nativePushAudioFrame(pcmData, sampleCount)
    }

    fun popAudioFrame(outputBuffer: ShortArray, sampleCount: Int): Int {
        return nativePopAudioFrame(outputBuffer, sampleCount)
    }

    /**
     * Extracts 192-d speaker identity and 64-d prosody vectors from 16kHz PCM audio chunk.
     */
    fun extractDualVectors(pcmChunk: ShortArray): Pair<FloatArray, FloatArray>? {
        if (pcmChunk.isEmpty()) return null
        val speakerVec = FloatArray(SPEAKER_DIM)
        val prosodyVec = FloatArray(PROSODY_DIM)

        val ok = nativeExtractVectors(pcmChunk, pcmChunk.size, speakerVec, prosodyVec)
        return if (ok) Pair(speakerVec, prosodyVec) else null
    }

    /**
     * Synthesizes expressive spoken debrief audio from decrypted trajectory matrix
     * via Qwen3-Omni backbone + SNAC audio heads on Vulkan GPU.
     */
    fun synthesizeDebrief(trajectoryMatrix: List<FloatArray>): ShortArray {
        if (trajectoryMatrix.isEmpty()) return ShortArray(0)

        val vectorCount = trajectoryMatrix.size
        val flatMatrix = FloatArray(vectorCount * INTENT_DIM)
        for (i in 0 until vectorCount) {
            val vec = trajectoryMatrix[i]
            val copyLen = minOf(vec.size, INTENT_DIM)
            System.arraycopy(vec, 0, flatMatrix, i * INTENT_DIM, copyLen)
        }

        // Allocate buffer for up to 10 seconds of 16 kHz audio (160,000 samples)
        val outPcm = ShortArray(SAMPLE_RATE * 10)
        val actualSamples = nativeSynthesizeDebrief(flatMatrix, vectorCount, outPcm)

        return if (actualSamples > 0) {
            val result = ShortArray(actualSamples)
            System.arraycopy(outPcm, 0, result, 0, actualSamples)
            result
        } else {
            ShortArray(0)
        }
    }

    /**
     * Sets the narrator voice preset embedding (Aditi, Agastya, Priya, Kabir).
     */
    fun setSpeakerPreset(presetVector: FloatArray): Boolean {
        return nativeSetSpeakerPreset(presetVector, presetVector.size)
    }

    /**
     * Wipes volatile memory, intermediate tensors, and audio ring buffers.
     */
    fun wipeMemory() {
        nativeWipeMemory()
    }

    fun teardown() {
        nativeTeardownEngine()
        isInitialized = false
    }

    // Native JNI Bindings
    private external fun nativeInitEngine(): Boolean
    private external fun nativePushAudioFrame(pcmData: ShortArray, sampleCount: Int): Int
    private external fun nativePopAudioFrame(outputBuffer: ShortArray, sampleCount: Int): Int
    private external fun nativeIsVulkanSupported(): Boolean
    private external fun nativeInitVulkanBackend(modelPath: String): Boolean
    private external fun nativeExtractVectors(
        pcmData: ShortArray,
        sampleCount: Int,
        outSpeaker: FloatArray,
        outProsody: FloatArray
    ): Boolean
    private external fun nativeSynthesizeDebrief(
        trajectoryMatrix: FloatArray,
        vectorCount: Int,
        outPcm: ShortArray
    ): Int
    private external fun nativeSetSpeakerPreset(presetVector: FloatArray, dim: Int): Boolean
    private external fun nativeWipeMemory()
    private external fun nativeTeardownEngine()
}
