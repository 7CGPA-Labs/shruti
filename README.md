# S.H.R.U.T.I. (श्रुति)
### **S**peech-native **H**ardware **R**untime for **U**biquitous **T**elephony **I**ntelligence

[![Android API](https://img.shields.io/badge/Android-14%20%7C%2015%20(API%2034--35)-3DDC84?logo=android&logoColor=white)](#prerequisites--system-requirements)
[![NDK](https://img.shields.io/badge/NDK-r26c%2B%20(C%2B%2B20)-00599C?logo=c%2B%2B&logoColor=white)](#native-dsp--c-engine)
[![Runtime](https://img.shields.io/badge/ONNX%20Runtime-Mobile%20v1.19%2B-005CED?logo=onnx&logoColor=white)](#heterogeneous-ml-pipeline)
[![NPU Backend](https://img.shields.io/badge/Hardware%20NPU-Qualcomm%20QNN%20%7C%20NNAPI-FF3E00)](#heterogeneous-ml-pipeline)
[![Privacy](https://img.shields.io/badge/Privacy-Zero--Disk%20Audio%20%2F%20Zero--Text-brightgreen)](#zero-persistence-privacy-invariant)
[![TRAI Compliance](https://img.shields.io/badge/TRAI-140%20%7C%20160%20%7C%201909%20Compliant-orange)](#trai-regulatory-engine--anti-spam)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

> **Etymology & Philosophy**  
> In classical Sanskrit, ***Śruti*** (श्रुति) signifies *"that which is heard"*—knowledge transmitted directly through acoustic perception without written mediation.  
> **S.H.R.U.T.I.** (Wake-Word: `"Hey Shruti"`) mirrors this philosophy as an on-device, zero-text, audio-native telephony assistant. Powered by **`Qwen3-Omni-3B` (INT4 AWQ)**, it eliminates cascading Speech-to-Text (STT) $\rightarrow$ LLM $\rightarrow$ Text-to-Speech (TTS) pipelines in favor of direct Speech-to-Speech (S2S) discrete token inference executed entirely on local mobile NPU silicon.

---

## Key Highlights

- **Passive In-Call & Ambient Voice Vectorizer:** Operates strictly as a zero-touch passive listener during calls and ambient meetings. The AI never interrupts, intercepts, or converses with callers live; incoming calls use the standard default dialer `[Decline]` / `[Answer]`.
- **LiteRT Tri-Tier Hardware Execution Pipeline:**
  - **Tier 1 (NPU - Snapdragon HTP / MTK APU):** INT8 QAT Dual-Head Vectorizer extracts 192-d speaker identity ($e_{\text{speaker}}$) and 64-d prosody/energy dynamics ($z_{\text{prosody}}$) in $< 1.8\text{ ms}$ with static tensor allocation (`1, 50, 80`).
  - **Tier 2 (GPU - Qualcomm Adreno / ARM Mali via OpenCL):** Streaming Whisper / SLM semantic encoder extracting 128-d semantic intent ($w_{\text{semantic}}$) + 1-d pause delta ($\Delta_{\text{pause}}$) in $< 18\text{ ms}$.
  - **Tier 3 (CPU - ARM NEON / XNNPACK):** Silero VAD v5 ($< 2.5\text{ ms}$) and lock-free C++ DSP ring buffer.
- **Post-Call Expressive Storytelling / Spoken Debrief:** Transforms on-device vector streams into expressive spoken summaries with speaker attribution, dynamic prosody (SSML), and conversational brevity on demand once calls or ambient sessions conclude.
- **Google Play & DoT Policy Compliant:**
  - **Human-in-the-Loop 1909 Intent:** Pre-filled `Intent.ACTION_SENDTO` (`smsto:1909`) for single-tap user SMS confirmation (Play Store `SEND_SMS` policy compliant).
  - **`ROLE_DIALER` Emergency Routing:** Complete dialer client with instant zero-latency pass-through for Emergency numbers (`112` / `911`).
  - **Zero Caller-Impersonation Risk:** The AI is strictly passive on calls; assistant persona is transparently established during first-time voice onboarding.
- **Zero-Persistence Privacy Invariant:** No raw audio (`.wav`, `.pcm`, `.mp3`) and no textual transcripts are ever written to flash storage. Volatile RAM buffers are aggressively scrubbed using `memset_s`.
- **Memory-Mapped FlatBuffers `.vecstream`:** High-speed, zero-copy composite vector stream storage ($v_t \in \mathbb{R}^{384}$) encrypted at rest via AES-256-GCM.

---

## High-Level Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │          Inbound PSTN / Cellular Call        │
                  └──────────────────────┬───────────────────────┘
                                         │
                         ┌───────────────┴───────────────┐
                         │   TRAI O(1) Prefix Evaluator  │
                         └───────┬───────────────┬───────┘
                     Matches 140 │               │ Pass / Unknown / 160
                                 ▼               ▼
                       [ Instant Drop ]    [ Default Dialer UI ]
                                           [Decline]   [Answer]
                                                 │
                                                 ▼ (User Answers - Passive Mode)
                                   ┌───────────────────────────┐
                                   │ Lockless SPSC Ring Buffer │
                                   │ (AudioRingBuffer.cpp)     │
                                   └─────────────┬─────────────┘
                                                 │
                        ┌────────────────────────┴────────────────────────┐
                        ▼                                                 ▼
        ┌───────────────────────────────┐                 ┌───────────────────────────────┐
        │  Silero VAD (CPU / ARM NEON)  │                 │ LiteRT NPU Dual-Head Vector   │
        │  * Speech chunking (<2.5ms)   │                 │ * 192-d Speaker + 64-d Prosody│
        └───────────────────────────────┘                 └───────────────┬───────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ LiteRT GPU (OpenCL) Whisper   │
                                                          │ * 128-d Semantic + 1-d Pause  │
                                                          └───────────────┬───────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ FlatBuffers .vecstream Serial │
                                                          │ * Composite vt in R^384       │
                                                          └───────────────┬───────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ SQLCipher AES-256 Storage     │
                                                          │ * Volatile RAM scrub (memset) │
                                                          └───────────────┬───────────────┘
                                                                          │
                                              (Post-Call User Query / Hero Orbit Tap)
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────────┐
                                                          │ Expressive SLM Debrief Engine │
                                                          │ * Qwen3-Omni-3B / SSML Prosody│
                                                          │ * Spoken Story & Key Insights │
                                                          └───────────────────────────────┘
```

---

## Repository Structure

```
shruti/
 ├── PRD.md                            # Product Requirements Document (v3.0.0)
 ├── TRD.md                            # Technical Requirements Document
 ├── architecture.md                   # System Architecture Specification (v3.0.0)
 ├── notebooks/
 │    └── dual_head_vectorizer_qat.ipynb# LiteRT INT8 QAT Export & Benchmarks
 ├── milestone_tasks.md                # 16-Sprint Implementation Roadmap
 ├── ui_design.md                      # Gemini-Style UI/UX & Passive In-Call Spec
 ├── backend_schema.md                 # AIDL, FlatBuffers & SQLCipher DDL Schema
 ├── restriction.md                    # Regulatory & Store Policy Analysis
 ├── TRAINING_PLAN.md                  # S2S Debrief Model Training & Surgery Plan
 ├── LICENSE                           # Apache 2.0 License
 ├── .github/workflows/build.yml       # GitHub Actions CI/CD Build Pipeline
 └── app/
      ├── build.gradle.kts             # Android application configuration
      ├── CMakeLists.txt               # NDK build script for C++ DSP engine (-std=c++20)
      └── src/
           ├── main/
                ├── cpp/
                │    ├── include/
                │    │    ├── shruti_ring_buffer.hpp      # Lockless circular queue
                │    │    └── shruti_memory_sanitizer.hpp # Volatile memory wiping (memset_s)
                │    └── src/
                │         ├── shruti_ring_buffer.cpp      # SPSC atomic memory barriers
                │         └── shruti_jni_bridge.cpp       # Native JNI entry point
                ├── java/org/seven_cgpalabs/shruti/
                │    ├── core/
                │    │    └── ShrutiAudioEngine.kt        # JNI Kotlin controller wrapper
                │    └── ui/
                │         └── ShrutiMainActivity.kt       # Activity entry point
                └── AndroidManifest.xml
```

---

## Setup & Build Instructions

### 1. Clone Repository
```bash
git clone https://github.com/7CGPA-Labs/shruti.git
cd shruti
```

### 2. Build Native Engine and Debug APK
Ensure Android NDK `r26c` and CMake `3.22.1` are installed in your Android SDK environment:
```bash
# Compile native C++20 DSP binaries and debug APK
./gradlew assembleDebug
```

- **Long Multi-Person Conversation & Vector Trajectory Architecture:** Supports 10 to 60+ minute multi-speaker meetings/conversations via CAM++ ONNX speaker diarization, streaming 30-second chunk-and-flush KV-cache management (constant ~450 MB RAM), and Vector Trajectory Matrix ($\mathbf{M}_{\text{session}} \in \mathbb{R}^{K \times 512}$) storage in SQLCipher.

---

## Documentation Index

- [Product Requirements Document (PRD)](PRD.md)
- [Technical Requirements Document (TRD)](TRD.md)
- [System Architecture Specification](architecture.md)
- [Dual-Head INT8 QAT Vectorizer Notebook](notebooks/dual_head_vectorizer_qat.ipynb)
- [Model Training & Surgery Plan](TRAINING_PLAN.md)
- [Implementation Roadmap & Milestones](milestone_tasks.md)
- [UI/UX Design Specification](ui_design.md)
- [Backend & System Schema Specification](backend_schema.md)
- [Regulatory & Store Restrictions Analysis](restriction.md)

---

## License

Copyright © 2026 7CGPA-Labs. Distributed under the Apache 2.0 License.