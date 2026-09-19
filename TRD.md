# Technical Requirements Document (TRD)

---

### 1. System Engineering Stack & Target Environment

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **NDK Level:** NDK r26c+ (C++20 standard)
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) + CMake 3.22.1
* **ML Inference Framework:** ONNX Runtime Mobile v1.19+ with native JNI and C++ APIs
* **Hardware Acceleration Backends:**
  * Qualcomm QNN (HTP - Hexagon Tensor Processor) Execution Provider
  * MediaTek NeuroPilot / Android NNAPI Execution Provider
  * CPU Fallback: XNNPACK with ARM NEON SIMD optimizations
* **Telephony & Transport:** LiveKit Android WebRTC SDK v2.7+ over DoT-Compliant Carrier SIP DIDs / Android Telecom Framework (`ROLE_DIALER` 3-Action Caller UI)
* **Security & Storage:** SQLCipher v4.5.4 (FIPS 140-2 compliant AES-256) + Android Keystore StrongBox

---

### 2. Neural Architecture & Model Execution Matrix

#### 2.1 Model Execution Matrix

| Component | Model Architecture | Parameters | Quantization | Runtime EP | RAM Footprint | Inference Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 | CPU (XNNPACK) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Acoustic Neural Codec** | Mimi / WavTokenizer | 75 M | INT8 | QNN HTP / NNAPI | ~82 MB | < 12 ms / frame |
| **Speech-to-Speech SLM** | Mini-S2S / Llama-3.2-1B-Audio | 1.1 B | INT4 (AWQ/GPTQ) | QNN HTP NPU | ~620 MB | < 25 ms / token |
| **Semantic Vector Encoder** | CLAP / WavLM Latent Head | 45 M | INT8 | QNN / CPU | ~48 MB | < 45 ms / utterance |

#### 2.2 S2S SLM Mandatory AI Disclosure System Prompt Contract
```
System Prompt: "You are S.H.R.U.T.I., an automated AI voice assistant screening a call for [User Name]. Your very first sentence to the caller MUST strictly be: 'I am an automated voice assistant screening this call for [User Name]. Please state the reason for your call.' Do not deviate from this disclosure."
```

---

### 3. Telephony Architecture & 3-Action Call State Machine

#### 3.1 3-Action Incoming Call State Machine (`ShrutiInCallActivity.kt`)
When `InCallService` signals an incoming ringing state:
```kotlin
enum class IncomingCallAction {
    DECLINE,                 // Terminates call immediately
    ANSWER,                  // Answers call directly to earpiece/speaker
    DELEGATE_AI_SCREENER     // Accepts call in AI bridge mode & launches screening overlay
}

fun onUserCallAction(action: IncomingCallAction, call: Call) {
    when (action) {
        IncomingCallAction.DECLINE -> {
            call.reject(false, null)
        }
        IncomingCallAction.ANSWER -> {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        IncomingCallAction.DELEGATE_AI_SCREENER -> {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            ShrutiAudioBridge.startAiScreeningSession(call)
            ShrutiOverlayManager.showGeminiScreeningOverlay()
        }
    }
}
```

#### 3.2 Emergency Call Pass-Through (`ROLE_DIALER`)
```kotlin
fun evaluateCallInterception(phoneNumber: String): CallAction {
    if (PhoneNumberUtils.isEmergencyNumber(phoneNumber) || phoneNumber in listOf("112", "911", "100", "101", "102")) {
        return CallAction.EMERGENCY_PASS_THROUGH // Zero AI interception, direct PSTN route
    }
    return TraiPrefixMatcher.evaluate(phoneNumber)
}
```

#### 3.3 Human-in-the-Loop 1909 Intent Builder (`Trai1909Reporter.kt`)
```kotlin
data class TraiViolationReport(
    val callerNumber: String,
    val callTimestampMs: Long,
    val extractedCategory: CommercialCategory
) {
    fun to1909SmsBody(): String {
        val sdf = SimpleDateFormat("dd/MM/yy,HH:mm", Locale.US)
        val formattedDate = sdf.format(Date(callTimestampMs))
        return "${extractedCategory.description}, $callerNumber, $formattedDate"
    }

    fun buildSystemSmsIntent(): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:1909")
            putExtra("sms_body", to1909SmsBody())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
```

---

### 4. Cryptographic Storage & Native DSP Engine

```cpp
// Secure volatile memory wiper
void wipe_pcm_frame(int16_t* buffer, size_t sample_count) {
    if (buffer == nullptr || sample_count == 0) return;
    volatile int16_t* p = buffer;
    while (sample_count--) {
        *p++ = 0;
    }
    #if defined(__STDC_LIB_EXT1__)
    memset_s(buffer, sample_count * sizeof(int16_t), 0, sample_count * sizeof(int16_t));
    #else
    __asm__ __volatile__("" : : "r"(buffer) : "memory");
    #endif
}
```
