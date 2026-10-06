# System Architecture Specification

---

### 1. Document Control & Architecture Overview

* **Project Codename:** S.H.R.U.T.I. (Speech-native Hardware Runtime for Ubiquitous Telephony Intelligence)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35) (Compatible down to Android 10 / API 29 via Vulkan 1.1)
* **Hardware Tier:** **Universal Android Smartphone Coverage via Vulkan 1.1+** (Qualcomm Adreno 6xx/7xx/8xx, ARM Mali-Gxx / Immortalis, Imagination PowerVR, Samsung Xclipse / AMD RDNA2) with multithreaded ARM NEON CPU fallback.
* **Inference Engine:** **`llama.cpp` + GGML with Vulkan Compute Backend (`ggml-vulkan`)** using unified GGUF binary format.
* **Document Version:** 4.0.0 (Universal Vulkan & `llama.cpp` Architecture)
* **Status:** Approved System Architecture

#### 1.1 Why Vulkan + `llama.cpp` Replaces Proprietary NPUs
While dedicated NPUs (Qualcomm Hexagon/HTP, MediaTek APU, Google Tensor TPU) theoretically offer high efficiency, they introduce severe production bottlenecks on Android:
1. **Extreme Market Fragmentation:** NPUs are restricted to premium flagships; over 80% of active smartphones (especially in the targeted Indian telecom demographic) lack dedicated NPUs or lack vendor-exposed NPU drivers.
2. **Proprietary Vendor Lock-in & Closed Firmware:** Qualcomm QNN, MediaTek NeuroPilot, and Samsung ENN require incompatible proprietary binaries, closed toolchains, and distinct compilation targets.
3. **Android Platform Instability:** Google deprecated Android NNAPI in Android 15, leaving no universal NPU HAL.
4. **The Universal Vulkan Solution:** **Khronos Vulkan 1.1+ is universally mandatory on all Android 10+ devices**. Standard `libvulkan.so` is available on every device with a modern GPU. Paired with `llama.cpp` (`ggml-vulkan.cpp`), S.H.R.U.T.I. achieves high-performance GPU tensor acceleration with zero proprietary drivers, uniform FP16/INT8/INT4 math, and seamless ARM NEON CPU fallback.

#### 1.2 Architectural Invariant (Passive Listener Only)
S.H.R.U.T.I. operates strictly as an on-device, passive conversational listener and expressive storyteller. The AI **never answers, speaks to, or intercepts callers live during active telephone calls**. Its operational loop is strictly divided into:
1. **Passive Real-Time Ingress:** Capturing in-call or ambient speech via lock-free C++ DSP ring buffer without disk writes.
2. **Vulkan GPU Burst Vectorization:** Converting 500ms audio chunks into composite math vectors $v_t \in \mathbb{R}^{384}$ via GGML / Vulkan compute shaders.
3. **Continuous Diarization:** Online cosine clustering tracking up to 8 speaker centroids in $O(1)$ memory.
4. **Post-Session Expressive Narration:** Decoding `.vecstream` into an emotionally nuanced, SSML-orchestrated narrative debrief via `llama.cpp` Vulkan SLM and Android system TTS / local Kokoro.

---

### 2. High-Level Hardware & Processor Allocation

```
SYSTEM HARDWARE BUS
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│    ┌─────────────────────────┐               ┌──────────────────────────────┐    │
│    │    CPU Efficiency Core  │               │        Mobile GPU Silicon    │    │
│    │      (LITTLE Cluster)   │               │   (Adreno / Mali / Xclipse)  │    │
│    ├─────────────────────────┤               ├──────────────────────────────┤    │
│    │ • Sensor Interrupts     │               │ • Vulkan 1.1+ Compute Shaders│    │
│    │ • Audio Ring Buffering  │               │ • GGML Vulkan Pipeline       │    │
│    │ • Silero VAD (ARM NEON) │               │ • Dual-Head Vectorizer (Q8_0)│    │
│    │ • FlatBuffer Serializer │               │ • Streaming Whisper (Q8_0)   │    │
│    │ • AES-256 Memory Scrub  │               │ • Qwen3-Omni-3B (Q4_K_M)     │    │
│    └────────────┬────────────┘               └──────────────▲───────────────┘    │
│                 │                                           │                    │
│                 │      Zero-Copy Vulkan Shared Memory       │                    │
│                 │   (VK_EXT_external_memory_dma_buf / AHardwareBuffer)           │
│                 └───────────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

### 3. End-to-End Ingress & Vectorization Pipeline

```
[ Inbound PSTN / Cellular Call ]
              │
              ▼ (User Answers Normally - Default Dialer)
