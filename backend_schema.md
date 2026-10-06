# S.H.R.U.T.I. Backend & System Schema Specification

---

## 1. Document Control & Scope

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14.0+ (API 34 & 35) (Compatible down to Android 10 / API 29)
* **ML Runtime:** **`llama.cpp` + GGML (Vulkan GPU + ARM NEON CPU Fallback)**
* **Model Packaging:** Unified GGUF Format (`Q4_K_M` and `Q8_0`)
* **S2S / Debrief Engine:** `Qwen3-Omni-3B` (Pruned to ~1.45B params, quantized via Q4_K_M GGUF)
* **Storage Engine:** Zero-Copy FlatBuffers (`.vecstream`) + SQLCipher v4.5.4 (AES-256-GCM encrypted at rest)
* **IPC Transport:** Android AIDL / Binder IPC
* **Native Memory:** C++20 Lockless SPSC Circular Ring Buffer
* **Compliance:** DoT Indian Telecom Interconnect, Google Play `ROLE_DIALER` & AI Policy Compliant
* **Status:** Complete Production Specification

---

## 2. llama.cpp & Vulkan (GGUF) Heterogeneous Execution & Debrief Contract

| Subsystem Component | Model Architecture | Parameters | Quantization | Execution Provider (EP) | Memory Footprint | Latency Budget |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Voice Activity Detector** | Silero VAD v5 | 1.8 M | INT8 | CPU (ARM NEON) | ~2.5 MB | < 2.5 ms / 32ms chunk |
| **Tier 1 Dual-Head Vectorizer** | Dual-Head Conv1D/GRU | 420 K | Q8_0 GGUF | Vulkan GPU (`ggml-vulkan`) | ~420 KB | < 1.6 ms / 500ms chunk |
| **Tier 1 Semantic Audio Encoder**| Streaming Whisper Tiny | 38 M | Q8_0 GGUF | Vulkan GPU (`ggml-vulkan`) | ~38 MB | < 14 ms / chunk |
| **Post-Session SLM Narrator** | Pruned `Qwen3-Omni-3B` | 1.45 B | Q4_K_M GGUF | `llama.cpp` Vulkan GPU (`-ngl 99`)| ~725 MB | < 22 ms / token |

*Post-Session Expressive Narrative Debrief System Prompt Contract:*
```
System Prompt: "You are S.H.R.U.T.I., an expressive post-session narrator. Analyze the provided multi-speaker vector trajectory and narrate a concise, engaging spoken debrief of the conversation. Attribute statements accurately to Speaker 1 and Speaker 2. Use SSML prosody tags to convey urgency or assurance. Keep the debrief under 30 seconds."
```

---

## 3. AIDL Interface Contracts

### 3.1 Passive Audio Pipeline AIDL
```idl
// Package: org.seven_cgpalabs.shruti.ipc
package org.seven_cgpalabs.shruti.ipc;

interface IShrutiAudioPipeline {
    boolean initializePipeline(in String ggufModelPath);
    oneway void pushInboundFrame(in byte[] pcmFrameData, int sampleCount);
    oneway void finalizeSessionAndSerialize(in String sessionUuid);
    oneway void terminateSessionAndScrubMemory();
}
```

### 3.2 Spoken Debrief Generator AIDL
```idl
// Package: org.seven_cgpalabs.shruti.ipc
package org.seven_cgpalabs.shruti.ipc;

interface IShrutiDebriefEngine {
    byte[] synthesizeSpokenDebrief(in String sessionUuid);
    oneway void stopDebriefPlayback();
}
```

---

## 4. FlatBuffers Binary Serialization Schema (`shruti_vector_stream.fbs`)

```fbs
namespace org.seven_cgpalabs.shruti.serialization;

table VectorFrame {
    timestamp_ms: ulong;
    speaker_id: ubyte;
    confidence: float;
    speaker_embedding: [float];   // 192-d
    prosody_features: [float];    // 64-d
    semantic_embedding: [float];  // 128-d
    pause_delta_ms: ushort;
}

table ShrutiVectorStream {
    version: uint;
    session_uuid: string;
    start_epoch_ms: ulong;
    total_frames: uint;
    frames: [VectorFrame];
}

root_type ShrutiVectorStream;
```

---

## 5. SQLCipher Cryptographic Storage Schema

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

### Session Vector Trajectory DDL (Long Multi-Person Conversations)

```sql
-- SQLCipher Encrypted Table for Multi-Vector Trajectory Matrices
CREATE TABLE IF NOT EXISTS session_trajectory (
    session_id TEXT PRIMARY KEY,
    trajectory_blob BLOB NOT NULL,       -- Encrypted K x 512 Float32 Matrix (AES-256-GCM)
    num_vectors INTEGER NOT NULL,        -- K (Number of 2-minute topic blocks)
    duration_seconds INTEGER NOT NULL,   -- Total call duration in seconds
    speaker_count INTEGER NOT NULL,      -- Diarized unique speaker count
    created_at INTEGER NOT NULL          -- Unix Epoch Timestamp (ms)
);
```
