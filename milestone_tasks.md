# S.H.R.U.T.I. Implementation Milestones & Task Breakdown

---

## Executive Summary

**Project Codename:** S.H.R.U.T.I. (Aegis-Voice)  
**Target Platform:** Android 14.0+ (API Level 34 & 35)  
**Target Package:** `org.seven_cgpalabs.shruti`  
**Silicon Targets:** Qualcomm Snapdragon 8 Gen 2 / Gen 3 / Gen 4 (QNN HTP NPU), MediaTek Dimensity 9200/9300/9400 (NNAPI/NeuroPilot), ARM NEON CPU Fallback  
**Total Estimated Timeframe:** 16 Sprints (~16 Weeks / 4 Months)  
**Architecture:** On-Device Speech-to-Speech (S2S), Lockless C++20 Ring Buffer, Zero-Disk Persistence, Dual-Stream Latent Storage (SQLCipher AES-256 + StrongBox), TRAI Regulatory Engine (140/160/1909), `ROLE_DIALER` Full Dialer UI (with Emergency 112/911 routing), Human-in-the-Loop 1909 SMS Intent, Gemini-Style UI.

---

## Milestone Roadmap & Sprint Schedule

```
+-------------------------------------------------------------------------------------------------------+
|  Phase 0: Scaffolding & Native Core | Sprints 1–2 (Weeks 1–2)                                          |
|  - C++20 SPSC Ring Buffer, memory sanitizer (memset_s), CMake/Gradle scaffolding, ML scripts        |
+-------------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+-------------------------------------------------------------------------------------------------------+
|  Phase 1: MVP Alpha (Core S2S & SIP) | Sprints 3–7 (Weeks 3–7)                                         |
|  - LiveKit WebRTC SIP Bridge, Silero VAD (CPU), CPU ONNX S2S (Mimi + 1B SLM), SQLCipher, Orbit UI     |
+-------------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+-------------------------------------------------------------------------------------------------------+
|  Phase 2: Beta (ROLE_DIALER & TRAI Engine) | Sprints 8–12 (Weeks 8–12)                                |
|  - Full Dialer UI & Emergency 112 Pass-Through, TRAI 140/160 regex, One-Tap 1909 Intent, QNN NPU      |
+-------------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+-------------------------------------------------------------------------------------------------------+
|  Phase 3: Production (Ambient & Hardening) | Sprints 13–16 (Weeks 13–16)                             |
|  - SoundTrigger hardware DSP, Process Isolation Sandbox, Mandatory AI Disclosure, Audit & Release     |
+-------------------------------------------------------------------------------------------------------+
```

| Phase | Description | Sprints | Time Period | Key Objectives |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | **Engineering Scaffolding & Native DSP** | Sprints 1–2 | Weeks 1–2 | Project layout, NDK C++20 setup, SPSC Ring Buffer, memory sanitizer, model tooling. |
| **Phase 1** | **MVP Alpha (Core S2S & SIP Screening)** | Sprints 3–7 | Weeks 3–7 | LiveKit WebRTC bridge, Silero VAD CPU integration, CPU ONNX S2S pipeline, SQLCipher vector store, Gemini-style visualizer. |
| **Phase 2** | **Beta (ROLE_DIALER, NPU & TRAI Engine)** | Sprints 8–12 | Weeks 8–12 | Full `ROLE_DIALER` UI & Emergency 112/911 pass-through, TRAI 140/160 rules, Human-in-the-Loop 1909 Intent, QNN HTP NPU backend, dynamic barge-in. |
| **Phase 3** | **Production (Ambient Mode & Policy Audit)**| Sprints 13–16 | Weeks 13–16 | `SoundTrigger` low-power hardware DSP, process sandboxing IPC, mandatory AI disclosure, Play Store policy & security audit. |

---

## Detailed Sprint Breakdown & Actionable Tasks

---

### Phase 0: Engineering Scaffolding & Native DSP Core
**Timeframe:** Sprints 1–2 (Weeks 1–2)  
**Focus:** Project structure, native NDK build system, lockless memory management, and quantization tooling.

