# S.H.R.U.T.I. Implementation Milestones & Task Breakdown

---

## Executive Summary

**Project Codename:** S.H.R.U.T.I. (Aegis-Voice)  
**Target Platform:** Android 14.0+ (API Level 34 & 35)  
**Target Package:** `org.seven_cgpalabs.shruti`  
**Silicon Targets:** Qualcomm Snapdragon 8 Gen 2 / Gen 3 / Gen 4 (QNN HTP NPU), MediaTek Dimensity 9200/9300/9400 (NNAPI/NeuroPilot), ARM NEON CPU Fallback  
**S2S / Debrief Engine:** `Qwen3-Omni-3B` (Quantized via INT4 AWQ)  
**Total Estimated Timeframe:** 16 Sprints (~16 Weeks / 4 Months)  
**Architecture:** Passive In-Call Voice Vectorizer, LiteRT Tri-Tier Hardware Execution Pipeline (NPU + GPU OpenCL + CPU), Lockless C++20 Ring Buffer, Zero-Copy FlatBuffers (`.vecstream`), SQLCipher AES-256 Storage, TRAI Regulatory Engine (140/160/1909), `ROLE_DIALER` Full Dialer UI (with Emergency 112/911 routing), Human-in-the-Loop 1909 SMS Intent, Gemini-Style UI.

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
|  Phase 1: MVP Alpha (Passive Ingress & Vectorizer) | Sprints 3–7 (Weeks 3–7)                          |
|  - Audio Ingress (InCallService), Silero VAD (CPU), Dual-Head Vectorizer, FlatBuffers .vecstream, UI  |
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
|  - SoundTrigger hardware DSP, Process Isolation Sandbox, Play Store policy & security audit           |
+-------------------------------------------------------------------------------------------------------+
```

| Phase | Description | Sprints | Time Period | Key Objectives |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | **Engineering Scaffolding & Native DSP** | Sprints 1–2 | Weeks 1–2 | Project layout, NDK C++20 setup, SPSC Ring Buffer, memory sanitizer, model tooling. |
| **Phase 1** | **MVP Alpha (Passive Ingress & Vectorization)** | Sprints 3–7 | Weeks 3–7 | Audio Ingress (`InCallService`), Silero VAD CPU integration, LiteRT Dual-Head Vectorizer, FlatBuffers `.vecstream` storage, Gemini-style visualizer. |
| **Phase 2** | **Beta (ROLE_DIALER, NPU & TRAI Engine)** | Sprints 8–12 | Weeks 8–12 | Full `ROLE_DIALER` UI & Emergency 112/911 pass-through, TRAI 140/160 rules, Human-in-the-Loop 1909 Intent, QNN HTP NPU backend, dynamic barge-in. |
| **Phase 3** | **Production (Ambient Mode & Policy Audit)**| Sprints 13–16 | Weeks 13–16 | `SoundTrigger` low-power hardware DSP, process sandboxing IPC, Play Store policy & security audit. |

---

## Detailed Sprint Breakdown & Actionable Tasks

#### Sprint 2 (Week 2): Volatile Memory Sanitization & Model Quantization Tooling
- [ ] **Task 0.2.1: Cryptographic Memory Sanitizer (`shruti_memory_sanitizer.hpp`)**
- [ ] **Task 0.2.2: Automated Quantization & Export Pipeline (`tools/quantize_models.py`)**
  - Convert and quantize `Qwen3-Omni-3B` to INT4 AWQ ONNX format (~620 MB).
- [ ] **Task 0.2.3: Shell Integration Script (`tools/compile_qnn_context.sh`)**

#### Sprint 5 (Week 5): LiteRT Tri-Tier Vectorizer & Serialization Engine
- [ ] **Task 1.5.1: LiteRT NPU INT8 Dual-Head Vectorizer Integration**
  - Integrate static `(1, 50, 80)` INT8 QAT vectorizer extracting 192-d speaker identity and 64-d prosody in $< 1.8\text{ ms}$.
- [ ] **Task 1.5.2: LiteRT GPU Streaming Whisper & SLM Semantic Vectorizer**
  - Extract 128-d semantic embedding ($w_{\text{semantic}}$) and 1-d pause delta ($\Delta_{\text{pause}}$) in $< 18\text{ ms}$ over OpenCL delegate.
- [ ] **Task 1.5.3: Memory-Mapped FlatBuffers `.vecstream` Zero-Copy Serializer**
  - Serialize composite vectors $v_t \in \mathbb{R}^{384}$ directly into encrypted memory-mapped storage without intermediate object allocations.
- [ ] **Task 1.5.4: Streaming 30-Second Chunk-and-Flush KV-Cache Engine**
  - Implement 30s semantic chunk processing with complete `memset_s` KV-cache flushing to maintain constant ~450 MB RAM footprint over 10 to 60+ min calls.

#### Sprint 15 (Week 15): Spoken Call Debriefs & Voice Retrieval Engine
- [ ] **Task 3.15.1: Zero-Text Spoken Debrief Generator**
  - Synthesize 5-to-10 second expressive spoken summary on demand via `Qwen3-Omni-3B` with rich SSML prosody and speaker attribution.
- [ ] **Task 3.15.2: Voice-to-Voice Latent Similarity Search**
- [ ] **Task 3.15.3: Hierarchical Vector Trajectory Matrix Storage & Multi-Vector Soft Prompt Debrief**
  - Serialize $K \times 512$ trajectory matrix `session_trajectory` in SQLCipher (AES-256-GCM) and project as soft prompt sequence for long-meeting spoken debriefs.
