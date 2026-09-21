# System Architecture Specification

---

### 1. Document Control & Architecture Overview

* **Project Codename:** S.H.R.U.T.I. (Aegis-Voice)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU SoCs (Snapdragon 8 Gen 2/3/4, MediaTek Dimensity 9200/9300/9400)
* **S2S SLM Model:** `Qwen3-Omni-3B` (INT4 AWQ)
* **Document Version:** 2.3.0 (Qwen3-Omni-3B Model Specification)
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
|   |   | - Qwen3-Omni-3B S2S SLM (INT4 AWQ, Mandatory AI Disclosure)|   |   |
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
* **`Qwen3-Omni-3B` (INT4 AWQ):** Autoregressive S2S SLM execution engine running on QNN HTP NPU / NNAPI.
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
| **Qwen3-Omni-3B Prefill & 1st Token** | INT4 AWQ execution on QNN HTP NPU | 75 ms | 289 ms |
| **Neural Codec Decoding (QNN HTP)** | Mimi INT8 Decoder | 15 ms | 304 ms |
| **Ring Buffer Dequeue & Uplink** | Atomic pop to LiveKit sink | 2 ms | 306 ms |
| **WebRTC Outbound OPUS Encoding** | OPUS encode + packetization | 15 ms | 321 ms |
| **PSTN Carrier Gateway Transit** | Carrier network transit | 45 ms | **366 ms** |


---

## 5. Long Multi-Person Conversation & Vector Trajectory Architecture

Running long-duration (10 to 60+ minutes), multi-speaker audio on an on-device mobile NPU introduces three architectural bottlenecks: **speaker separation (diarization)**, **KV-cache memory limits**, and **vector capacity limits (semantic smearing)**. S.H.R.U.T.I. resolves these through a streaming chunk-and-flush pipeline, auxiliary speaker diarization, and a hierarchical vector trajectory matrix.

```
Long Multi-Person Audio (20+ mins)
                │
                ▼
[ Lightweight Diarizer (CAM++ / ONNX) ] ──► Distinguishes Speaker A, B, C... (192-d d-vectors)
                │
                ▼
[ 30-Second Sliding Window Ingestion ]   ──► Flushes KV-Cache every turn via memset_s (Constant ~450MB RAM)
                │
                ▼
[ Qwen3-Omni S2S Backbone (NPU) ]
                │
                ▼
[ Hierarchical Vector Pooling ]          ──► Trajectory Matrix: M_session = [v₁, v₂, ..., v_K]ᵀ ∈ ℝ^(K × 512)
                │
                ▼
[ AES-256-GCM Encryption ──► SQLCipher ]
                │
        (Later: Debrief Turn)
                │
                ▼
User Asks: "What was decided in the meeting?"
                │
Decrypted Vector Trajectory ──► Multi-Vector Soft Prompt ──► Spoken Structured Debrief
```

### The 3 Core Engineering Solutions

#### 1. Speaker Diarization (CAM++ / 3D-Speaker ONNX Engine)
* **Auxiliary Engine:** A tiny $\sim 15\text{ MB}$ ONNX model running on CPU via XNNPACK.
* **Extraction & Distance:** Extracts a 192-d speaker identity d-vector every $1.5\text{ seconds}$.
* **Boundary Injection:** When cosine distance indicates a speaker change, the pipeline inserts a lightweight speaker token ($\\langle\text{SPK}_1\\rangle$, $\\langle\text{SPK}_2\\rangle$) into Qwen3-Omni's input stream without storing raw voice biometrics.

#### 2. Streaming 30-Second Chunk-and-Flush Pattern (Constant RAM Footprint)
* **Memory Bottleneck:** A 30-minute conversation generates over 45,000 acoustic frames; holding an active KV-cache causes instant NPU OOM or triggers Android's Low Memory Killer (LMK).
* **Execution Pattern:** Process incoming audio in **30-second semantic chunks**. Summarize each chunk's hidden states into a single vector $\\mathbf{v}_k \in \mathbb{R}^{512}$.
* **Volatile Cache Flush:** Completely flush the KV-cache via `memset_s` before ingesting the next chunk. RAM footprint remains constant at **$\sim 450\text{ MB}$** regardless of call duration (10 to 60+ minutes).

#### 3. Hierarchical Trajectory Matrix (Anti-Semantic Smearing)
* **Formula:** To avoid numbers, action items, and conflicting opinions canceling out during single-vector mean pooling, store a **Vector Trajectory Matrix**:
  $$\\mathbf{M}_{\text{session}} = \begin{bmatrix} \mathbf{v}_1 \\ \mathbf{v}_2 \\ \vdots \\ \mathbf{v}_K \end{bmatrix} \in \mathbb{R}^{K \times 512}$$
* **Storage Footprint:** $K$ represents 2-minute topic blocks ($K \approx 10$ vectors for a 20-min call $\approx 20.48\text{ KB}$). Encrypted with AES-256-GCM and stored in SQLCipher.

#### 4. Spoken Structured Debrief Synthesis
* **Decryption:** Decrypts $\\mathbf{M}_{\\text{session}}$ inside volatile RAM using AndroidKeyStore master key.
* **Soft Prompt Injection:** Projects $[\\mathbf{v}_1, \dots, \mathbf{v}_K]$ through a lightweight cross-attention layer and prepends them as a soft prompt sequence to Qwen3-Omni.
* **Spoken Output:** Synthesizes structured spoken summary via SNAC codec decoder (*"Discussion lasted 24 minutes with three participants..."*).
* **Memory Scrub:** Wipes decrypted matrix $\\mathbf{M}_{\\text{session}}$ immediately from volatile memory via `memset_s`.

---

### Short Telephony Triage vs. Long Multi-Person Conversation

| Dimension | Short Telephony Screening | Long Multi-Person Conversation |
| :--- | :--- | :--- |
| **Duration** | 30 – 90 seconds | 10 – 60+ minutes |
| **Speaker Setup** | Single remote caller | Multiple speakers in a room / shared call |
| **Diarization Required** | No (Channel is isolated) | **Yes** (Requires CAM++ / speaker boundary tokens) |
| **NPU Memory Strategy** | Single static KV-cache ($\\le 2048$ tokens) | **Sliding window + 30s KV-cache flush (`memset_s`)** |
| **Storage Representation** | Single vector $\\mathbf{v} \in \mathbb{R}^{512}$ (2 KB) | **Trajectory matrix $\\mathbf{M} \in \mathbb{R}^{K \times 512}$ (~20–60 KB)** |
| **Debrief Granularity** | Single-sentence recap (5–8 sec) | Structured thematic debrief (15–30 sec) |
