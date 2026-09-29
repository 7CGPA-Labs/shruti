# Product Requirements Document (PRD)

---

### 1. Document Control & Executive Summary

* **Project Codename:** S.H.R.U.T.I. (Speech-native Hardware Runtime for Ubiquitous Telephony Intelligence)
* **Target Package:** `org.seven_cgpalabs.shruti`
* **Target Platforms:** Android 14.0+ (API Level 34 & 35)
* **Target Hardware:** Devices equipped with dedicated NPUs (Snapdragon 8 Gen 2/3/4, Google Tensor G3/G4, MediaTek Dimensity 9200/9300/9400)
* **Document Version:** 3.0.0 (Passive Semantic Vector Ingress & Expressive Narrator Architecture)
* **Status:** Approved Production Specification

#### 1.1 Executive Summary & Core Paradigm Shift
Traditional call recording and logging applications save conversations as linear, unindexed audio containers (`.wav`, `.mp3`, `.aac`), resulting in excessive storage consumption, privacy vulnerabilities, and friction when reviewing information.

S.H.R.U.T.I. reimagines conversational logging by replacing raw waveform persistence with **quantized multi-modal mathematical vectors**. Operating strictly as a **passive listener** (the AI never answers or speaks live to callers during calls), the app captures in-call and ambient speech streams, converting them in real time into continuous frames of speaker identity embeddings, acoustic prosodic vectors, and semantic text tokens. 

Audio frames are completely scrubbed from volatile memory immediately after vector extraction. Post-conversation review is delivered through an **Expressive On-Device Narrator** that reconstructs the dialogue as a dramatic, emotionally nuanced debrief with identity resolution, natural pauses, and vocal inflection, executed entirely on-device via NPU/GPU acceleration.

---

### 2. Problem Statement & The "Right Way" Approach

| Challenge / Naive Approach | Critical Platform Failure Mode | The "Right Way" Implementation (S.H.R.U.T.I.) |
| :--- | :--- | :--- |
| **Pure Acoustic Vector Storage** | Acoustic vectors capture vocal timbre and pitch, but cannot reconstruct textual meaning without storing raw audio. | **Composite Vector Frames:** Store a compound frame: Quantized ASR semantic tokens + 192-d ECAPA-TDNN speaker embeddings + 64-d prosodic vectors + pause deltas. Zero audio saved. |
| **Cellular Downlink Call Recording** | Android 12+ and carrier basebands silence incoming downlink audio on standard `AudioRecord` APIs without root/OEM signing. | **Dual-Engine Ingress:** Operates as a native VoIP communication app (WebRTC/SIP) for primary call capture, with fallback to `VOICE_COMMUNICATION` mode under `ROLE_DIALER` where carrier hardware permits. |
| **Continuous 24/7 Wake-Word** | Running continuous mic processing in background exhausts battery in 4–6 hours, keeps Android's privacy indicator active, and gets killed by OS Doze. | **Deliberate Ambient Triggering:** Low-power sensor hardware interrupts (double-tap back gesture $>14\text{ m/s}^2$, Quick Settings Tile, lock-screen widget) activate a 15-second primed wake-word window. |
| **Continuous Unbatched NPU Execution** | Pushing 20 ms frames continuously to the NPU keeps power rails energized, causing thermal throttling and battery drain. | **Batch-and-Burst ("Race-to-Sleep"):** 1.0-second ring buffers on CPU efficiency cores; burst-processed on NPU in $\le 12\text{ ms}$; NPU powers down between frames ($\le 2\%$ duty cycle). |
| **Native Android TTS for Drama** | Android system TTS voices are robotic and designed for utility (turn-by-turn navigation), failing at dramatic narrative pauses. | **SSML Prosody Orchestration + Local Neural Fallback:** Prompt-tuned on-device SLM generates rich SSML for native TTS (`Voice.QUALITY_VERY_HIGH`), with support for an embedded local INT8 neural TTS engine (Kokoro/Piper). |
| **Live Regex Name Parsing** | Hardcoded regex during live calls yields false positives on slang and idioms. | **Two-Tier Identity Resolution:** ContactsContract phone lookup anchors primary contacts; on-device SLM resolves secondary speaker names and vocatives post-call. |
| **Live Conversational Call Screener** | Interrupting or speaking over callers introduces latency and conversational failure modes. | **Passive Listener Only:** The AI only listens, vectorizes, and tracks speakers; it never speaks to or converses with the caller during live calls. |

---

### 3. User Personas & Core Use Cases

#### Persona A: The Power Caller / Executive
* **Profile:** Spends 3–5 hours daily on high-stakes calls with vendors, colleagues, and external partners.
* **Pain Point:** Cannot re-listen to lengthy 45-minute audio recordings; reluctant to use cloud recorders that store sensitive business discussions.
* **Journey:** User participates in a 45-minute multi-party call. S.H.R.U.T.I. runs silently in the background, extracting composite vectors across 4 distinct speakers with zero audio saved to disk. At call conclusion, a persistent notification provides a 90-second expressive narrative debrief: *"Alex presented the roadmap with steady confidence, but David hesitated for three seconds when addressing the migration timeline..."*

#### Persona B: The Field Professional
* **Profile:** Constantly on the move, participating in standups and field meetings.
* **Pain Point:** Needs hands-free ambient meeting logging without draining phone battery or holding the phone.
* **Journey:** User double-taps the back of the phone during a meeting. The device gives a tactical dual-click haptic pulse, primes a 15-second window, and captures the discussion as math vectors. Speaker names mentioned in the room are bound to speaker vectors, and structured action items are indexed into local vector memory.

---

### 4. Functional Requirements (FR)

