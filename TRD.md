# Technical Requirements Document (TRD)

---

### 1. System Engineering Stack & Target Environment

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **NDK Level:** NDK r26c+ (C++20 standard)
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) + CMake 3.22.1
* **ML Inference Framework:** LiteRT (TensorFlow Lite v2.16+) with GPU (OpenCL) & Qualcomm QNN HTP NPU delegates + ONNX Runtime Mobile v1.19+ fallback
* **SLM / Debrief Engine:** `Qwen3-Omni-3B` (Pruned to ~1.45B params, quantized via INT4 AWQ) / Local Kokoro / Android TTS
* **Hardware Acceleration Backends:**
  * **NPU:** Qualcomm QNN HTP (Hexagon Tensor Processor) / MediaTek NeuroPilot APU (Tier 1 Dual-Head Vectorizer)
  * **GPU:** Qualcomm Adreno / ARM Mali via OpenCL (Tier 2 Streaming Whisper / SLM Encoder)
  * **CPU:** ARM NEON SIMD with XNNPACK (Tier 3 Silero VAD v5 + C++ Lockless Ring Buffer)
* **Telephony & Ingress:** Android Telecom Framework (`ROLE_DIALER` Default Dialer UI) / `InCallService` + `VOICE_COMMUNICATION` passive dual-channel ingress
* **Storage & Serialization:** Zero-Copy FlatBuffers (`.vecstream`) + SQLCipher v4.5.4 (FIPS 140-2 compliant AES-256-GCM) + Android Keystore StrongBox

---

### 2. Neural Architecture & Model Execution Matrix

```
                             ┌────────────────────────────────────────┐
                             │       INCOMING AUDIO (16kHz PCM)       │
                             │       (In-Call Audio or Ambient Mic)   │
                             └───────────────────┬────────────────────┘
                                                 │
                                                 ▼
                             ┌────────────────────────────────────────┐
                             │      Silero VAD v5 (ARM NEON CPU)      │
                             │      Buffer Chunk: 32ms (512 samples)  │
                             └───────────────────┬────────────────────┘
                                                 │
                        [Voice Detected] ────────┴──────── [Silence / Noise]
                               │                                   │
                               ▼                                   ▼
          ┌────────────────────────────────────────┐        [Zero Memory]
          │   Lock-Free SPSC DSP Ring Buffer       │        (memset_s)
          │   (50 frames / 500ms Mel Spectrogram)  │
          └────────────────────┬───────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼ (Mel: 1, 50, 80)                    ▼ (Acoustic Waveform)
┌────────────────────────────────────────┐ ┌────────────────────────────────────────┐
│ Tier 1: LiteRT NPU Dual-Head Vectorizer│ │ Tier 2: LiteRT GPU (OpenCL) Whisper    │
│ * 192-d Speaker d-vector (e_spk)       │ │ * 128-d Semantic Intent (w_sem)        │
│ * 64-d Prosody/Energy Dynamics (z_pros)│ │ * 1-d Pause Delta (Δ_pause)            │
│ INT8 QAT (~418 KB, < 1.8 ms)           │ │ INT8 OpenCL (~38 MB, < 18 ms)          │
└──────────────────┬─────────────────────┘ └───────────────────┬────────────────────┘
                   │                                           │
                   └─────────────────────┬─────────────────────┘
                                         │ Composite Vector vt ∈ R^384
                                         ▼
                         ┌───────────────────────────────┐
                         │ FlatBuffers .vecstream Serial │
                         │ * Memory-mapped zero-copy     │
                         └───────────────┬───────────────┘
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │ SQLCipher AES-256 Storage     │
                         │ * Volatile RAM scrub (memset) │
                         └───────────────┬───────────────┘
                                         │
                   (Post-Session Spoken Debrief / User Voice Query)
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │ Qwen3-Omni-3B Debrief SLM     │
                         │ * Reconstructs conversation   │
                         │ * Synthesizes SSML narrative  │
                         └───────────────────────────────┘
```

#### 2.1 Model Registry Specifications

| Component | Model Architecture | Parameters | Quantization | Runtime EP | Memory Footprint | Latency Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 | CPU (XNNPACK) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Tier 1 Dual-Head Vectorizer** | Dual-Head Conv1D/GRU | 420 K | INT8 QAT | LiteRT NPU (QNN HTP) | ~418 KB | < 1.8 ms / 500ms chunk |
| **Tier 2 Semantic Encoder** | Streaming Whisper Tiny | 38 M | INT8 | LiteRT GPU (OpenCL) | ~38 MB | < 18 ms / chunk |
| **Post-Session SLM Narrator** | Pruned `Qwen3-Omni-3B` | 1.45 B | INT4 AWQ | LiteRT / QNN NPU | ~725 MB | < 25 ms / token |

#### 2.2 Post-Session Expressive Narrative Debrief Prompt Contract
System prompt contract for `Qwen3-Omni-3B` during debrief generation:
```
System Prompt: "You are S.H.R.U.T.I., an expressive post-session narrator. Analyze the provided multi-speaker vector trajectory and narrate a concise, engaging spoken debrief of the conversation. Attribute statements accurately to Speaker 1 and Speaker 2. Use SSML prosody tags to convey urgency or assurance. Keep the debrief under 30 seconds."
```

---

### 3. Telephony Architecture & Regulatory Compliance Engines

#### 3.1 Passive In-Call Voice Vectorizer State Machine (`ShrutiInCallActivity.kt`)
When `InCallService` handles telephony call transitions:
```kotlin
enum class CallSessionState {
    IDLE,
    RINGING,                          // Displays standard Default Dialer Accept/Decline UI
    ACTIVE_CALL_PASSIVE_VECTORIZE,    // User converses normally; audio pushed to C++ DSP ring buffer
    CALL_DISCONNECTED_FINALIZE_STREAM // Flushes FlatBuffers .vecstream & generates debrief notification
}
```

#### 3.2 Emergency Call Pass-Through (`ROLE_DIALER`)
```kotlin
fun evaluateCallInterception(phoneNumber: String): CallAction {
    if (PhoneNumberUtils.isEmergencyNumber(phoneNumber) || phoneNumber in listOf("112", "911", "100", "101", "102")) {
        return CallAction.EMERGENCY_PASS_THROUGH // Zero AI routing, direct PSTN route
    }
    return TraiPrefixMatcher.evaluate(phoneNumber)
}
```