#### Sprint 1 (Week 1): Codebase Scaffolding & C++ Native Engine Foundation [COMPLETED]
- [x] **Task 0.1.1: Project Directory Structure & Build Configuration**
- [x] **Task 0.1.2: Lockless SPSC Circular Audio Ring Buffer (`shruti_ring_buffer.hpp/.cpp`)**
- [x] **Task 0.1.3: Unit Test Framework for Native Ring Buffer (`test_ring_buffer.cpp`)**

#### Sprint 2 (Week 2): Volatile Memory Sanitization & Model Quantization Tooling
- [ ] **Task 0.2.1: Cryptographic Memory Sanitizer (`shruti_memory_sanitizer.hpp`)**
- [ ] **Task 0.2.2: Automated Quantization & Export Pipeline (`tools/quantize_models.py`)**
- [ ] **Task 0.2.3: Shell Integration Script (`tools/compile_qnn_context.sh`)**

---

### Phase 1: MVP Alpha — Core S2S Pipeline & SIP Screening
**Timeframe:** Sprints 3–7 (Weeks 3–7)  
**Focus:** LiveKit WebRTC audio transport over DoT-compliant carrier SIP DIDs, CPU-accelerated S2S inference, SQLCipher vector persistence, and Gemini-style UI.

#### Sprint 3 (Week 3): LiveKit WebRTC Ingestion & JNI Bridge
- [ ] **Task 1.3.1: JNI Controller Implementation (`ShrutiAudioEngine.kt`)**
- [ ] **Task 1.3.2: LiveKit WebRTC Custom Audio Sink & Source (`LiveKitAudioBridge.kt`)**

#### Sprint 4 (Week 4): Silero VAD CPU Integration & Schmitt Trigger Gate
- [ ] **Task 1.4.1: Silero VAD C++ Execution Manager (`ShrutiVadController.kt` / C++)**
- [ ] **Task 1.4.2: Dual-Threshold Schmitt Trigger State Machine**

#### Sprint 5 (Week 5): On-Device CPU Speech-to-Speech Engine
- [ ] **Task 1.5.1: Neural Codec Encoder/Decoder ONNX Runtime Integration**
- [ ] **Task 1.5.2: 1B S2S SLM Autoregressive Execution Engine with Mandatory AI Disclosure**
  - Implement system prompt enforcing mandatory disclosure greeting: *"I am an automated voice assistant screening this call for [User Name]..."*

#### Sprint 6 (Week 6): Non-Invertible Latent Storage & SQLCipher AES-256
- [ ] **Task 1.6.1: CLAP / WavLM Latent Projection Head**
- [ ] **Task 1.6.2: SQLCipher Database Manager (`ShrutiLatentStore.kt`)**
- [ ] **Task 1.6.3: Zero-Disk Persistence Invariant Audit**

#### Sprint 7 (Week 7): Gemini-Style UI & Action Overlay
- [ ] **Task 1.7.1: Gemini-Style Dynamic Acoustic Orbit Visualizer (`ShrutiAcousticVisualizer.kt`)**
- [ ] **Task 1.7.2: Floating Action Overlay Chips (`ShrutiFloatingOverlay.kt`)**

---

### Phase 2: Beta — ROLE_DIALER, NPU Acceleration & TRAI Engine
**Timeframe:** Sprints 8–12 (Weeks 8–12)  
**Focus:** Full `ROLE_DIALER` Android telephony UI, emergency call pass-through, TRAI rules, Human-in-the-Loop 1909 Intent, QNN NPU optimization, and dynamic barge-in.

#### Sprint 8 (Week 8): Full Dialer UI & Emergency Call Pass-Through (`ROLE_DIALER`)
- [ ] **Task 2.8.1: Full Dialer Interface Implementation (`ShrutiDialerActivity.kt`)**
  - Build complete dialpad, call history log, and contact picker UI to satisfy Google Play `ROLE_DIALER` requirements.
- [ ] **Task 2.8.2: Emergency Call Routing Pass-Through Engine**
  - Implement instant zero-latency pass-through for emergency calls (`112`, `911`, `100`, `101`, `102`), completely bypassing AI screening.

