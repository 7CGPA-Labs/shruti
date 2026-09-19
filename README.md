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
> **S.H.R.U.T.I.** (Wake-Word: `"Hey Shruti"`) mirrors this philosophy as an on-device, zero-text, audio-native telephony assistant. Powered by **`Llama-3.2-1B-Audio-Instruct` (INT4 AWQ)**, it eliminates cascading Speech-to-Text (STT) $\rightarrow$ LLM $\rightarrow$ Text-to-Speech (TTS) pipelines in favor of direct Speech-to-Speech (S2S) discrete token inference executed entirely on local mobile NPU silicon.

---

## Key Highlights

- **Pure Speech-to-Speech Architecture ($\le 380\text{ ms}$ Turnaround):** Ingests raw 16 kHz PCM directly into neural acoustic codecs (Mimi / WavTokenizer), streams discrete tokens into `Llama-3.2-1B-Audio-Instruct` (INT4 AWQ), and reconstructs synthesized speech without generating intermediate text tokens.
- **3-Action Incoming Call UI:** Provides three explicit user action controls on incoming calls: `[Decline]`, `[Answer]`, and `[Screen with S.H.R.U.T.I. AI]`.
- **Google Play & DoT Policy Compliant:**
  - **Human-in-the-Loop 1909 Intent:** Pre-filled `Intent.ACTION_SENDTO` (`smsto:1909`) for single-tap user SMS confirmation (Play Store `SEND_SMS` policy compliant).
  - **`ROLE_DIALER` Emergency Routing:** Complete dialer client with instant zero-latency pass-through for Emergency numbers (`112` / `911`).
  - **Mandatory AI Disclosure:** System prompt enforces Turn 1 disclosure (*"I am an automated voice assistant screening this call for [User Name]..."*).
  - **DoT Interconnect Compliance:** Carrier SIP trunks terminate via licensed Indian Telecom Service Providers (TSPs).
- **Zero-Persistence Privacy Invariant:** No raw audio (`.wav`, `.pcm`, `.mp3`) and no textual transcripts are ever written to flash storage. Volatile RAM buffers are aggressively scrubbed using `memset_s`.
- **Non-Invertible Latent Storage:** Historical conversation context is stored exclusively as 512-dimensional continuous unit vectors ($\mathbf{v} \in \mathbb{S}^{511}$) in an encrypted SQLCipher database.
- **Heterogeneous Silicon Allocation:** 
  - **CPU (ARM NEON):** Silero VAD v5 (32 ms window) operates at $< 2.5\text{ ms}$ latency without waking the NPU or draining standby power.
  - **NPU (Qualcomm QNN HTP / Android NNAPI):** Accelerates neural codec encoders/decoders and the 1B INT4 S2S model.

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
                       [ Instant Drop ]    [ 3-Action Caller Screen UI ]
                                           [Decline] [Answer] [Screen AI]
                                                 │
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
                                                          │ Llama-3.2-1B S2S (HTP NPU)    │
                                                          │ * INT4 AWQ Autoregressive     │
                                                          │ * Mandatory AI Disclosure     │
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
 ├── PRD.md                            # Product Requirements Document
 ├── TRD.md                            # Technical Requirements Document
 ├── architecture.md                   # System Architecture Specification
 ├── milestone_tasks.md                # 16-Sprint Implementation Roadmap
 ├── ui_design.md                      # Gemini-Style UI/UX & 3-Action Caller Spec
 ├── backend_schema.md                 # AIDL, SQLCipher DDL & Ring Buffer Schema
 ├── restriction.md                    # Regulatory & Store Policy Analysis
 ├── implementation_plan_milestone_1.md# Sprint 1 Engineering Execution Plan
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

---

## Documentation Index

- [Product Requirements Document (PRD)](PRD.md)
- [Technical Requirements Document (TRD)](TRD.md)
- [System Architecture Specification](architecture.md)
- [Implementation Roadmap & Milestones](milestone_tasks.md)
- [UI/UX Design Specification](ui_design.md)
- [Backend & System Schema Specification](backend_schema.md)
- [Regulatory & Store Restrictions Analysis](restriction.md)

---

## License

Copyright © 2026 7CGPA-Labs. Distributed under the Apache 2.0 License.