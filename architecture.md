# System Architecture Specification

---

### 1. Document Control & Architecture Overview

* **Project Codename:** S.H.R.U.T.I. (Speech-native Hardware Runtime for Ubiquitous Telephony Intelligence)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU-equipped SoCs (Snapdragon 8 Gen 2/3/4, Google Tensor G3/G4, MediaTek Dimensity 9200/9300/9400)
* **Document Version:** 3.0.0 (Passive Vector Ingress & Tri-Tier LiteRT Architecture)
* **Status:** Approved System Architecture

#### 1.1 Architectural Invariant (Passive Listener Only)
S.H.R.U.T.I. operates strictly as an on-device, passive conversational listener and expressive storyteller. The AI **never answers, speaks to, or intercepts callers live during active telephone calls**. Its operational loop is strictly divided into:
1. **Passive Real-Time Ingress:** Capturing in-call or ambient speech without disk waveform writes.
2. **NPU Burst Vectorization:** Converting 1.0s audio tiles into composite math vectors via LiteRT.
3. **Continuous Diarization:** Online cosine clustering tracking up to 8 speaker centroids in $O(1)$ memory.
4. **Post-Session Expressive Narration:** Decoding `.vecstream` into an emotionally nuanced, SSML-orchestrated narrative debrief via on-device SLM and system TTS.

---

### 2. High-Level Hardware & Processor Allocation

```
SYSTEM HARDWARE BUS
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│    ┌─────────────────────────┐               ┌──────────────────────────────┐    │
│    │    CPU Efficiency Core  │               │        Hardware NPU          │    │
│    │      (LITTLE Cluster)   │               │   (Qualcomm QNN / Tensor)    │    │
│    ├─────────────────────────┤               ├──────────────────────────────┤    │
│    │ • Sensor Interrupts     │               │ • Silero VAD (INT8)          │    │
│    │ • Audio Ring Buffering  │               │ • Dual-Head Vectorizer (INT8)│    │
│    │ • Zero-Crossing Filter  │               │ • Conformer ASR Encoder      │    │
│    │ • FlatBuffer Serializer │               │ • Gemma-2 2B Narrator (INT4) │    │
│    └────────────┬────────────┘               └──────────────▲───────────────┘    │
│                 │                                           │                    │
│                 │  Zero-Copy Shared Memory (DMA-BUF / RAM)  │                    │
│                 └───────────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

### 3. End-to-End Ingress & Vectorization Pipeline

```
[ Audio Source (VoIP / InCall / Mic) ]
                  │
                  ▼
       [ AudioIngressService ] ──► [ CPU Energy Filter ]
                  │
        ┌─────────┴─────────┐ (Speech Detected)
        ▼
[ DirectByteBuffer (1.0s / 16kHz) ]
        │
        ▼ (Race-to-Sleep Burst via LiteRT NPU Delegate)
┌───────────────────────────────────────┐
│        Hardware Acceleration          │
│ • VAD Filtering                       │
│ • 192-d Speaker Embedding (e_speaker) │
│ • 64-d Prosody/Emotion Vector (z_pro) │
│ • ASR Semantic Token IDs (w_semantic) │
└───────────────────┬───────────────────┘
                    │
                    ▼
[ Composite Vector Tuple v_t ] ──► [ Google FlatBuffers ] ──► [ Encrypted .vecstream ]
                    │
        ┌───────────┴───────────┐ (Session Concluded)
        ▼
[ Two-Tier Identity Resolver ] (ContactsContract + Post-Call SLM Pass)
        │
        ▼
[ On-Device SLM (Gemma 2 2B / Qwen3 INT4) ] ──► Generates Expressive SSML Script
        │
        ▼
[ Android Native TextToSpeech Engine ] (Offline High-Quality Voice / Kokoro Fallback)
```

---

### 4. Audio Ingress & Platform Abstraction

To navigate Android 14+ platform limitations (where cellular downlink audio is often isolated by OEM basebands), the ingress layer isolates telephony mechanisms from the vectorizer pipeline.

```kotlin
interface AudioIngressEngine {
    fun startCapture(onChunkReady: (ByteBuffer) -> Unit)
    fun stopCapture()
    fun getStreamType(): IngressType
}

