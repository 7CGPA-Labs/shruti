# S.H.R.U.T.I. Backend & System Schema Specification

---

## 1. Document Control & Scope

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14.0+ (API 34 & 35)
* **S2S SLM Engine:** `Llama-3.2-1B-Audio-Instruct` (Quantized via INT4 AWQ)
* **Storage Engine:** SQLCipher v4.5.4 (AES-256-GCM encrypted at rest)
* **IPC Transport:** Android AIDL / Binder IPC
* **Native Memory:** C++20 Lockless SPSC Circular Ring Buffer
* **Compliance:** DoT Indian Telecom Interconnect, Google Play `ROLE_DIALER` & AI Policy Compliant
* **Status:** Complete Production Specification

---

## 2. Heterogeneous ML Execution & Mandatory AI Disclosure

| Subsystem Component | Model Architecture | Parameters | Quantization | Execution Provider (EP) | Memory Footprint | Latency Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 ONNX | CPU (XNNPACK / ARM NEON) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Neural Codec Encoder** | Mimi / WavTokenizer | 75 M | INT8 ONNX | Qualcomm QNN HTP / NNAPI | ~82 MB | < 12 ms / frame |
| **Speech-to-Speech SLM** | `Llama-3.2-1B-Audio-Instruct` | 1.1 B | INT4 AWQ | Qualcomm QNN HTP NPU | ~620 MB | < 25 ms / token |
| **Neural Codec Decoder** | Mimi / WavTokenizer | 75 M | INT8 ONNX | Qualcomm QNN HTP / NNAPI | ~82 MB | < 15 ms / frame |
| **Latent Vector Encoder** | CLAP / WavLM Head | 45 M | INT8 ONNX | QNN HTP / CPU Fallback | ~48 MB | < 45 ms / utterance |

*Mandatory AI Disclosure System Prompt Contract:*
`"I am an automated voice assistant powered by Llama-3.2-1B-Audio screening this call for [User Name]. Please state the reason for your call."`

---

## 3. AIDL Interface Contracts

```idl
// Package: org.seven_cgpalabs.shruti.ipc
package org.seven_cgpalabs.shruti.ipc;

interface IShrutiAudioPipeline {
    boolean initializePipeline(in String modelAssetPath);
    oneway void pushInboundFrame(in byte[] pcmFrameData, int sampleCount);
    byte[] pollOutboundFrame(int requestedSamples);
    oneway void triggerBargeIn();
    oneway void terminateSessionAndScrubMemory();
}
```

---

## 4. SQLCipher Cryptographic Storage Schema

```sql
CREATE TABLE IF NOT EXISTS call_sessions (
    session_uuid TEXT PRIMARY KEY NOT NULL,
    caller_hash TEXT NOT NULL,           -- SHA-256(Salt + E.164 Phone Number)
    trai_category TEXT NOT NULL,          -- 'PROMOTIONAL_140', 'TRANSACTIONAL_160', 'UNKNOWN_SOLICITATION'
    timestamp_epoch INTEGER NOT NULL,     -- Unix epoch timestamp in milliseconds
    call_duration_seconds INTEGER NOT NULL,
    debrief_vector BLOB NOT NULL         -- 512 x Float32 (2048 bytes) continuous unit vector
);

CREATE INDEX IF NOT EXISTS idx_sessions_timestamp ON call_sessions(timestamp_epoch DESC);
CREATE INDEX IF NOT EXISTS idx_sessions_caller ON call_sessions(caller_hash);
```
