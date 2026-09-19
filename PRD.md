# Product Requirements Document (PRD)

---

### 1. Document Control & Executive Summary

* **Project Codename:** Aegis-Voice / S.H.R.U.T.I.
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14+ (API Level 34 & 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU-equipped SoCs (Snapdragon 8 Gen 2/3/4, MediaTek Dimensity 9200/9300/9400)
* **Speech-to-Speech Engine:** `Llama-3.2-1B-Audio-Instruct` (Quantized via INT4 AWQ)
* **Document Version:** 2.3.0 (Llama-3.2-1B-Audio-Instruct Model Specification)
* **Status:** Approved Production Specification

#### 1.1 Executive Summary
Aegis-Voice (S.H.R.U.T.I.) is an on-device, audio-native call screening and conversational intelligence platform for Android. Operating entirely on mobile silicon using an end-to-end Speech-to-Speech (S2S) architecture via ONNX Runtime powered by **`Llama-3.2-1B-Audio-Instruct` (INT4 AWQ)**, it bypasses legacy cloud STT $\rightarrow$ LLM $\rightarrow$ TTS pipelines.

The platform operates under a strict **Zero-Text, Zero-Audio Persistence** paradigm: no raw audio recordings and no textual transcripts are ever saved to disk. S.H.R.U.T.I. features an intuitive **3-Action Incoming Call UI** (`[Decline]`, `[Answer]`, and `[Screen with S.H.R.U.T.I. AI]`), enabling users to delegate unknown calls to the on-device AI assistant powered by `Llama-3.2-1B-Audio-Instruct` with a single tap.

---

### 2. Market Positioning & Competitive Moats

| Capability / Metric | Truecaller Assistant | Google Pixel Call Screen | Carrier CNAP (TRAI) | Aegis-Voice / S.H.R.U.T.I. |
| :--- | :--- | :--- | :--- | :--- |
| **Compute Architecture** | Cloud-based SIP forwarding | Hybrid On-Device STT/LLM | Network Switch (SS7/SIP) | 100% On-Device NPU (ONNX) |
| **Operating Pipeline** | STT $\rightarrow$ Cloud LLM $\rightarrow$ TTS | Cascading STT $\rightarrow$ SLM $\rightarrow$ TTS | Static KYC Lookup | Native Audio S2S (`Llama-3.2-1B-Audio-Instruct`) |
| **End-to-End Latency** | 1,200 ms – 2,500 ms | 600 ms – 900 ms | 0 ms (Call Setup only) | **250 ms – 380 ms** |
| **Incoming Call UI** | Cloud Screening Popup | Standard 2-button / 1-chip | Native Ringing UI | **3-Action Control (Decline, Answer, Screen AI)** |
| **Turn-by-Turn Privacy** | Audio streamed to cloud | Transcripts logged locally | Name exposed to caller | **Zero-Audio & Zero-Text Disk Logs** |
| **Storage Primitive** | Cloud database records | Plaintext local SQLite | Carrier databases | **Non-Invertible Semantic Vectors** |
| **Play Store SMS Policy** | N/A | Proprietary system app | Network signaling | **Human-in-the-Loop 1909 Intent** |

---

### 3. Target User Personas & Use Cases

#### Persona A: The High-Volume Professional
* **Profile:** Receives 30–50 calls daily from couriers, vendors, recruitment agents, and unknown business contacts.
* **Pain Point:** Interruptions during meetings; reluctance to pick up unknown numbers or use privacy-invasive cloud assistants.
* **Journey:** Inbound call from unknown number $\rightarrow$ 3-Action Caller Screen surfaces $\rightarrow$ User taps `[Screen with S.H.R.U.T.I. AI]` $\rightarrow$ `Llama-3.2-1B-Audio-Instruct` model announces mandatory disclosure, screens caller purpose, surfaces Gemini-style overlay, and synthesizes a spoken debrief upon completion.

---

### 4. Functional Requirements (FR)

#### FR-1: Telephony Interception, 3-Action UI & `ROLE_DIALER` Compliance
* **FR-1.1:** System shall support a **VoIP / SIP Gateway Bridge** via LiveKit WebRTC SDK, handling duplex 16 kHz PCM audio streaming over DoT-compliant carrier SIP DIDs.
* **FR-1.2 3-Action Incoming Call UI:** When an incoming call arrives, the caller interface (`ShrutiInCallActivity.kt`) shall present three explicit primary user action controls: `[Decline]`, `[Answer]`, and `[Screen with S.H.R.U.T.I. AI]`.
* **FR-1.3 Full Dialer Requirements:** Interactive dialpad, call history logs, contact picker, and **Instant Emergency Call Pass-Through** (`112` / `911` / `100` / `101` / `102` route directly to PSTN without AI screening).

#### FR-2: Pure Audio-Native Speech-to-Speech Screening & AI Transparency
* **FR-2.1:** System must completely bypass intermediate text token generation during active screening dialogue.
* **FR-2.2:** Input audio digitized into discrete acoustic codec tokens (Mimi / WavTokenizer at 50 Hz frame rates).
* **FR-2.3 Speech-to-Speech SLM Model:** Autoregressive **`Llama-3.2-1B-Audio-Instruct`** (quantized via INT4 AWQ) executing on Qualcomm QNN HTP NPU / MediaTek NeuroPilot NNAPI generating direct acoustic codec response tokens.
* **FR-2.4 Dynamic Barge-In:** Silero VAD detects caller interruption within 50 ms and flushes downstream synthesis queues ($\le 40\text{ ms}$).
* **FR-2.5 Mandatory AI Synthetic Media Disclosure:** `Llama-3.2-1B-Audio-Instruct` model's first spoken utterance to the caller MUST begin with:
  `"I am an automated voice assistant screening this call for [User Name]. Please state the reason for your call."`

---

### 5. Non-Functional Requirements (NFR) & Compliance

#### NFR-1: Turnaround Latency Budgets
* **NFR-1.1 End-to-End Latency:** Duration between caller speech end and initial AI response frame playback from `Llama-3.2-1B-Audio-Instruct` must not exceed **380 ms** on Snapdragon 8 Gen 2+.
* **NFR-1.2 Memory Footprint:** The `Llama-3.2-1B-Audio-Instruct` INT4 AWQ model weight allocation must consume $\le 620\text{ MB}$ of system RAM.
