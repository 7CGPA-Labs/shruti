# S.H.R.U.T.I. (श्रुति)
### **S**peech-native **H**ardware **R**untime for **U**biquitous **T**elephony **I**ntelligence

[![Android API](https://img.shields.io/badge/Android-14%20%7C%2015%20(API%2034--35)-3DDC84?logo=android&logoColor=white)](#prerequisites--system-requirements)
[![NDK](https://img.shields.io/badge/NDK-r26c%2B%20(C%2B%2B20)-00599C?logo=c%2B%2B&logoColor=white)](#native-dsp--c-engine)
[![Runtime](https://img.shields.io/badge/ONNX%20Runtime-Mobile%20v1.19%2B-005CED?logo=onnx&logoColor=white)](#heterogeneous-ml-pipeline)
[![NPU Backend](https://img.shields.io/badge/Hardware%20NPU-Qualcomm%20QNN%20%7C%20NNAPI-FF3E00)](#heterogeneous-ml-pipeline)
[![Privacy](https://img.shields.io/badge/Privacy-Zero--Disk%20Audio%20%2F%20Zero--Text-brightgreen)](#zero-persistence-privacy-invariant)
[![TRAI Compliance](https://img.shields.io/badge/TRAI-140%20%7C%20160%20%7C%201909%20Compliant-orange)](#trai-regulatory-engine--anti-spam)

---

> **Etymology & Philosophy**  
> In classical Sanskrit, ***Śruti*** (श्रुति) signifies *"that which is heard"*—knowledge transmitted directly through acoustic perception without written mediation.  
> **S.H.R.U.T.I.** mirrors this philosophy as an on-device, zero-text, audio-native telephony assistant. It eliminates cascading Speech-to-Text (STT) $\rightarrow$ LLM $\rightarrow$ Text-to-Speech (TTS) pipelines in favor of direct Speech-to-Speech (S2S) discrete token inference executed entirely on local mobile NPU silicon.

---

## Key Highlights

- **Pure Speech-to-Speech Architecture ($\le 380\text{ ms}$ Turnaround):** Ingests raw 16 kHz PCM directly into neural acoustic codecs (Mimi / WavTokenizer), streams discrete tokens into a 1B audio-native SLM, and reconstructs synthesized speech without generating intermediate text tokens.
- **Zero-Persistence Privacy Invariant:** No raw audio (`.wav`, `.pcm`, `.mp3`) and no textual transcripts are ever written to disk. Volatile RAM buffers are aggressively scrubbed using `memset_s`.
- **Non-Invertible Latent Storage:** Historical conversation context is stored exclusively as 512-dimensional continuous unit vectors ($\mathbf{v} \in \mathbb{S}^{511}$) in an encrypted SQLCipher database, rendering audio waveform reconstruction mathematically impossible.
- **Heterogeneous Silicon Allocation:** 
  - **CPU (ARM NEON):** Silero VAD v5 (32 ms window) operates at $< 2\text{ ms}$ latency without waking the NPU or draining standby power.
  - **NPU (Qualcomm QNN HTP / Android NNAPI):** Accelerates neural codec encoders/decoders and the 1B INT4 S2S model.
- **TRAI Regulatory Shield & Anti-Spam Engine:**
  - $\mathcal{O}(1)$ prefix matching drops `140xxxxxxx` promotional telemarketers with zero NPU wake-ups.
  - Identifies `160xxxxxxx` / `1601xxxxxxx` series for high-priority transactional processing (couriers, banking, OTPs).
  - Automatically compiles standardized 1909 UCC complaints for unregistered commercial callers.
- **Power-Aware Ambient Listening:** Uses Android `SoundTrigger` low-power hardware DSP hooks and peripheral gating (car Bluetooth, docking) to avoid persistent battery drain and Android's OS microphone indicator.

---

## High-Level Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │          Inbound Cellular / SIP Call         │
                  └──────────────────────┬───────────────────────┘
                                         │
                         ┌───────────────┴───────────────┐
                         │   TRAI O(1) Prefix Evaluator  │
                         └───────┬───────────────┬───────┘
                     Matches 140 │               │ Pass / Unknown / 160
                                 ▼               ▼
                       [ Instant Drop ]    [ LiveKit SIP Bridge ]
                                                 │ 16kHz PCM (20ms)
                                                 ▼
                                   ┌───────────────────────────┐
                                   │ Lockless SPSC Ring Buffer │
                                   │ (AudioRingBuffer.cpp)     │
                                   └─────────────┬─────────────┘
                                                 │
                        ┌────────────────────────┴────────────────────────┐
                        ▼                                                 ▼
        ┌───────────────────────────────┐                 ┌───────────────────────────────┐
        │  Silero VAD (CPU / ARM NEON)  │                 │ Mimi Codec Encoder (HTP NPU)  │
        │  * Barge-in detection (<40ms) │                 │ * 50Hz Discrete Tokens        │
        └───────────────────────────────┘                 └───────────────┬───────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ Audio-Native 1B SLM (HTP NPU) │
                                                          │ * INT4 AWQ Autoregressive     │
                                                          └───────────────┬───────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ Mimi Codec Decoder (HTP NPU)  │
                                                          │ * Synthesizes 16kHz PCM       │
                                                          └───────────────┬───────────────┘
                                                                          │
                                    ┌─────────────────────────────────────┴─┐
                                    ▼                                       ▼
                     [ Outbound LiveKit Audio Sink ]         [ Non-Invertible Projection ]
                                                                            │ 512-d Latent Vector
                                                                            ▼
                                                             [ SQLCipher AES-256 Storage ]
                                                             * Volatile RAM scrubbed (memset_s)
```

---

## Repository Structure

```
shruti/
 ├── docs/
 │    ├── PRD.md                       # Product Requirements Document
 │    ├── TRD.md                       # Technical Requirements Document
 │    └── architecture.md              # End-to-End System & Latency Specifications
 ├── app/
 │    ├── build.gradle.kts             # Android application configuration
 │    ├── CMakeLists.txt               # NDK build script for C++ DSP engine
 │    └── src/
 │         ├── main/
 │         │    ├── cpp/
 │         │    │    ├── include/
 │         │    │    │    ├── shruti_ring_buffer.hpp      # Lockless circular queue
 │         │    │    │    ├── shruti_memory_sanitizer.hpp # Secure volatile wiping
 │         │    │    │    └── shruti_onnx_bridge.hpp      # ORT C++ session wrapper
 │         │    │    └── src/
 │         │    │         ├── shruti_ring_buffer.cpp
 │         │    │         └── shruti_audio_pipeline.cpp   # WebRTC <-> ORT bridge
 │         │    ├── java/org/seven_cgpalabs/shruti/
 │         │    │    ├── core/
 │         │    │    │    ├── ShrutiAudioEngine.kt        # JNI controller
 │         │    │    │    ├── ShrutiLatentStore.kt        # SQLCipher vector store
 │         │    │    │    └── ShrutiVadController.kt      # Silero VAD manager
 │         │    │    ├── filter/
 │         │    │    │    ├── TraiNumberMatcher.kt        # 140/160 regex evaluator
 │         │    │    │    └── Trai1909Reporter.kt         # 1909 SMS builder
 │         │    │    ├── service/
 │         │    │    │    └── ShrutiCallScreeningService.kt # Telecom interceptor
 │         │    │    └── ui/
 │         │    │         ├── ShrutiAcousticVisualizer.kt # Zero-text orbital visual
 │         │    │         └── ShrutiFloatingOverlay.kt    # Lockscreen action chips
 │         │    └── AndroidManifest.xml
 │         └── test/                   # Unit & JNI memory wipe verification tests
 └── tools/
      ├── quantize_models.py           # ONNX Runtime INT4/INT8 quantization utility
      └── compile_qnn_context.sh       # Qualcomm Hexagon HTP binary builder
```

---

## Prerequisites & System Requirements

### Host Environment
- **JDK:** OpenJDK 17 or 21
- **Android Studio:** Jellyfish | Koala (2024.1+)
- **Android NDK:** `26.3.11579264` (r26c)
- **CMake:** `3.22.1+`
- **Qualcomm Neural Processing SDK (Optional):** QNN v2.22+ for direct HTP context compilation

### Target Mobile Silicon
- **Tier 1 (Full HTP / APU Acceleration):**
  - Qualcomm Snapdragon 8 Gen 2, Gen 3, Gen 4 (Hexagon Tensor Processor)
  - MediaTek Dimensity 9200, 9300, 9400 (MediaTek APU via NNAPI/Neuron)
- **Tier 2 (CPU ARM NEON Fallback):**
  - Modern ARMv8.2-A or ARMv9 cores with FP16 dot-product extensions.

---

## Setup & Build Instructions

### 1. Clone with Submodules
```bash
git clone --recurse-submodules https://github.com/your-org/shruti.git
cd shruti
```

### 2. Download and Quantize Model Weights
S.H.R.U.T.I. uses optimized ONNX weights. Ensure models are converted and placed into the app assets directory:
```bash
# Set up Python environment
python3 -m venv .venv
source .venv/bin/activate
pip install -r tools/requirements-converter.txt

# Run automated quantization pipeline
python tools/quantize_models.py \
    --vad-model silero_vad \
    --codec-model mimi-int8 \
    --slm-model llama-3.2-1b-audio-awq \
    --output-dir app/src/main/assets/models/
```

### 3. Build Native Libraries and APK
```bash
# Grant execution permissions
chmod +x gradlew

# Compile native C++ DSP binaries and debug APK
./gradlew assembleDebug
```

---

## TRAI Regulatory Engine & Anti-Spam

The Indian Telecom Regulatory Authority of India (TRAI) enforces explicit prefixes for enterprise calling. S.H.R.U.T.I. executes deterministic pre-filtering before initializing any ML weights:

| Number Prefix | Series Designation | Pipeline Action | Compute Layer | Latency |
| :--- | :--- | :--- | :--- | :--- |
| `140xxxxxxx` | Promotional Telemarketing | Silent Decline & Disallow | Regex Matcher | $< 1\text{ ms}$ (CPU) |
| `160xxxxxxx` | Transactional / Service | Priority Screening (Couriers/Banks) | Codec + S2S Engine | $\sim 280\text{ ms}$ (NPU) |
| Unknown 10-digit | Unregistered Entity | Conversational Interrogation | Codec + S2S Engine | $\sim 360\text{ ms}$ (NPU) |

### Automated 1909 DND Violation Workflow
If an unregistered mobile number attempts unsolicited commercial communication:
1. S.H.R.U.T.I. extracts caller commercial intent and logs the timestamp.
2. The call terminates cleanly.
3. An automated notification appears with a single action: **"Report to 1909"**.
4. Tapping fires an SMS to `1909` conforming to TRAI formatting:
   ```
   Unsolicited Loan Offer, +919876543210, 19/09/26, 14:32
   ```

---

## Zero-Persistence Privacy Invariant

S.H.R.U.T.I. enforces privacy through mathematical design rather than policy:

1. **Volatile RAM Scrubbing:**
   Incoming audio frames are placed in non-pageable C++ memory buffers (`MAP_LOCKED`). Upon inference completion, buffers are explicitly overwritten:
   ```cpp
   // Cryptographic zeroing of memory frame
   wipe_pcm_frame(frame_ptr, sample_count);
   ```
2. **Non-Invertible Semantic Embeddings:**
   Audio is compressed into non-invertible unit vectors $\mathbf{v} \in \mathbb{S}^{511}$ via continuous projection:
   $$\mathbf{v} = \frac{\text{Proj}(\mathbf{h}_{\text{latent}})}{\|\text{Proj}(\mathbf{h}_{\text{latent}})\|_2}$$
   Reconstruction of the original waveform $\mathbf{x} \in \mathbb{R}^N$ from $\mathbf{v}$ is an underdetermined problem ($512$ floating-point values vs. $160,000$ acoustic phase samples), rendering voice recovery impossible even in a forensic memory dump.

---

## Documentation Links

- [Product Requirements Document (PRD)](docs/PRD.md)
- [Technical Requirements Document (TRD)](docs/TRD.md)
- [System & ML Architecture Document](docs/architecture.md)

---

## License

Copyright © 2026 S.H.R.U.T.I. Systems. Distributed under the Apache 2.0 License.