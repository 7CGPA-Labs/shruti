# S.H.R.U.T.I. Implementation Milestones & Task Breakdown

---

## Executive Summary

**Project Codename:** S.H.R.U.T.I. (Aegis-Voice)  
**Target Platform:** Android 14.0+ (API Level 34 & 35)  
**Target Package:** `org.seven_cgpalabs.shruti`  
**Silicon Targets:** Qualcomm Snapdragon 8 Gen 2 / Gen 3 / Gen 4 (QNN HTP NPU), MediaTek Dimensity 9200/9300/9400 (NNAPI/NeuroPilot), ARM NEON CPU Fallback  
**S2S SLM Engine:** `Llama-3.2-1B-Audio-Instruct` (Quantized via INT4 AWQ)  
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
|  - LiveKit WebRTC SIP Bridge, Silero VAD (CPU), CPU Llama-3.2-1B-Audio S2S, SQLCipher, Orbit UI       |
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
| **Phase 1** | **MVP Alpha (Core S2S & SIP Screening)** | Sprints 3–7 | Weeks 3–7 | LiveKit WebRTC bridge, Silero VAD CPU integration, CPU `Llama-3.2-1B-Audio-Instruct` pipeline, SQLCipher vector store, Gemini-style visualizer. |
| **Phase 2** | **Beta (ROLE_DIALER, NPU & TRAI Engine)** | Sprints 8–12 | Weeks 8–12 | Full `ROLE_DIALER` UI & Emergency 112/911 pass-through, TRAI 140/160 rules, Human-in-the-Loop 1909 Intent, QNN HTP NPU backend, dynamic barge-in. |
| **Phase 3** | **Production (Ambient Mode & Policy Audit)**| Sprints 13–16 | Weeks 13–16 | `SoundTrigger` low-power hardware DSP, process sandboxing IPC, mandatory AI disclosure, Play Store policy & security audit. |

---

## Detailed Sprint Breakdown & Actionable Tasks

#### Sprint 2 (Week 2): Volatile Memory Sanitization & Model Quantization Tooling
- [ ] **Task 0.2.1: Cryptographic Memory Sanitizer (`shruti_memory_sanitizer.hpp`)**
- [ ] **Task 0.2.2: Automated Quantization & Export Pipeline (`tools/quantize_models.py`)**
  - Convert and quantize `Llama-3.2-1B-Audio-Instruct` to INT4 AWQ ONNX format (~620 MB).
- [ ] **Task 0.2.3: Shell Integration Script (`tools/compile_qnn_context.sh`)**

#### Sprint 5 (Week 5): On-Device CPU Speech-to-Speech Engine
- [ ] **Task 1.5.1: Neural Codec Encoder/Decoder ONNX Runtime Integration**
- [ ] **Task 1.5.2: `Llama-3.2-1B-Audio-Instruct` Autoregressive Engine with Mandatory AI Disclosure**
  - Implement system prompt enforcing mandatory disclosure greeting: *"I am an automated voice assistant screening this call for [User Name]..."*

#### Sprint 15 (Week 15): Spoken Call Debriefs & Voice Retrieval Engine
- [ ] **Task 3.15.1: Zero-Text Spoken Debrief Generator**
  - Synthesize 5-to-10 second spoken summary on demand via `Llama-3.2-1B-Audio-Instruct`.
- [ ] **Task 3.15.2: Voice-to-Voice Latent Similarity Search**