[ AudioIngressEngine (VOICE_COMMUNICATION) ]
              │
              ▼ (Raw 16kHz PCM Frames)
[ Lockless C++20 Ring Buffer (AudioRingBuffer.cpp) ]
              │
              ▼ (50-Frame / 500ms Log-Mel Spectrogram)
┌────────────────────────────────────────────────────────┐
│         llama.cpp / GGML Vulkan Compute Engine         │
│  (ggml-vulkan: SPIR-V Compute Shaders over libvulkan)  │
│                                                        │
│  • Head A: 192-d Speaker d-vector (e_speaker)          │
│  • Head B: 64-d Prosody/Energy Vector (z_prosody)      │
│  • Head C: 128-d Semantic Embedding (w_semantic)       │
│  • Pause:  1-d Inter-Speech Delta (Δ_pause)            │
└───────────────────────────┬────────────────────────────┘
                            │ Composite Vector vt ∈ R^384
                            ▼
┌────────────────────────────────────────────────────────┐
│           Zero-Copy FlatBuffers Serialization          │
│        (Memory-Mapped .vecstream / AES-256-GCM)        │
└───────────────────────────┬────────────────────────────┘
                            │
               (Call Disconnects / User Query)
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│      llama.cpp Vulkan SLM Narrator (Q4_K_M GGUF)       │
│   • Ingests Decrypted Trajectory Matrix M_session      │
│   • Generates Expressive Debrief Script with SSML tags │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
[ Android TTS / Kokoro Audio Playback (Speaker Attribution & Emotion) ]
```

---

### 4. Audio Ingress & Platform Abstraction

To navigate Android platform requirements, the ingress layer isolates telephony mechanisms from the vectorizer pipeline.

```kotlin
interface AudioIngressEngine {
    fun startCapture(onChunkReady: (ByteBuffer) -> Unit)
    fun stopCapture()
    fun getStreamType(): IngressType
}

enum class IngressType {
    VOIP_INTERNAL,           // Built-in WebRTC Engine (Full 2-way digital capture)
    TELECOM_DOWNLINK_PASSIVE // Native Telecom (MediaRecorder.AudioSource.VOICE_COMMUNICATION under ROLE_DIALER)
}
```

```kotlin
class TelecomAudioIngress(private val context: Context) : AudioIngressEngine {
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    override fun startCapture(onChunkReady: (ByteBuffer) -> Unit) {
        val directBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()

        // Pinned strictly to background efficiency core (LITTLE cluster)
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readBytes = audioRecord?.read(directBuffer, bufferSize) ?: 0
                if (readBytes > 0) {
                    onChunkReady(directBuffer)
                    directBuffer.clear()
                }
            }
        }.start()
    }

    override fun stopCapture() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun getStreamType(): IngressType = IngressType.TELECOM_DOWNLINK_PASSIVE
}
```

---

### 5. Google FlatBuffers Binary Vector Schema (`shruti_vector_stream.fbs`)

Conversations are serialized into an append-only binary stream using Google FlatBuffers, eliminating JVM heap allocation and garbage collection overhead.

```protobuf
namespace org.seven_cgpalabs.shruti.serialization;

struct ProsodyMetrics {
    pitch_hz: float32;       // Fundamental frequency F0
    energy_rms: float32;     // Root Mean Square amplitude
    speech_rate_wpm: uint16; // Words per minute estimate
    valence: int8;           // Emotional Valence (-128 to 127)
    arousal: int8;           // Emotional Arousal (-128 to 127)
}

table VectorFrame {
    timestamp_ms: uint64;
    duration_ms: uint16;
    speaker_id: uint8;
    pause_delta_ms: uint16;
    speaker_embedding: [float32]; // 192-d d-vector
    prosody_features: [float32];  // 64-d prosody dynamics
    semantic_embedding: [float32];// 128-d semantic intent
}

table ShrutiVectorStream {
    version: uint32;
    session_uuid: string;
    start_epoch_ms: uint64;
    total_frames: uint32;
    frames: [VectorFrame];
}

