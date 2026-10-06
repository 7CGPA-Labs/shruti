#include <jni.h>
#include <android/log.h>
#include "shruti_ring_buffer.hpp"
#include "shruti_vulkan_bridge.hpp"

#define LOG_TAG "ShrutiJniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static shruti::audio::AudioRingBuffer g_inbound_ring_buffer;
static shruti::audio::AudioRingBuffer g_outbound_ring_buffer;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeInitEngine(
    JNIEnv* env,
    jobject thiz
) {
    g_inbound_ring_buffer.clear_and_wipe();
    g_outbound_ring_buffer.clear_and_wipe();
    LOGI("S.H.R.U.T.I. Native Audio Engine Initialized Successfully.");
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativePushAudioFrame(
    JNIEnv* env,
    jobject thiz,
    jshortArray pcmData,
    jint sampleCount
) {
    if (pcmData == nullptr || sampleCount <= 0) return 0;

    jshort* body = env->GetShortArrayElements(pcmData, nullptr);
    const size_t pushed = g_inbound_ring_buffer.push(std::span<const int16_t>(body, sampleCount));
    env->ReleaseShortArrayElements(pcmData, body, JNI_ABORT);

    return static_cast<jint>(pushed);
}

JNIEXPORT jint JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativePopAudioFrame(
    JNIEnv* env,
    jobject thiz,
    jshortArray outputBuffer,
    jint sampleCount
) {
    if (outputBuffer == nullptr || sampleCount <= 0) return 0;

    jshort* body = env->GetShortArrayElements(outputBuffer, nullptr);
    const size_t popped = g_inbound_ring_buffer.pop(std::span<int16_t>(body, sampleCount));
    env->ReleaseShortArrayElements(outputBuffer, body, 0);

    return static_cast<jint>(popped);
}

JNIEXPORT jboolean JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeIsVulkanSupported(
    JNIEnv* env,
    jobject thiz
) {
    bool supported = shruti::compute::VulkanComputeEngine::instance().check_vulkan_support();
    return supported ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeInitVulkanBackend(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath
) {
    const char* pathStr = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(pathStr ? pathStr : "");
    env->ReleaseStringUTFChars(modelPath, pathStr);

    bool ok = shruti::compute::VulkanComputeEngine::instance().initialize(path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeExtractVectors(
    JNIEnv* env,
    jobject thiz,
    jshortArray pcmData,
    jint sampleCount,
    jfloatArray outSpeaker,
    jfloatArray outProsody
) {
    if (pcmData == nullptr || sampleCount <= 0 || outSpeaker == nullptr || outProsody == nullptr) {
        return JNI_FALSE;
    }

    jshort* pcmBody = env->GetShortArrayElements(pcmData, nullptr);
    jfloat* spkBody = env->GetFloatArrayElements(outSpeaker, nullptr);
    jfloat* prosBody = env->GetFloatArrayElements(outProsody, nullptr);

    bool res = shruti::compute::VulkanComputeEngine::instance().extract_vectors(
        pcmBody,
        static_cast<size_t>(sampleCount),
        spkBody,
        prosBody
    );

    env->ReleaseShortArrayElements(pcmData, pcmBody, JNI_ABORT);
    env->ReleaseFloatArrayElements(outSpeaker, spkBody, 0);
    env->ReleaseFloatArrayElements(outProsody, prosBody, 0);

    return res ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeSynthesizeDebrief(
    JNIEnv* env,
    jobject thiz,
    jfloatArray trajectoryMatrix,
    jint vectorCount,
    jshortArray outPcm
) {
    if (trajectoryMatrix == nullptr || vectorCount <= 0 || outPcm == nullptr) {
        return 0;
    }

    jsize maxSamples = env->GetArrayLength(outPcm);
    jfloat* matrixBody = env->GetFloatArrayElements(trajectoryMatrix, nullptr);
    jshort* pcmBody = env->GetShortArrayElements(outPcm, nullptr);

    int samplesProduced = shruti::compute::VulkanComputeEngine::instance().synthesize_debrief(
        matrixBody,
        static_cast<size_t>(vectorCount),
        pcmBody,
        static_cast<size_t>(maxSamples)
    );

    env->ReleaseFloatArrayElements(trajectoryMatrix, matrixBody, JNI_ABORT);
    env->ReleaseShortArrayElements(outPcm, pcmBody, 0);

    return static_cast<jint>(samplesProduced);
}

JNIEXPORT jboolean JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeSetSpeakerPreset(
    JNIEnv* env,
    jobject thiz,
    jfloatArray presetVector,
    jint dim
) {
    if (presetVector == nullptr || dim <= 0) return JNI_FALSE;

    jfloat* body = env->GetFloatArrayElements(presetVector, nullptr);
    bool ok = shruti::compute::VulkanComputeEngine::instance().set_speaker_preset(
        body,
        static_cast<size_t>(dim)
    );
    env->ReleaseFloatArrayElements(presetVector, body, JNI_ABORT);

    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeWipeMemory(
    JNIEnv* env,
    jobject thiz
) {
    shruti::compute::VulkanComputeEngine::instance().wipe_memory();
    g_inbound_ring_buffer.clear_and_wipe();
    g_outbound_ring_buffer.clear_and_wipe();
    LOGI("Volatile memory tensors wiped.");
}

JNIEXPORT void JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeTeardownEngine(
    JNIEnv* env,
    jobject thiz
) {
    shruti::compute::VulkanComputeEngine::instance().teardown();
    g_inbound_ring_buffer.clear_and_wipe();
    g_outbound_ring_buffer.clear_and_wipe();
    LOGI("S.H.R.U.T.I. Native Engine Memory Wiped & Torn Down.");
}

} // extern "C"