#### FR-01: Ephemeral Vectorization Pipeline
* **FR-01.1:** The app shall ingest single-channel 16 kHz PCM audio directly into non-heap memory (`DirectByteBuffer`).
* **FR-01.2:** Audio data must be processed within a 1.0-second sliding window and immediately overwritten. No file write operations for `.wav`, `.mp3`, `.m4a`, or `.aac` shall ever occur.
* **FR-01.3:** Output storage format shall be a flat vector binary (`.vecstream` via Google FlatBuffers), containing the unified tuple:
  $$v_t = [e_{\text{speaker}} (192\text{-d}), z_{\text{prosody}} (64\text{-d}), w_{\text{semantic}} (\text{16-bit tokens}), \Delta_{\text{pause}} (\text{16-bit int})]$$

#### FR-02: Heterogeneous Streaming Diarization
* **FR-02.1:** Maintain continuous speaker diarization across sessions exceeding 60 minutes with $O(1)$ memory growth.
* **FR-02.2:** Utilize Online Cosine Centroid Tracking with a dynamic similarity threshold ($\tau=0.72$) and exponential moving average ($\alpha=0.85$) to update speaker clusters.
* **FR-02.3:** Limit active cluster tracking to a maximum of 8 simultaneous speakers per session to cap vector state memory below 2 MB.

#### FR-03: Two-Tier Identity Resolution
* **FR-03.1 (Deterministic OS Anchor):** Query `ContactsContract.PhoneLookup` using the remote URI handle from `Call.Details` to resolve known 1-on-1 contact identities prior to call connection.
* **FR-03.2 (Conversational NER & Vocative Binding):** For multi-speaker conferences and unknown numbers, pass the transcribed semantic tokens to a post-call on-device Small Language Model (SLM) pass to bind self-introductions (*"Hey, this is Sarah"*) and vocative addresses (*"Thanks, Mark"*) to their corresponding speaker cluster IDs.

#### FR-04: Expressive Debriefing Engine
* **FR-04.1:** An on-device SLM (Gemma 2 2B / Qwen3 INT4) shall translate the `.vecstream` into an SSML narrator script with custom prosody, pitch, rate, and break intervals.
* **FR-04.2:** Playback shall interface with `android.speech.tts.TextToSpeech`, dynamically querying and selecting installed high-quality, offline system voice packs (`Voice.QUALITY_VERY_HIGH`), with fallback to local INT8 neural TTS (Kokoro/Piper).
* **FR-04.3:** The debrief shall be launchable via:
  1. In-app call log view.
  2. Interactive Android App Widget (`AppWidgetProvider`).
  3. Android Quick Settings Tile (`TileService`).

#### FR-05: Ambient Triggering Infrastructure
* **FR-05.1:** Register a low-power `SensorEventListener` to identify physical double-tap gestures via accelerometer variance spikes ($>14.0\text{ m/s}^2$ within a 600 ms window).
* **FR-05.2:** When primed via gesture or Quick Settings, activate a 15-second keyword spotting (KWS) window for *"Hey Shruti"* using an INT8-quantized micro-model.
* **FR-05.3:** Provide immediate tactile confirmation of recording state transitions using Android Vibrator (`EFFECT_HEAVY_CLICK` on start, `EFFECT_DOUBLE_CLICK` on stop).

#### FR-06: Passive In-Call Audio Ingress
* **FR-06.1:** System shall operate strictly as a passive listener during active phone calls. The AI never synthesizes speech or transmits audio to the remote caller.
* **FR-06.2:** Support bi-directional digital capture via built-in WebRTC VoIP engine and native telecom `InCallService` under `ROLE_DIALER` (`MediaRecorder.AudioSource.VOICE_COMMUNICATION`).

---

### 5. Non-Functional Requirements (NFR) & Performance Budgets

#### NFR-1: Tri-Tier Compute Budgets
* **NPU Mode (Primary):** $\le 12\text{ ms}$ compute burst per 1000 ms audio chunk (Peak energy efficiency: ~3.5 TOPS/Watt).
* **GPU OpenCL Mode (Secondary):** $\le 28\text{ ms}$ compute burst per 1000 ms audio chunk (Targeting Adreno 7xx / Mali-G7xx; ~1.8 TOPS/Watt). Persistent binary kernel caching allocated up to 8 MB in `codeCacheDir/litert_opencl`.
* **CPU Mode (Fallback):** $\le 85\text{ ms}$ compute on 2x LITTLE efficiency cores via XNNPACK (`THREAD_PRIORITY_BACKGROUND`).

#### NFR-2: Storage & Memory Budgets
* **Storage Footprint:** Complete vector stream must consume $\le 180\text{ KB}$ per minute of active conversation ($\ge 85\%$ reduction compared to 16 kHz 16-bit PCM).
* **Model Footprint:** Dual-head audio vectorizer INT8 model file $\le 500\text{ KB}$ (fitting 100% inside on-die NPU SRAM/TCM cache).

#### NFR-3: Battery Consumption & Thermal Limits
* **Standby Window:** Background sensor and gesture monitoring must consume $<1.5\%$ battery per 8-hour standby window.
* **Active Processing:** Continuous vector recording must consume $<4.5\%$ battery per hour.
* **Thermal Throttling:** Dynamic extension of chunking window from 1.0s to 2.5s upon receiving `THERMAL_STATUS_MODERATE`.

#### NFR-4: Privacy & Platform Compliance
* **Zero Waveform Leakage:** Audio data resides strictly in volatile non-heap RAM (`DirectByteBuffer`) and is scrubbed via `memset_s` immediately post-inference. Zero audio files saved to flash.
* **Air-Gapped Sandbox:** Core ML vectorization and SLM narrator modules strictly exclude `android.permission.INTERNET`.
* **Google Play Policy:** Background foreground service declared under `foregroundServiceType="microphone"` accompanied by an active, un-dismissible user notification.
