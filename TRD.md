# Technical Requirements Document (TRD)

---

### 1. System Engineering Stack & Target Environment

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35) (Compatible down to Android 10 / API 29 via Vulkan 1.1)
* **NDK Level:** NDK r26c+ (C++20 standard)
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) + CMake 3.22.1 (`-DGGML_VULKAN=ON`)
* **ML Inference Framework:** **`llama.cpp` + GGML (C++20)** with native Vulkan compute backend (`ggml-vulkan.cpp`) linking Android system `libvulkan.so`
* **Model Packaging:** Unified **GGUF format** (quantized via `llama-quantize` to `Q4_K_M` and `Q8_0`)
* **SLM / Debrief Engine:** Pruned `Qwen3-Omni-3B` (~1.45B params, quantized to `Q4_K_M` GGUF) / Local Kokoro / Android TTS
* **Hardware Acceleration Backends:**
  * **GPU (Primary):** Universal Khronos Vulkan 1.1+ Compute Shaders (Qualcomm Adreno 6xx/7xx/8xx, ARM Mali-Gxx / Immortalis, Imagination PowerVR, Samsung Xclipse / AMD RDNA2)
  * **CPU (Fallback):** Multithreaded ARM NEON SIMD with GGML CPU execution (`ggml-cpu`) + Silero VAD v5 + Lockless SPSC C++ Ring Buffer
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
            ▼ (Log-Mel: 1, 50, 80)                ▼ (Acoustic Waveform)
┌────────────────────────────────────────┐ ┌────────────────────────────────────────┐
│ Tier 1: Vulkan Dual-Head Vectorizer    │ │ Tier 1: Vulkan Whisper Audio Encoder   │
│ * 192-d Speaker d-vector (e_spk)       │ │ * 128-d Semantic Intent (w_sem)        │
│ * 64-d Prosody/Energy Dynamics (z_pros)│ │ * 1-d Pause Delta (Δ_pause)            │
│ Q8_0 GGUF (~420 KB, < 1.6 ms)          │ │ Q8_0 GGUF (~38 MB, < 14 ms)            │
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
                         │ llama.cpp Vulkan SLM Narrator │
                         │ * Pruned Qwen3-Omni (Q4_K_M)  │
                         │ * Offloaded to Vulkan (-ngl 99│
                         │ * Synthesizes SSML narrative  │
                         └───────────────────────────────┘
```

#### 2.1 Model Registry Specifications

| Component | Model Architecture | Parameters | Quantization | Runtime EP | Memory Footprint | Latency Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 | CPU (ARM NEON) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Dual-Head Vectorizer** | Dual-Head Conv1D/GRU | 420 K | Q8_0 GGUF | Vulkan GPU (`ggml-vulkan`) | ~420 KB | < 1.6 ms / 500ms chunk |
| **Semantic Audio Encoder** | Streaming Whisper Tiny | 38 M | Q8_0 GGUF | Vulkan GPU (`ggml-vulkan`) | ~38 MB | < 14 ms / chunk |
| **Post-Session SLM Narrator** | Pruned `Qwen3-Omni-3B` | 1.45 B | Q4_K_M GGUF | `llama.cpp` Vulkan GPU | ~725 MB | < 22 ms / token |

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