enum class IngressType {
    VOIP_INTERNAL,           // Built-in WebRTC Engine (Full 2-way digital capture)
    TELECOM_DOWNLINK_FALLBACK // Native Telecom (MediaRecorder.AudioSource.VOICE_COMMUNICATION)
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

    override fun getStreamType(): IngressType = IngressType.TELECOM_DOWNLINK_FALLBACK
}
```

---

### 5. Google FlatBuffers Binary Vector Schema (`vector_frame.fbs`)

Conversations are serialized into an append-only binary stream using Google FlatBuffers, eliminating JVM heap allocation and garbage collection overhead.

```protobuf
namespace AudioIntelligence;

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
    speaker_cluster_id: uint8;
    pause_delta_ms: uint16;
    speaker_embedding: [float32]; // 192-d ECAPA-TDNN vector
    prosody: ProsodyMetrics;
    semantic_tokens: [uint16];    // ASR Vocabulary token IDs
}

table CallSessionRecord {
    session_id: string;
    start_timestamp: uint64;
    primary_contact_name: string;
    resolved_participants: [string];
    frames: [VectorFrame];
}

root_type CallSessionRecord;
```

---

### 6. Tri-Tier Hardware Dispatcher & OpenCL GPU Fallback

```
[ 1.0s Audio Chunk (DirectBuffer) ]
                 │
                 ▼
[ Tri-Tier Hardware Dispatcher ]
                 │
 ┌───────────────┼───────────────┐
 ▼               ▼               ▼
[ Tier 1: NPU ] [ Tier 2: OpenCL GPU ] [ Tier 3: CPU LITTLE ]
(QNN / Tensor)  (Adreno / Mali)        (XNNPACK 2-Threads)
• 12ms Burst    • 28ms Burst           • 85ms Window
• Zero DRAM Bus • Persistent Kernel    • Core Affinity
└───────────────┼───────────────┘
                 ▼
[ Multi-Vector Output Frame ]
```

#### 6.1 Native C++ OpenCL Delegate Configuration (NDK Layer)
```cpp
#include <tensorflow/lite/c/c_api.h>
#include <tensorflow/lite/delegates/gpu/delegate.h>
#include <android/log.h>

TfLiteDelegate* CreateOpenCLGpuDelegate(const char* cache_dir, const char* model_token) {
    TfLiteGpuDelegateOptionsV2 options = TfLiteGpuDelegateOptionsV2Default();
    options.inference_preference = TFLITE_GPU_INFERENCE_PREFERENCE_SUSTAINED_SPEED;
    options.inference_priority1 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_LATENCY;
    options.inference_priority2 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_MEMORY_USAGE;
    options.inference_priority3 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
    
    // Enable INT8 quantization passthrough on GPU
    options.experimental_flags |= TFLITE_GPU_EXPERIMENTAL_FLAGS_ENABLE_QUANT;
    
    // Set persistent OpenCL binary cache directory to eliminate runtime recompilation stalls
    options.serialization_dir = cache_dir;
    options.model_token = model_token;
    
    TfLiteDelegate* gpu_delegate = TfLiteGpuDelegateV2Create(&options);
    return gpu_delegate;
}
```

#### 6.2 Kotlin Hardware Execution Manager
```kotlin
class HardwareExecutionManager(
    private val context: Context,
    private val modelBuffer: ByteBuffer,
    private val modelIdentifier: String
) {
    private var interpreter: Interpreter? = null
    private var activeBackend: ExecutionBackend = ExecutionBackend.UNKNOWN

    enum class ExecutionBackend { NPU, GPU_OPENCL, CPU_LITTLE, UNKNOWN }

    fun initializePipeline(): ExecutionBackend {
        // TIER 1: Hardware NPU
        try {
            val npuOptions = Interpreter.Options().apply {
                addDelegate(NpuDelegate())
                setNumThreads(1)
            }
            interpreter = Interpreter(modelBuffer, npuOptions)
            activeBackend = ExecutionBackend.NPU
            return activeBackend
        } catch (e: Exception) {
            // NPU delegate unavailable or unsupported ops
        }

        // TIER 2: GPU via OpenCL
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val gpuOptions = GpuDelegate.Options().apply {
                    setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                    setQuantizedModelsAllowed(true)
                    val cacheDir = File(context.codeCacheDir, "litert_opencl").apply { mkdirs() }
                    setSerializationDir(cacheDir.absolutePath)
                    setModelToken(modelIdentifier)
                }
                val gpuDelegate = GpuDelegate(gpuOptions)
                val interpreterOptions = Interpreter.Options().apply {
                    addDelegate(gpuDelegate)
                }
                interpreter = Interpreter(modelBuffer, interpreterOptions)
                activeBackend = ExecutionBackend.GPU_OPENCL
                return activeBackend
            }
        } catch (e: Exception) {
            // OpenCL driver context creation failed
        }

        // TIER 3: CPU efficiency cores via XNNPACK
        val cpuOptions = Interpreter.Options().apply {
            setUseXNNPACK(true)
            setNumThreads(2)
        }
        interpreter = Interpreter(modelBuffer, cpuOptions)
        activeBackend = ExecutionBackend.CPU_LITTLE
        return activeBackend
    }
}
```

---

### 7. Race-to-Sleep Burst Scheduling & Thermal Management

```
Time (ms)  0               980        1000                    1012            2000
CPU (LITTLE) [---- Buffering Audio ----] [ Dispatch Buffer ]   [ Buffering... ]
NPU Rail     [        SLEEP            ] [ Power ON ] [ Inference ] [ SLEEP   ]
                                         (12ms burst)