root_type ShrutiVectorStream;
```

---

### 6. Universal Vulkan Execution Engine (`llama.cpp` + GGML)

#### 6.1 Native C++ Vulkan Backend Initialization (NDK Layer)

`llama.cpp` initializes the Vulkan backend via `ggml-vulkan.cpp`, using Android's system `libvulkan.so`.

```cpp
#include "llama.h"
#include "ggml-vulkan.h"
#include <android/log.h>

#define LOG_TAG "ShrutiVulkanEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class ShrutiVulkanRuntime {
public:
    bool initialize(const char* gguf_model_path) {
        LOGI("Initializing llama.cpp with Vulkan compute backend...");

        // Step 1: Initialize GGML Vulkan backend device
        ggml_backend_t vk_backend = ggml_backend_vk_init(0); // device index 0
        if (!vk_backend) {
            LOGE("Vulkan GPU backend unavailable; falling back to multithreaded CPU NEON");
            return false;
        }

        // Step 2: Configure llama model parameters with full GPU offloading
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 99; // Offload all 22 pruned transformer layers to Vulkan GPU
        model_params.use_mmap = true;   // Zero-copy direct memory mapping from APK/assets

        model_ = llama_load_model_from_file(gguf_model_path, model_params);
        if (!model_) {
            LOGE("Failed to load GGUF model from %s", gguf_model_path);
            return false;
        }

        // Step 3: Configure llama context parameters
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048;        // Capped context for fast mobile inference
        ctx_params.n_batch = 512;      // Batch prompt evaluation
        ctx_params.n_threads = 4;      // CPU worker threads if hybrid execution needed

        ctx_ = llama_new_context_with_model(model_, ctx_params);
        if (!ctx_) {
            LOGE("Failed to allocate llama context");
            return false;
        }

        LOGI("llama.cpp Vulkan runtime initialized successfully (Model offloaded to GPU)");
        return true;
    }

    ~ShrutiVulkanRuntime() {
        if (ctx_) llama_free(ctx_);
        if (model_) llama_free_model(model_);
    }

private:
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
};
```

#### 6.2 Kotlin Hardware Execution Manager (Vulkan $\to$ CPU Fallback)

```kotlin
class HardwareExecutionManager(
    private val context: Context,
    private val modelPath: String
) {
    private var nativeEngineHandle: Long = 0L
    private var activeBackend: ExecutionBackend = ExecutionBackend.UNKNOWN

    enum class ExecutionBackend { GPU_VULKAN, CPU_NEON, UNKNOWN }

    fun initializePipeline(): ExecutionBackend {
        // TIER 1: Universal Mobile GPU via llama.cpp + Vulkan
        try {
            if (isVulkanSupported()) {
                nativeEngineHandle = nativeInitVulkanEngine(modelPath, useGpu = true)
                if (nativeEngineHandle != 0L) {
                    activeBackend = ExecutionBackend.GPU_VULKAN
                    return activeBackend
                }
            }
        } catch (e: Exception) {
            // Vulkan initialization failed or unsupported driver
        }

        // TIER 2: Multithreaded CPU Fallback via GGML ARM NEON
        nativeEngineHandle = nativeInitVulkanEngine(modelPath, useGpu = false)
        activeBackend = ExecutionBackend.CPU_NEON
        return activeBackend
    }

    private fun isVulkanSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1)
    }

    private external fun nativeInitVulkanEngine(path: String, useGpu: Boolean): Long
}
```

---

### 7. Race-to-Sleep GPU Burst Scheduling & Thermal Management

```
Time (ms)  0               980        1000                    1016            2000
CPU (LITTLE) [---- Buffering Audio ----] [ Dispatch Buffer ]   [ Buffering... ]
Vulkan GPU   [        SUSPENDED        ] [ Power ON ] [ Inference ] [ SUSPENDED ]
                                         (16ms burst)
