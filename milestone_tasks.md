# S.H.R.U.T.I. Implementation Milestones & Task Breakdown

---

## Executive Summary

**Project Codename:** S.H.R.U.T.I. (Aegis-Voice)  
**Target Platform:** Android 14.0+ (API Level 34 & 35) (Compatible down to Android 10 / API 29)  
**Target Package:** `org.seven_cgpalabs.shruti`  
**Silicon Targets:** Universal Mobile GPUs via Vulkan 1.1+ (Qualcomm Adreno 6xx/7xx/8xx, ARM Mali-Gxx / Immortalis, Imagination PowerVR, Samsung Xclipse / AMD RDNA2) with multithreaded ARM NEON CPU Fallback  
**S2S / Debrief Engine:** Pruned `Qwen3-Omni-3B` (Quantized via Q4_K_M GGUF format for `llama.cpp` Vulkan backend)  
**Total Estimated Timeframe:** 16 Sprints (~16 Weeks / 4 Months)  
**Architecture:** Passive In-Call Voice Vectorizer, Universal Vulkan & `llama.cpp` Hardware Execution Pipeline, GGUF Unified Packaging, Lockless C++20 Ring Buffer, Zero-Copy FlatBuffers (`.vecstream`), SQLCipher AES-256 Storage, TRAI Regulatory Engine (140/160/1909), `ROLE_DIALER` Full Dialer UI (with Emergency 112/911 routing), Human-in-the-Loop 1909 SMS Intent, Gemini-Style UI.

---

## Milestone Roadmap & Sprint Schedule

```
+-------------------------------------------------------------------------------------------------------+
|  Phase 0: Scaffolding & Native Core | Sprints 1–2 (Weeks 1–2)                                          |
|  - C++20 SPSC Ring Buffer, memory sanitizer (memset_s), CMake/Gradle scaffolding, GGUF tooling       |
+-------------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+-------------------------------------------------------------------------------------------------------+
|  Phase 1: MVP Alpha (Passive Ingress & Vulkan Vectorizer) | Sprints 3–7 (Weeks 3–7)                   |
|  - Audio Ingress (InCallService), Silero VAD (CPU), Vulkan Vectorizer, FlatBuffers .vecstream, UI     |
+-------------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+-------------------------------------------------------------------------------------------------------+
|  Phase 2: Beta (ROLE_DIALER & Vulkan Engine) | Sprints 8–12 (Weeks 8–12)                              |
|  - Full Dialer UI & Emergency 112 Pass-Through, TRAI 140/160 regex, One-Tap 1909 Intent, Vulkan GPU   |
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
| **Phase 0** | **Engineering Scaffolding & Native DSP** | Sprints 1–2 | Weeks 1–2 | Project layout, NDK C++20 setup with Vulkan headers, SPSC Ring Buffer, memory sanitizer, GGUF tooling. |
| **Phase 1** | **MVP Alpha (Passive Ingress & Vulkan Vectorizer)** | Sprints 3–7 | Weeks 3–7 | Audio Ingress (`InCallService`), Silero VAD CPU integration, `llama.cpp` Vulkan Vectorizer, FlatBuffers `.vecstream` storage, Gemini-style visualizer. |
| **Phase 2** | **Beta (ROLE_DIALER, Vulkan & TRAI Engine)** | Sprints 8–12 | Weeks 8–12 | Full `ROLE_DIALER` UI & Emergency 112/911 pass-through, TRAI 140/160 rules, Human-in-the-Loop 1909 Intent, `ggml-vulkan` backend, dynamic barge-in. |
| **Phase 3** | **Production (Ambient Mode & Policy Audit)**| Sprints 13–16 | Weeks 13–16 | `SoundTrigger` low-power hardware DSP, process sandboxing IPC, Play Store policy & security audit. |

---

## Detailed Sprint Breakdown & Actionable Tasks

#### Sprint 2 (Week 2): Volatile Memory Sanitization & Model Quantization Tooling
- [ ] **Task 0.2.1: Cryptographic Memory Sanitizer (`shruti_memory_sanitizer.hpp`)**
- [ ] **Task 0.2.2: Automated GGUF Conversion & Quantization Pipeline (`tools/quantize_models.py`)**
  - Convert and quantize `Qwen3-Omni-3B` to Q4_K_M GGUF format (~725 MB) using `llama-quantize`.
- [ ] **Task 0.2.3: Vulkan Compute Shader Compilation (`tools/compile_vulkan_shaders.sh`)**
  - Compile SPIR-V compute shaders for `ggml-vulkan` mobile runtime.

#### Sprint 5 (Week 5): Universal Vulkan Vectorizer & Serialization Engine
- [ ] **Task 1.5.1: `llama.cpp` / GGML Vulkan Dual-Head Vectorizer Integration**
  - Integrate static Log-Mel Q8_0 GGUF vectorizer extracting 192-d speaker identity and 64-d prosody in $< 1.6\text{ ms}$ over Vulkan compute shaders.
- [ ] **Task 1.5.2: GGML Vulkan Streaming Whisper Audio Encoder**
  - Extract 128-d semantic embedding ($w_{\text{semantic}}$) and 1-d pause delta ($\Delta_{\text{pause}}$) in $< 14\text{ ms}$ over Vulkan backend.
- [ ] **Task 1.5.3: Memory-Mapped FlatBuffers `.vecstream` Zero-Copy Serializer**
  - Serialize composite vectors $v_t \in \mathbb{R}^{384}$ directly into encrypted memory-mapped storage without intermediate object allocations.
- [ ] **Task 1.5.4: Streaming 30-Second Chunk-and-Flush KV-Cache Engine**
  - Implement 30s semantic chunk processing with complete `memset_s` KV-cache flushing to maintain constant ~450 MB RAM footprint over 10 to 60+ min calls.

#### Sprint 15 (Week 15): Spoken Call Debriefs & Voice Retrieval Engine
- [ ] **Task 3.15.1: Zero-Text Spoken Debrief Generator**
  - Synthesize 5-to-10 second expressive spoken summary on demand via `llama.cpp` Vulkan SLM (`Qwen3-Omni-3B` Q4_K_M) with rich SSML prosody and speaker attribution.
- [ ] **Task 3.15.2: Voice-to-Voice Latent Similarity Search**
- [ ] **Task 3.15.3: Hierarchical Vector Trajectory Matrix Storage & Multi-Vector Soft Prompt Debrief**
  - Serialize $K \times 512$ trajectory matrix `session_trajectory` in SQLCipher (AES-256-GCM) and project as soft prompt sequence for long-meeting spoken debriefs.
