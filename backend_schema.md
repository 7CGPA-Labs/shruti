# S.H.R.U.T.I. Backend & System Schema Specification

---

## 1. Document Control & Scope

* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14.0+ (API 34 & 35)
* **Storage Engine:** SQLCipher v4.5.4 (AES-256-GCM encrypted at rest)
* **IPC Transport:** Android AIDL / Binder IPC
* **Native Memory:** C++20 Lockless SPSC Circular Ring Buffer
* **Compliance:** DoT Indian Telecom Interconnect, Google Play `ROLE_DIALER` & AI Policy Compliant
* **Status:** Complete Production Specification

---

## 2. Process Architecture & Sandboxing Model

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                 ANDROID SYSTEM PROCESSES                                │
│                                                                                         │
│  ┌─────────────────────────────────────────┐   AIDL Binder IPC   ┌───────────────────┐  │
│  │ Process 1: :telephony_process           │ ◄─────────────────► │ Process 2:        │  │
│  │ Package: org.seven_cgpalabs.shruti      │ (Raw PCM 16kHz &    │ :ml_core_process  │  │
│  │ Permissions: INTERNET, RECORD_AUDIO,    │ Control Signals)    │ Package:          │  │
│  │ READ_CONTACTS, TELECOM_CALL             │                     │ org.seven_        │  │
│  │ Components:                             │                     │ cgpalabs.shruti   │  │
│  │ - ShrutiCallScreeningService            │                     │ Permissions:      │  │
│  │ - ShrutiInCallActivity (3-Action UI)    │                     │ NONE (No INTERNET)│  │
│  │ - ShrutiDialerActivity (ROLE_DIALER)    │                     │ Components:       │  │
│  │ - LiveKitAudioBridge                    │                     │ - ONNX Engine     │  │
│  │ - Trai1909Reporter (ACTION_SENDTO)      │                     │ - SPSC RingBuffer │  │
│  │ - ShrutiFloatingOverlay UI              │                     │ - SQLCipher DB    │  │
│  └─────────────────────────────────────────┘                     │ - SQLCipher DB    │  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Telephony 3-Action Caller State Schema

Located in `org.seven_cgpalabs.shruti.telephony`:

```kotlin
enum class IncomingCallAction {
    DECLINE,                 // Terminates call immediately
    ANSWER,                  // Answers call directly to user earpiece/speaker
    DELEGATE_AI_SCREENER     // Accepts call in AI bridge mode & launches screening overlay
}

data class IncomingCallSession(
    val sessionUuid: String,
    val callerNumber: String,
    val callerName: String?,
    val traiCategory: String,
    val isEmergency: Boolean
)
```

---

## 4. AIDL Interface Contracts

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

## 5. Native C++ Lockless SPSC Audio Ring Buffer Schema

```cpp
namespace shruti::audio {

struct alignas(64) SpscRingBuffer {
    static constexpr size_t BUFFER_SIZE = 65536; // 64 KB
    static constexpr size_t BUFFER_MASK = BUFFER_SIZE - 1;

    alignas(64) std::atomic<size_t> write_index{0};
    alignas(64) std::atomic<size_t> read_index{0};

    int16_t buffer[BUFFER_SIZE / sizeof(int16_t)];

    void wipe_memory() noexcept {
        volatile int16_t* ptr = buffer;
        size_t count = BUFFER_SIZE / sizeof(int16_t);
        while (count--) {
            *ptr++ = 0;
        }
        #if defined(__STDC_LIB_EXT1__)
        memset_s(buffer, BUFFER_SIZE, 0, BUFFER_SIZE);
        #else
        __asm__ __volatile__("" : : "r"(buffer) : "memory");
        #endif
    }
};

} // namespace shruti::audio
```

---

## 6. SQLCipher Cryptographic Storage Schema

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