#### Sprint 9 (Week 9): TRAI O(1) Prefix Rule Matcher Engine
- [ ] **Task 2.9.1: Deterministic Pattern Evaluator (`TraiNumberMatcher.kt`)**
- [ ] **Task 2.9.2: TRAI Rules Performance & Unit Test Suite**

#### Sprint 10 (Week 10): Automated TRAI 1909 Human-in-the-Loop Reporting
- [ ] **Task 2.10.1: Unregistered Commercial Call Classifier (`Trai1909Reporter.kt`)**
- [ ] **Task 2.10.2: One-Tap Pre-Filled `Intent.ACTION_SENDTO` Builder**
  - Build system intent launching pre-filled SMS app to `smsto:1909` upon user tap on `[Send 1909 Report]` chip. Omit direct background `SEND_SMS` to ensure Google Play Store compliance.

#### Sprint 11 (Week 11): Qualcomm QNN HTP NPU Backend Acceleration
- [ ] **Task 2.11.1: ONNX Runtime QNN Execution Provider Configuration**
- [ ] **Task 2.11.2: Unified NPU Memory Tensor Arenas**

#### Sprint 12 (Week 12): Dynamic Conversational Barge-In Manager
- [ ] **Task 2.12.1: Dynamic Interruption Detector Circuit**
- [ ] **Task 2.12.2: Instantaneous Playback Queue Truncation ($\le 40\text{ ms}$)**

---

### Phase 3: Production Release — Ambient Mode, Policy Audit & Hardening
**Timeframe:** Sprints 13–16 (Weeks 13–16)  
**Focus:** Low-power hardware DSP ambient mode, process isolation sandboxing, spoken debriefs, and system-wide Play Store compliance verification.

#### Sprint 13 (Week 13): Power-Gated Ambient Context Engine
- [ ] **Task 3.13.1: SoundTrigger Hardware DSP HAL Integration**
  - Use low-power hardware wake-words to avoid continuous background microphone FGS running and prevent OS privacy dot violations.
- [ ] **Task 3.13.2: Peripheral & Power-State Gating**

#### Sprint 14 (Week 14): Multi-Process Security Sandboxing & IPC
- [ ] **Task 3.14.1: Sandbox Architecture & AIDL IPC Design (`IShrutiAudioPipeline.aidl`)**
- [ ] **Task 3.14.2: Sandbox Verification & Air-Gap Testing**

#### Sprint 15 (Week 15): Spoken Call Debriefs & Voice Retrieval Engine
- [ ] **Task 3.15.1: Zero-Text Spoken Debrief Generator**
- [ ] **Task 3.15.2: Voice-to-Voice Latent Similarity Search**

#### Sprint 16 (Week 16): System Benchmarking, Policy Audit & Release Packaging
- [ ] **Task 3.16.1: End-to-End Latency Budget Validation ($\le 380\text{ ms}$)**
- [ ] **Task 3.16.2: Google Play Policy Compliance Audit**
  - Verify mandatory AI disclosure utterance on Turn 1.
  - Verify Emergency `112`/`911` call pass-through in `ROLE_DIALER`.
  - Verify absence of programmatic `SEND_SMS` permission.
- [ ] **Task 3.16.3: Memory Sanitization Forensic Audit & Battery Test**
- [ ] **Task 3.16.4: Production Build & Release Candidate Packaging**

---

## Definition of Done (DoD) & Policy Verification Matrix

1. **Zero-Persistence Invariant:** No `.wav`, `.pcm`, `.mp3`, or plaintext transcript files exist anywhere on flash storage (`/data/data/org.seven_cgpalabs.shruti/`).
2. **Google Play Store Policy Compliant:**
   * `SEND_SMS` permission omitted; 1909 complaints use pre-filled `Intent.ACTION_SENDTO`.
   * `ROLE_DIALER` implemented with complete dialpad, contacts, and instant Emergency `112`/`911` pass-through.
   * AI synthetic media disclosure greeting played automatically on Turn 1.
3. **DoT Telecom Compliant:** Carrier SIP trunks terminate via licensed Indian TSPs.
4. **Turnaround Latency:** Total round-trip time between caller speech end and AI response playback initiation is $\le 380\text{ ms}$ on Snapdragon 8 Gen 2+.
5. **Air-Gap Security:** The ML engine process operates with zero network permissions.
