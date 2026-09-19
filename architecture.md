# System Architecture Specification

---

### 1. Document Control & Architecture Overview

* **Project Codename:** S.H.R.U.T.I. (Aegis-Voice)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU SoCs (Snapdragon 8 Gen 2/3/4, MediaTek Dimensity 9200/9300/9400)
* **S2S SLM Model:** `Llama-3.2-1B-Audio-Instruct` (INT4 AWQ)
* **Document Version:** 2.3.0 (Llama-3.2-1B-Audio-Instruct Model Specification)
* **Status:** Approved System Architecture

---

### 2. High-Level System Architecture

```
+-----------------------------------------------------------------------------------------+
|                                    ANDROID SYSTEM                                       |
|                                                                                         |
|   +--------------------------+                         +----------------------------+   |
|   |   PSTN / Cellular Call   |                         |     Android Framework      |   |
|   |  (Emergency 112/911 Direct|                         | (Telecom / ROLE_DIALER)    |   |
|   +------------+-------------+                         +-------------+--------------+   |
|                |                                                     |                  |
|                | DoT Licensed Carrier SIP Trunk                      | Control Signals  |
|                v                                                     v                  |
|   +---------------------------------------------------------------------------------+   |
|   |                   Telephony & Ingestion Subsystem (:telephony)                  |   |
|   |  - LiveKit WebRTC Transport Engine (Duplex 16 kHz OPUS/PCM)                     |   |
|   |  - 3-Action Caller Screen UI (Decline / Answer / Screen with S.H.R.U.T.I. AI)   |   |
|   |  - TRAI O(1) Prefix Matcher & Emergency 112/911 Pass-Through                    |   |
|   |  - Human-in-the-Loop 1909 Intent Builder (Intent.ACTION_SENDTO)                 |   |
|   |  - Permission: android.permission.INTERNET, RECORD_AUDIO                        |   |
|   +------------------------------------+--------------------------------------------+   |
|                                        | Raw PCM (16 kHz) via AIDL Binder IPC           |
|                                        v                                                |
|   +---------------------------------------------------------------------------------+   |
|   |              Native C++ DSP & Ring Buffer Engine (In-Memory)                    |   |
|   |  - SPSC Lockless Circular Ring Buffer (AudioRingBuffer.cpp)                     |   |
|   |  - Real-time Barge-In Detector & Playback Flush Manager                         |   |
|   |  - Volatile Memory Sanitizer (wipe_pcm_frame via memset_s)                      |   |
|   +-------------------+----------------------------------------+--------------------+   |
|                       | Audio Frames (20ms)                    | Synthesized PCM        |
|                       v                                        ^                        |
|   +------------------------------------------------------------+--------------------+   |
|   |            On-Device Heterogeneous ML Pipeline Subsystem (:ml_core)              |   |
|   |  - STRICTLY NO android.permission.INTERNET (Air-Gapped Sandbox)                 |   |
|   |                                                                                 |   |
|   |   +-------------------------------------------------------------------------+   |   |
|   |   | CPU Execution Layer (ARM NEON / XNNPACK)                                |   |   |
|   |   | - Silero VAD v5 (32ms sliding chunk analysis, < 2.5ms latency)          |   |   |
|   |   +------------------------------------+------------------------------------+   |   |
|   |                                        | Speech Gate                            |   |
|   |                                        v                                        |   |
|   |   +-------------------------------------------------------------------------+   |   |
|   |   | Qualcomm QNN / HTP / NNAPI NPU Subsystem                                |   |   |
|   |   | - Neural Acoustic Codec Encoder (Mimi/WavTokenizer, INT8, 50Hz)         |   |   |
|   |   | - Llama-3.2-1B-Audio-Instruct S2S SLM (INT4 AWQ, Mandatory AI Disclosure)|   |   |
|   |   | - Neural Acoustic Codec Decoder (Synthesis to 16kHz PCM)                |   |   |
|   |   | - Non-Invertible Semantic Vector Projector (WavLM/CLAP, 512-d Float32)  |   |   |
|   |   +------------------------------------+------------------------------------+   |   |
|   +----------------------------------------|----------------------------------------+   |
|                                            | 512-d Latent Vectors (Non-Invertible)      |
|                                            v                                            |
|   +---------------------------------------------------------------------------------+   |
|   |                 Cryptographic Storage & Memory Architecture                     |   |
|   |  - SQLCipher Encrypted Database (AES-256-GCM via Android StrongBox Keystore)    |   |
|   |  - Zero Disk Persistence Guarantee: NO .wav, NO .pcm, NO text transcripts      |   |
|   +------------------------------------+--------------------------------------------+   |
|                                        | Vector Descriptors                             |
|                                        v                                                |
|   +---------------------------------------------------------------------------------+   |
|   |                    User Experience & Visual Interface Layer                     |   |
|   |  - 3-Action Caller UI (Decline, Answer, Screen with S.H.R.U.T.I. AI)            |   |
|   |  - Gemini-Style Bottom Sheet & Full Screen Ambient Glow                         |   |
|   |  - Action Chips ([Take Over Call], [Preset Voice], [Send 1909 Report])          |   |
|   +---------------------------------------------------------------------------------+   |
+-----------------------------------------------------------------------------------------+
```

---

### 3. Subsystem Breakdown & Component Contracts

#### 3.1 On-Device ML Engine (`org.seven_cgpalabs.shruti.core`)
* **`Llama-3.2-1B-Audio-Instruct` (INT4 AWQ):** Autoregressive S2S SLM execution engine running on QNN HTP NPU / NNAPI.
* **System Prompt Contract:** Forces mandatory Turn 1 AI synthetic media disclosure greeting:
  `"I am an automated voice assistant screening this call for [User Name]. Please state the reason for your call."`

---

### 4. Latency Budget Analysis

| Pipeline Stage | Implementation Detail | Processing Time | Cumulative Latency |
| :--- | :--- | :--- | :--- |
| **VAD Trailing Silence Window** | Silero VAD boundary detection | 180 ms | 180 ms |
| **WebRTC OPUS Ingestion & Jitter** | LiveKit OPUS decode | 20 ms | 200 ms |
| **Ring Buffer Enqueue** | Lockless atomic copy | 2 ms | 202 ms |
| **Neural Codec Encoding (QNN HTP)** | Mimi INT8 Encoder | 12 ms | 214 ms |
| **Llama-3.2-1B-Audio Prefill & 1st Token** | INT4 AWQ execution on QNN HTP NPU | 75 ms | 289 ms |
| **Neural Codec Decoding (QNN HTP)** | Mimi INT8 Decoder | 15 ms | 304 ms |
| **Ring Buffer Dequeue & Uplink** | Atomic pop to LiveKit sink | 2 ms | 306 ms |
| **WebRTC Outbound OPUS Encoding** | OPUS encode + packetization | 15 ms | 321 ms |
| **PSTN Carrier Gateway Transit** | Carrier network transit | 45 ms | **366 ms** |
