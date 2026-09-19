package org.seven_cgpalabs.shruti.core

import android.util.Log

class ShrutiAudioEngine {

    companion object {
        private const val TAG = "ShrutiAudioEngine"

        init {
            try {
                System.loadLibrary("shruti_native_engine")
                Log.i(TAG, "Native library 'shruti_native_engine' loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library 'shruti_native_engine'", e)
            }
        }
    }

    fun initialize(): Boolean {
        return nativeInitEngine()
    }

    fun pushAudioFrame(pcmData: ShortArray, sampleCount: Int): Int {
        return nativePushAudioFrame(pcmData, sampleCount)
    }

    fun popAudioFrame(outputBuffer: ShortArray, sampleCount: Int): Int {
        return nativePopAudioFrame(outputBuffer, sampleCount)
    }

    fun teardown() {
        nativeTeardownEngine()
    }

    private external fun nativeInitEngine(): Boolean
    private external fun nativePushAudioFrame(pcmData: ShortArray, sampleCount: Int): Int
    private external fun nativePopAudioFrame(outputBuffer: ShortArray, sampleCount: Int): Int
    private external fun nativeTeardownEngine()
}
