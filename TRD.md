# Technical Requirements Document (TRD)

---

### 1. System Engineering Stack & Target Environment

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **NDK Level:** NDK r26c+ (C++20 standard)
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) + CMake 3.22.1
* **ML Inference Framework:** ONNX Runtime Mobile v1.19+ with native JNI and C++ APIs
* **S2S Language Model:** `Llama-3.2-1B-Audio-Instruct` (Quantized via INT4 AWQ)
* **Hardware Acceleration Backends:**
  * Qualcomm QNN (HTP - Hexagon Tensor Processor) Execution Provider
  * MediaTek NeuroPilot / Android NNAPI Execution Provider
  * CPU Fallback: XNNPACK with ARM NEON SIMD optimizations
* **Telephony & Transport:** LiveKit Android WebRTC SDK v2.7+ over DoT-Compliant Carrier SIP DIDs / Android Telecom Framework (`ROLE_DIALER` 3-Action Caller UI)
* **Security & Storage:** SQLCipher v4.5.4 (FIPS 140-2 compliant AES-256) + Android Keystore StrongBox

---

### 2. Neural Architecture & Model Execution Matrix

```
                             ┌────────────────────────────────────────┐
                             │       INCOMING AUDIO (16kHz PCM)       │
                             └───────────────────┬────────────────────┘
                                                 │
                                                 ▼
                             ┌────────────────────────────────────────┐
                             │      Silero VAD v5 (ARM NEON CPU)      │
                             │        Buffer Chunk: 32ms (512 samples) │
                             └───────────────────┬────────────────────┘
                                                 │
                        [Voice Detected] ────────┴──────── [Silence / Noise]
                               │                                   │
                               ▼                                   ▼
          ┌────────────────────────────────────────┐        [Discard Buffer]
          │   Mimi / WavTokenizer Neural Codec     │        [Zero Memory]
          │    Encoder (INT8 ONNX / QNN HTP)       │
          └────────────────────┬───────────────────┘
                               │
                        (Discrete Tokens)
                               │
                               ▼
          ┌────────────────────────────────────────┐
          │  Llama-3.2-1B-Audio-Instruct S2S SLM   │
          │    Quantized INT4 AWQ (QNN HTP NPU)    │
          │    * Enforces Mandatory AI Disclosure  │
          └────────────────────┬───────────────────┘
                               │
                       (Synthesis Tokens)
                               │
                               ▼
          ┌────────────────────────────────────────┐
          │   Mimi / WavTokenizer Neural Codec     │
          │    Decoder (INT8 ONNX / QNN HTP)       │
          └────────────────────┬───────────────────┘
                               │
                               ▼
          ┌────────────────────────────────────────┐
          │      OUTGOING AUDIO (16kHz PCM)        │
          │       Pushed to LiveKit SIP Sink       │
          └────────────────────────────────────────┘
```

#### 2.1 Model Registry Specifications

| Component | Model Architecture | Parameters | Quantization | Runtime EP | RAM Footprint | Inference Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 | CPU (XNNPACK) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Acoustic Neural Codec** | Mimi / WavTokenizer | 75 M | INT8 | QNN HTP / NNAPI | ~82 MB | < 12 ms / frame |
| **Speech-to-Speech SLM** | `Llama-3.2-1B-Audio-Instruct` | 1.1 B | INT4 (AWQ) | QNN HTP NPU | ~620 MB | < 25 ms / token |
| **Semantic Vector Encoder** | CLAP / WavLM Latent Head | 45 M | INT8 | QNN / CPU | ~48 MB | < 45 ms / utterance |

#### 2.2 S2S SLM Mandatory AI Disclosure System Prompt Contract
System prompt contract for `Llama-3.2-1B-Audio-Instruct`:
```
System Prompt: "You are S.H.R.U.T.I., an automated AI voice assistant powered by Llama-3.2-1B-Audio screening a call for [User Name]. Your very first sentence to the caller MUST strictly be: 'I am an automated voice assistant screening this call for [User Name]. Please state the reason for your call.' Do not deviate from this disclosure."
```

---

### 3. Telephony Architecture & Regulatory Compliance Engines

#### 3.1 3-Action Incoming Call State Machine (`ShrutiInCallActivity.kt`)
When `InCallService` signals an incoming ringing state:
```kotlin
enum class IncomingCallAction {
    DECLINE,                 // Terminates call immediately
    ANSWER,                  // Answers call directly to earpiece/speaker
    DELEGATE_AI_SCREENER     // Accepts call in AI bridge mode & launches Llama-3.2-1B-Audio screening overlay
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
