#include <jni.h>
#include <android/log.h>
#include "shruti_ring_buffer.hpp"

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

JNIEXPORT void JNICALL
Java_org_seven_1cgpalabs_shruti_core_ShrutiAudioEngine_nativeTeardownEngine(
    JNIEnv* env,
    jobject thiz
) {
    g_inbound_ring_buffer.clear_and_wipe();
    g_outbound_ring_buffer.clear_and_wipe();
    LOGI("S.H.R.U.T.I. Native Engine Memory Wiped & Torn Down.");
}

} // extern "C"