```

1. **Compute Burst:** During passive in-call listening, the 500ms audio chunk is dispatched to the Vulkan GPU in a brief $\le 16\text{ ms}$ burst, leaving the GPU idle for $>95\%$ of the interval.
2. **Thermal Throttling Manager:** Dynamically scales audio chunk batching to maintain device thermals under heavy calling sessions.

```kotlin
class ThermalThrottlingManager(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun registerThermalListener(onThrottleAdjust: (Long) -> Unit) {
        powerManager.addThermalStatusListener { status ->
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> onThrottleAdjust(1000L) // 1.0s normal batch
                PowerManager.THERMAL_STATUS_MODERATE -> onThrottleAdjust(2500L) // 2.5s batching (cooler)
                PowerManager.THERMAL_STATUS_SEVERE -> onThrottleAdjust(5000L) // 5.0s batching (emergency)
            }
        }
    }
}
```

---

### 8. Streaming Diarization & Identity Resolution

#### 8.1 Online Cosine Centroid Tracking ($O(1)$ Memory)
For an input speaker embedding $e_t$ at second $t$:
1. Compute Cosine Similarity against all active centroids $c_k$:
   $$S_k = \frac{e_t \cdot c_k}{\|e_t\|_2 \|c_k\|_2} \quad \forall k \in \{1, \dots, K\}$$
2. Let $k^* = \arg\max_k S_k$.
3. If $S_{k^*} \ge \tau$ ($\tau = 0.72$):
   - Assign frame $t$ to Cluster $k^*$.
   - Update centroid: $c_{k^*} \leftarrow \alpha c_{k^*} + (1 - \alpha) e_t$ ($\alpha = 0.85$).
   - Re-normalize: $c_{k^*} \leftarrow \frac{c_{k^*}}{\|c_{k^*}\|_2}$.
4. Else if $K < K_{\max}$ ($K_{\max} = 8$):
   - Initialize new centroid: $c_{K+1} = e_t$.
5. Else:
   - Merge the two closest existing clusters, then assign $e_t$.

#### 8.2 Post-Call SLM Name Resolution
An on-device Small Language Model (pruned `Qwen3-Omni-3B` in Q4_K_M GGUF format) evaluates dialogue tokens alongside cluster indices to resolve nameless clusters into human identities.

---

### 9. Expressive Narrative Debriefing Engine

Once names and dialogues are assembled, the on-device `llama.cpp` Vulkan SLM formats the debrief into rich Speech Synthesis Markup Language (SSML):

```xml
<speak>
  <prosody rate="92%" pitch="-1st">
    The conversation opened with a relaxed atmosphere.
  </prosody>
  <break time="350ms"/>
  <prosody pitch="+1st">
    Sarah jumped straight into the sprint deliverables, her cadence fast and energetic:
  </prosody>
  <break time="200ms"/>
  <prosody rate="105%" pitch="+2st" volume="loud">
    "We verified the Vulkan compute pipeline this morning—zero driver crashes across all test chipsets."
  </prosody>
  <break time="600ms"/>
  <prosody rate="88%" pitch="-3st">
    David paused for nearly three seconds before responding, his tone dropping with visible hesitation:
  </prosody>
  <break time="400ms"/>
  <prosody pitch="-2st">
    "That is a massive improvement over the NPU driver fragmentation we saw earlier."
  </prosody>
</speak>
```

---

### 10. Deliberate Ambient Triggering & KWS Pipeline

```
[ Deep Sleep State ]
         │
         ▼
[ Accelerometer Interrupt: Double-Tap ] (Variance > 14 m/s², Δt: 200–600 ms)
         │
         ▼
[ Haptic Engine: Pulse Ack ] (VibrationEffect.EFFECT_CLICK)
         │
         ▼
[ Primed Keyword Spotting Window: 15 Sec ] (GGML ARM NEON KWS listening for "Hey Shruti")
         │                           │
         ├─ (Hotword Matched)        └─ (Timeout / No Match)
         ▼                                      ▼
[ Transition to Active Vectorization ]    [ Return to Deep Sleep ]
• Start Foreground Service
• Run Vulkan Burst Vector Pipeline
```

---

### 11. Cryptographic Security & Memory Sanitization

1. **Ephemeral RAM Scrubbing:** Every 1.0-second PCM raw audio buffer resides in a native C++ allocation outside the garbage-collected JVM heap. Immediately following GGML / Vulkan inference completion, the buffer is explicitly scrubbed using `memset_s(buffer, 0, size)` before the next chunk is read.
2. **At-Rest Encryption:** Vector files (`.vecstream`) are stored in the application's private sandbox (`/data/user/0/<package>/files/vectors/`) and encrypted using the Android Keystore System with an **AES-256-GCM** hardware-backed Master Key.
3. **Air-Gapped Invariant:** The application manifest strictly excludes `android.permission.INTERNET` from the core ML vectorization and SLM modules, cryptographically assuring that biometric voice vectors and transcripts never leave the device.
