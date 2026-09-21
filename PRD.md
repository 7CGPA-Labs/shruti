# Product Requirements Document (PRD)

---

### 1. Document Control & Executive Summary

* **Project Codename:** Aegis-Voice / S.H.R.U.T.I.
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14+ (API Level 34 & 35)
* **Hardware Tier:** Tier-1 & Tier-2 NPU-equipped SoCs (Snapdragon 8 Gen 2/3/4, MediaTek Dimensity 9200/9300/9400)
* **Speech-to-Speech Engine:** `Qwen3-Omni-3B` (Quantized via INT4 AWQ)
* **Document Version:** 2.3.0 (Qwen3-Omni-3B Model Specification)
* **Status:** Approved Production Specification

#### 1.1 Executive Summary
Aegis-Voice (S.H.R.U.T.I.) is an on-device, audio-native call screening and conversational intelligence platform for Android. Operating entirely on mobile silicon using an end-to-end Speech-to-Speech (S2S) architecture via ONNX Runtime powered by **`Qwen3-Omni-3B` (INT4 AWQ)**, it bypasses legacy cloud STT $\rightarrow$ LLM $\rightarrow$ TTS pipelines.

The platform operates under a strict **Zero-Text, Zero-Audio Persistence** paradigm: no raw audio recordings and no textual transcripts are ever saved to disk. S.H.R.U.T.I. features an intuitive **3-Action Incoming Call UI** (`[Decline]`, `[Answer]`, and `[Screen with S.H.R.U.T.I. AI]`), enabling users to delegate unknown calls to the on-device AI assistant powered by `Qwen3-Omni-3B` with a single tap.

---

### 2. Market Positioning & Competitive Moats

| Capability / Metric | Truecaller Assistant | Google Pixel Call Screen | Carrier CNAP (TRAI) | Aegis-Voice / S.H.R.U.T.I. |
| :--- | :--- | :--- | :--- | :--- |
| **Compute Architecture** | Cloud-based SIP forwarding | Hybrid On-Device STT/LLM | Network Switch (SS7/SIP) | 100% On-Device NPU (ONNX) |
| **Operating Pipeline** | STT $\rightarrow$ Cloud LLM $\rightarrow$ TTS | Cascading STT $\rightarrow$ SLM $\rightarrow$ TTS | Static KYC Lookup | Native Audio S2S (`Qwen3-Omni-3B`) |
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
* **Journey:** Inbound call from unknown number $\rightarrow$ 3-Action Caller Screen surfaces $\rightarrow$ User taps `[Screen with S.H.R.U.T.I. AI]` $\rightarrow$ `Qwen3-Omni-3B` model announces mandatory disclosure, screens caller purpose, surfaces Gemini-style overlay, and synthesizes a spoken debrief upon completion.

---

### 4. Functional Requirements (FR)

#### FR-1: Telephony Interception, 3-Action UI & `ROLE_DIALER` Compliance
* **FR-1.1:** System shall support a **VoIP / SIP Gateway Bridge** via LiveKit WebRTC SDK, handling duplex 16 kHz PCM audio streaming over DoT-compliant carrier SIP DIDs.
* **FR-1.2 3-Action Incoming Call UI:** When an incoming call arrives, the caller interface (`ShrutiInCallActivity.kt`) shall present three explicit primary user action controls: `[Decline]`, `[Answer]`, and `[Screen with S.H.R.U.T.I. AI]`.
* **FR-1.3 Full Dialer Requirements:** Interactive dialpad, call history logs, contact picker, and **Instant Emergency Call Pass-Through** (`112` / `911` / `100` / `101` / `102` route directly to PSTN without AI screening).

#### FR-2: Pure Audio-Native Speech-to-Speech Screening & AI Transparency
* **FR-2.1:** System must completely bypass intermediate text token generation during active screening dialogue.
* **FR-2.2:** Input audio digitized into discrete acoustic codec tokens (Mimi / WavTokenizer at 50 Hz frame rates).
* **FR-2.3 Speech-to-Speech SLM Model:** Autoregressive **`Qwen3-Omni-3B`** (quantized via INT4 AWQ) executing on Qualcomm QNN HTP NPU / MediaTek NeuroPilot NNAPI generating direct acoustic codec response tokens.
* **FR-2.4 Dynamic Barge-In:** Silero VAD detects caller interruption within 50 ms and flushes downstream synthesis queues ($\le 40\text{ ms}$).
* **FR-2.5 Mandatory AI Synthetic Media Disclosure:** `Qwen3-Omni-3B` model's first spoken utterance to the caller MUST begin with:
  `"I am an automated voice assistant screening this call for [User Name]. Please state the reason for your call."`

