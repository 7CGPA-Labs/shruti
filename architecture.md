# System Architecture Specification

---

### 1. Document Control & Architecture Overview

* **Project Codename:** S.H.R.U.T.I. (Aegis-Voice)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target OS:** Android 14.0 (API 34), Android 15.0 (API 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU SoCs (Snapdragon 8 Gen 2/3/4, MediaTek Dimensity 9200/9300/9400)
* **Document Version:** 2.2.0 (3-Action Incoming Call UI Specification)
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
|   |   | - Native Audio-to-Audio SLM (1B S2S SLM, Enforces Mandatory AI Disclosure)|   |   |
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

### 3. Sequence Flow: User Taps "Screen with S.H.R.U.T.I. AI"

```
Caller (PSTN)      InCallService UI         ShrutiBridge          Native RingBuffer        NPU S2S Engine         Gemini Overlay UI
     │                    │                      │                      │                        │                        │
     │── Incoming Call ──>│                      │                      │                        │                        │
     │                    │── Displays 3-Action  │                      │                        │                        │
     │                    │   [Decline][Answer]  │                      │                        │                        │
     │                    │   [Screen AI]        │                      │                        │                        │
     │                    │                      │                      │                        │                        │
     │                    │── User Taps ────────>│                      │                        │                        │
     │                    │   "Screen AI"        │── Accept Call ──────>│                        │                        │
     │                    │                      │                      │                        │── Launch Overlay ─────>│
     │<── Connected ──────│                      │                      │                        │   Acoustic Orbit       │
     │                    │                      │                      │                        │                        │
     │                    │                      │                      │                        │── Turn 1 Disclosure ──>│
     │                    │<── Play Disclosure ─────────────────────────│                        │   ("I am an automated  │
     │<── Audio Disclosure│                      │                      │                        │    assistant...")      │
     │                    │                      │                      │                        │                        │
     │── Caller Reply ───>│── WebRTC RTP Frames ───────────────────────>│                        │                        │
     │   ("Is this Mr. X? │                      │                      │── 32ms Chunks ────────>│                        │
     │    Delivery here") │                      │                      │                        │                        │
     │                    │                      │                      │                        │── Silero VAD (CPU)     │
     │                    │                      │                      │                        │   Speech Detected      │
     │                    │                      │                      │                        │                        │
     │                    │                      │                      │                        │── Codec Encode (NPU)   │
     │                    │                      │                      │                        │── S2S SLM (NPU)        │
     │                    │                      │                      │                        │── Codec Decode (NPU)   │
     │                    │                      │                      │<── Synthesized PCM ────│                        │
     │                    │                      │                      │    ("Yes, drop at gate")                        │
     │                    │<── Playback Frames ─────────────────────────│                        │                        │
     │<── Audio Output ───│                      │                      │                        │                        │
```