```

#### Dynamic Thermal Throttling
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
An on-device Small Language Model (Gemma 2 2B / Qwen3 INT4) evaluates dialogue tokens alongside cluster indices to resolve nameless clusters:
```json
{
  "dialogue_events": [
    {"speaker": "cluster_0", "text": "Good morning, thanks for joining the architecture sync."},
    {"speaker": "cluster_1", "text": "Hey Gagan, Sarah here. Can you hear me clearly?"},
    {"speaker": "cluster_0", "text": "Loud and clear, Sarah. Is David coming?"},
    {"speaker": "cluster_2", "text": "Yes, I'm here too. Sorry for the delay."}
  ]
}
```
**Resolution Output:**
```json
{
  "cluster_0": "Gagan (User)",
  "cluster_1": "Sarah",
  "cluster_2": "David"
}
```

---

### 9. Expressive Narrative Debriefing Engine

Once names and dialogues are assembled, the on-device SLM formats the debrief into rich Speech Synthesis Markup Language (SSML):

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
    "We verified the NPU delegates this morning—zero operator fallbacks across the entire model graph."
  </prosody>
  <break time="600ms"/>
  <prosody rate="88%" pitch="-3st">
    David paused for nearly three seconds before responding, his tone dropping with visible hesitation:
  </prosody>
  <break time="400ms"/>
  <prosody pitch="-2st">
    "That's great for the Pixel devices... but we're still seeing driver timeouts on legacy chipsets."
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
[ Primed Keyword Spotting Window: 15 Sec ] (LiteRT INT8 KWS listening for "Hey Shruti")
         │                           │
         ├─ (Hotword Matched)        └─ (Timeout / No Match)
         ▼                                      ▼
[ Transition to Active Vectorization ]    [ Return to Deep Sleep ]
• Start Foreground Service
• Run NPU Burst Vector Pipeline
```

---

### 11. Cryptographic Security & Memory Sanitization

1. **Ephemeral RAM Scrubbing:** Every 1.0-second PCM raw audio buffer resides in a native C++ allocation outside the garbage-collected JVM heap. Immediately following LiteRT inference completion, the buffer is explicitly scrubbed using `memset_s(buffer, 0, size)` before the next chunk is read.
2. **At-Rest Encryption:** Vector files (`.vecstream`) are stored in the application's private sandbox (`/data/user/0/<package>/files/vectors/`) and encrypted using the Android Keystore System with an **AES-256-GCM** hardware-backed Master Key.
3. **Air-Gapped Invariant:** The application manifest strictly excludes `android.permission.INTERNET` from the core ML vectorization and SLM modules, cryptographically assuring that biometric voice vectors and transcripts never leave the device.