---

### 5. Non-Functional Requirements (NFR) & Compliance

#### NFR-1: Turnaround Latency Budgets
* **NFR-1.1 End-to-End Latency:** Duration between caller speech end and initial AI response frame playback from `Qwen3-Omni-3B` must not exceed **380 ms** on Snapdragon 8 Gen 2+.
* **NFR-1.2 Memory Footprint:** The `Qwen3-Omni-3B` INT4 AWQ model weight allocation must consume $\le 620\text{ MB}$ of system RAM.



---

### 6. First-Time Setup & Double-Confirmation (Voice Onboarding)

Because S.H.R.U.T.I. is designed as a zero-text, voice-first system, onboarding is conducted as an interactive spoken calibration session. This accomplishes two things simultaneously: it collects the necessary grounding facts (address, landmarks, delivery rules) and extracts the acoustic baseline for the user's voice so the assistant recognizes the owner during debriefs.

```
[ User Launches App ] ──► "Namaste! Let's set up S.H.R.U.T.I. Who should I say I am assisting?"
                                         │
                                         ▼
                            [ Step 1: Identity & Name ]
                                         │
                                         ▼
                 [ Step 2: Address Specifics (Tower, Flat, Floor) ]
                                         │
                                         ▼
                 [ Step 3: Navigation Landmarks & Gate Protocol ]
                                         │
                                         ▼
                 [ Step 4: Handover Rules (Guard vs. Doorstep) ]
                                         │
                                         ▼
               [ Step 5: Double-Confirmation Readback & Affirmation ]
                                         │
                  User says: "Yes, that's correct" / "Haan, theek hai"
                                         │
                                         ▼
            Compile into Encrypted System Prefix (E_sys)
```

#### 6.1 The Conversational Setup Script

1. **Owner Identification:**
   * *Assistant:* "Hello! I am ready to set up your call assistant. First, what name should I use when answering calls for you?"
   * *User:* "Chandrashekar, or just Gagan."

2. **Detailed Location & Tower/Door:**
   * *Assistant:* "Got it, Gagan. What is your exact apartment or house location, including building name, tower, and flat number?"
   * *User:* "Sobha Dream Acres, Tower 14, Flat 804 on the 8th floor."

3. **Wayfinding Landmarks & Gate Policy:**
   * *Assistant:* "If a delivery driver is entering from the main gate, what landmarks or directions should they follow to find your tower?"
   * *User:* "Enter through Gate 2 near the clubhouse. Take the second right past the swimming pool. Tower 14 is the yellow building on the left."

4. **Package Handover & Gate Rules:**
   * *Assistant:* "If you are unavailable, should they leave packages at the main gate security, outside your flat door, or call you directly?"
   * *User:* "Leave prepaid parcels with the lobby security guard. For cash on delivery or OTP packages, tell them to ring my doorbell."

5. **The Double-Confirmation Turn (The Guardrail):**
   * *Assistant:* "Let me confirm your details: I am assisting Gagan at Sobha Dream Acres, Tower 14, Flat 804. Directions: Enter Gate 2 near clubhouse, take the second right past the pool to the yellow building. Prepaid deliveries go to lobby security, OTP packages come to the door. Did I get everything right?"
   * *User:* "Yes, that is correct."
   * *Assistant:* "Setup complete. S.H.R.U.T.I. is now active and protecting your calls."

#### 6.2 Storage Architecture for Setup Data

Under the **Zero-Persistence / Ciphered Vector** model:
* **System Prompt Tensor:** The confirmed facts are mapped through the model's text encoder once to produce a static **System Conditioning Embedding Tensor** ($\mathbf{E}_{	ext{sys}} \in \mathbb{R}^{L 	imes 2048}$).
* **Hardware Encryption:** $\mathbf{E}_{	ext{sys}}$ is encrypted via AES-256-GCM using the Android KeyStore master key and saved as `profile_vector.blob` in SQLCipher.
* **Deterministic Rules (Fallbacks):** Exact numerical tokens (Flat `804`, Tower `14`, Gate `2`) are stored in an encrypted key-value table so that deterministic values are never subject to neural hallucinations during active calls.

---

### 7. Live Delivery Guidance: How the LLM Navigates the Driver

When a delivery partner (Swiggy, Zomato, Amazon, Blue Dart, Blinkit) calls, they are often riding a two-wheeler, dealing with heavy traffic or wind noise, and speaking rapid, code-mixed Hinglish/regional slang (*"Bhaiyya kahan aana hai?"*, *"Gate pe security entry nahi de raha"*).

The model handles navigation through an **anchored wayfinding state machine** inside the S2S loop.

```
[ Inbound Call Ringing ]
          │
          ▼
[ Load Profile Prefix E_sys ]  ──► (Tower 14, Flat 804, Gate 2, Clubhouse, Pool)
          │
          ▼
Assistant: "Hello, this is Gagan's assistant. Are you calling regarding a delivery?"
          │
          ▼
Driver: "Haan bhaiyya, main society gate pe hoon, location nahi samajh aa raha."
          │
          ▼
[ State 1: Gate Localization ]
Assistant: "Aap kaunse gate pe khade ho? Gate 1 ya Gate 2?"
          │
          ▼
Driver: "Gate 1 pe hoon."
          │
          ▼
[ State 2: Turn-by-Turn Wayfinding ]
Assistant: "Theek hai. Gate 1 se seedha aaiye, clubhouse cross karke right lijiye.
            Aage swimming pool aayega, uske paas Tower 14 yellow building hai."
          │
          ▼
Driver: "Lift se kaunse floor aana hai?"
          │
          ▼
[ State 3: Final Mile Delivery ]
Assistant: "8th floor, Flat 804. Agar prepaid packet hai toh lobby guard ke paas
            bhi drop kar sakte ho."
          │
          ▼
Driver: "Theek hai bhaiyya, guard ko de raha hoon."
          │
          ▼
[ Call Terminates ] ──► Extract 512-d intent vector ──► Encrypt to SQLCipher ──► Wipe RAM
```

#### 7.1 Key Engineering Pillars for Navigation Handling

1. **Atomic, Single-Instruction Pacing:** Delivery agents cannot process long paragraphs over noisy cellular audio. The model's fine-tuned screening persona is constrained to deliver **one or two landmark instructions per turn**:
   * *Ineffective:* "Enter Gate 2, take second right past the pool, then left at the clubhouse, find Tower 14, take lift to 8th floor Flat 804."
   * *S.H.R.U.T.I. Pattern:* "Gate 2 se enter karke pool ke paas second right lijiye. Wahan Tower 14 milega." (Waits for driver acknowledgement before giving flat/floor info).

2. **Native Code-Switching & Dialect Grounding:** The model does not force the driver into formal speech. If the driver asks in Kannada (*"Sir, address sigtha illa, elli barbeku?"*), the Whisper encoder extracts the semantic meaning, and the Qwen backbone paired with regional prosody tokens guides them in Kannada:
   * *"Gate 2 inda olage banni, swimming pool pakka Tower 14 ide, Flat 804."*

3. **Low-Latency Interruption (Barge-In) During Directions:** Drivers constantly interrupt mid-sentence when spotting a landmark or talking to security:
   * *Assistant:* "Aap seedha aakar round-about se right—"
   * *Driver (Interrupts):* "Haan haan, clubhouse dikh gaya mujhe!"
   * *Execution:* Silero VAD flags driver speech in $<32	ext{ ms}$, the native C++ ring buffer drops the remaining direction audio via `memset_s`, the KV-cache truncates the unplayed tokens, and the assistant instantly responds to the update: *"Haan, clubhouse ke bagal wala building hi Tower 14 hai."*

4. **The "Security Guard Intercom" Sub-Routine:** If the driver states that security is refusing entry:
   * *Driver:* "Security gate pass mang raha hai / register mein entry chahiye."
   * *Assistant:* "Security ko boliye Flat 804, Tower 14 mein Gagan ke yahan delivery hai. Entry approve karwayenge."

5. **Escalation & Call Bridging (Safety Net):** The assistant does not trap callers in an endless loop. If an edge case occurs, the model breaks out and alerts the owner:
   * **Escalation Triggers:**
     * Driver asks for an OTP (Cash on Delivery / high-value parcel).
     * Driver cannot find the tower after 3 turns.
     * Driver explicitly demands: *"User se baat karao / call transfer karo."*
   * **Action:** The assistant states: *"Main Gagan ko direct ring connect kar raha hoon, ek second hold kijiye."* The app triggers an urgent high-priority heads-up notification on the phone, breaking through DND to bridge the call to the handset speaker.
