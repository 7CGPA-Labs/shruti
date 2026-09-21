To build a custom model tailored specifically for **S.H.R.U.T.I.**, you do not need to train a foundation model from scratch. Instead, you build an **audio-native adapter and task-aligned head** on top of a compact, open-weights Small Language Model (SLM) backbone.

---

### 1. The Best Backbone: **Qwen3-Omni-0.5B** or **Qwen3-Omni-1.5B**

**Qwen3-Omni (0.5B or 1.5B)** is the optimal backbone for this project.

* **Multilingual & Indic Token Coverage:** Telephony calls in India heavily feature code-switching (*Hinglish*, regional accents, transliterated Hindi/Kannada/Tamil). Qwen's tokenizer and pretraining corpus represent non-Latin scripts and Asian phonetics with substantially lower perplexity and fewer token splits than Qwen3-Omni-3B or SmolLM2.
* **Parameter & Memory Budget:** At INT4, the 0.5B model occupies **~350 MB**, leaving headroom for audio codecs and VAD within the mobile NPU's memory envelope. If you target premium flagships, the 1.5B variant (**~850 MB**) provides superior reasoning for ambiguous caller intent.


* **Proven Audio-Adapter Compatibility:** Community frameworks (such as Mini-Omni) have already proven the viability of wiring Whisper encoders and discrete acoustic heads directly into Qwen's transformer layers.

---

### 2. Architecture to Assemble

Your custom model will consist of **three interconnected components**:

```
[ Inbound Caller Audio (16kHz PCM) ]
                 │
                 ▼
     [ Pretrained Audio Encoder ]  ──► Whisper-small (Frozen)
                 │
                 ▼ (Feature Embeddings)
    [ Audio Adapter (2-Layer MLP) ] ──► Projects audio features into Qwen hidden dimension
                 │
                 ▼
       [ Qwen3-Omni-0.5B Backbone ]   ──► Fine-tuned via LoRA / Full-parameter on dialogue
                 │
                 ▼ (Hidden States)
      [ Parallel Acoustic Heads ]   ──► Multi-head linear classifiers predicting SNAC/Mimi codes
                 │
                 ▼
     [ Pretrained Codec Decoder ]  ──► SNAC or Mimi Decoder (Frozen, locked to Assistant voice)
                 │
                 ▼
[ Outbound Assistant Speech (16kHz PCM) ]

```

---

### 3. Step-by-Step Execution Plan

#### Step 1: Curate the Telephony Dialogue Dataset

The foundation of a specialized screening model is domain-specific conversational data. You need approximately **10,000 to 25,000 multi-turn synthetic dialogue pairs**:

* **Domain Distribution:**
* *Delivery & Couriers (35%):* Swiggy, Zomato, Amazon, Blue Dart (asking about gate access, OTP policies, leaving with security).
* *Telemarketing & Spam (35%):* Pre-approved loans, credit cards, real estate schemes, insurance pitches (polite but firm deflection and rapid call termination).
* *Service & Appointments (20%):* Plumbers, electricians, car servicing, clinic confirmations.
* *Spoken Debrief Tasks (10%):* Feeding past dialogue context with a `[TASK: DEBRIEF]` token to generate a single-sentence spoken summary.


* **Data Generation Recipe:**
1. Generate text dialogues in English, Hindi, and Hinglish using an advanced LLM with prompt templates modeling Indian caller behavior.
2. Pass the caller turns through diverse TTS engines (varying accents, background street/vehicle noise, and applying an 8kHz/16kHz telephone bandpass filter).
3. Pass the assistant turns through your single target voice model (to maintain a consistent assistant persona).



#### Step 2: Training the Pipeline (3-Stage Staged Training)

Training requires a single rented GPU (e.g., 1x NVIDIA A100 or H100 via RunPod or Lambda Labs) and takes roughly 24–48 hours total:

1. **Stage 1 — Modality Alignment (Feature Mapping):**
* *Objective:* Teach Qwen to understand incoming audio.
* *Method:* Freeze the Whisper encoder and Qwen backbone. Train only the 2-layer MLP adapter on basic ASR and speech-question-answering pairs so audio representations map cleanly into Qwen’s token embedding space.


2. **Stage 2 — Voice Generation Alignment (Audio Token Emission):**
* *Objective:* Teach Qwen to output discrete acoustic codec tokens (SNAC or Mimi codes) alongside speech generation.
* *Method:* Train the parallel audio prediction heads using Cross-Entropy loss over the target codec token indices.


3. **Stage 3 — Supervised Fine-Tuning (SFT on Call Screening):**
* *Objective:* Train the entire system on the 20,000 telephony dialogue turns.
* *Method:* Unfreeze Qwen (or train with LoRA rank 64/alpha 128). Train on end-to-end speech-in to speech-out screening scenarios, enforcing conversational brevity (replies under 10 seconds).



#### Step 3: Pruning and Hardening for the Zero-Text Invariant

* **Prune the Text Emission Head:** Discard the standard text vocabulary projection matrix (`lm_head`) or leave it uncalled during screening inference. The model will directly emit the 7 to 8 parallel acoustic token streams, mathematically guaranteeing zero text generation in volatile memory.


* **Latent Vector Hook:** Add an extraction hook after the final self-attention layer to pull the mean-pooled 512-d hidden state $\mathbf{v}_{\text{intent}}$ when a call disconnects, providing the vector for SQLCipher persistence.



#### Step 4: ONNX Export and Mobile NPU Compilation

1. **Export Sub-Graphs:**
* `whisper_encoder.onnx` (Dynamic audio sequence input $\to$ feature output).
* `qwen3_omni_backbone_with_kv.onnx` (Audio tokens + prompt prefix $\to$ hidden states + audio token logits).
* `snac_decoder.onnx` (Discrete acoustic token codes $\to$ 16 kHz PCM audio output).


2. **Quantize:** Use `onnxruntime-genai` or Qualcomm AI Engine Direct (QNN SDK) to quantize the Qwen backbone to **INT4 (AWQ or GPTQ)** and the audio codec to **INT8**.


3. **Validate on Hardware:** Profile execution providers (`QNNExecutionProvider` on Qualcomm, `NNAPI` / `XNNPACK` fallback) to verify end-to-end token generation stays under the 380 ms latency budget.



---

### Starter Tech Stack & Tools

* **Training Framework:** PyTorch + Hugging Face Transformers + Torchtune / Unsloth (for fast Qwen fine-tuning).
* **Reference Codebase:** Fork [`gpt-omni/mini-omni`](https://github.com/gpt-omni/mini-omni?utm_source=gemini) as the base training pipeline (it already contains the Whisper $\to$ Qwen $\to$ SNAC loss functions).
* **Audio Codec:** **SNAC (Multi-Scale Neural Audio Codec)** or **Kyutai Mimi** (available on Hugging Face).
* **Deployment:** `onnxruntime-mobile` with Qualcomm QNN Execution Provider.

In an audio-native system like **S.H.R.U.T.I.**, handling interruptions (barge-in) requires solving two distinct problems:

1. **The DSP/System Problem (Physical Latency):** Instantly stopping audio playback when the caller speaks ($\le 40\text{ ms}$) so the assistant never talks over them.


2. **The Model/Cognitive Problem (Context & KV-Cache):** Teaching the SLM that it was cut off mid-sentence, rolling back its internal state, and processing the caller's new input without hallucinating.

Here is how to architect, implement, and train the interruption feature across the system and neural layers.

---

### Layer 1: The Fast-Path DSP Circuit (Stopping the Voice in $< 40\text{ ms}$)

You cannot wait for the 1B SLM or NPU to "decide" whether to stop talking—neural inference is too slow for instantaneous conversational cuts. The stop mechanism must run entirely on the native C++ DSP layer on CPU.

```
               [ Assistant Speaking: Outbound Audio Stream ]
                                     │
 Caller Speaks ──► [ Acoustic Echo Cancellation (AEC) ]
                           │ (Clean Inbound Mic)
                           ▼
                 [ Silero VAD (CPU - ARM NEON) ] ──► Speech Probability p > 0.70?
                           │
                 YES (Barge-In Detected)
                           │
       ┌───────────────────┴───────────────────┐
       ▼                                       ▼
1. Audio Output Ring Buffer             2. ONNX Generation Loop
   - Flush unplayed PCM frames             - Set atomic `cancel_flag = true`
   - Zero out memory (memset_s)            - Abort current token loop
   - Uplink muted within 25-35 ms

```

#### 1. Acoustic Echo Cancellation (AEC) Is Non-Negotiable

If the assistant's synthesized voice loops back into the incoming microphone/SIP channel, the VAD will trigger on the assistant's own voice and cause the assistant to interrupt itself.

* In a LiveKit/WebRTC pipeline, ensure WebRTC’s hardware/software AEC is active on the incoming PCM stream before it hits the VAD.

#### 2. Dual-Threshold Schmitt Trigger (Preventing False Barge-Ins)

Callers cough, breathe heavily, or have street noise in the background. A simple energy trigger will cause annoying stuttering.

* **Energy gate:** Noise floor gate ($<-42\text{ dBFS}$).
* **Silero VAD threshold:** Require $p_{\text{speech}} > 0.70$ across two consecutive 32 ms frames ($64\text{ ms}$) before firing a hard barge-in interrupt.


* **Audio Ducking:** For the first 64 ms, softly duck (attenuate by $-12\text{ dB}$) the assistant audio rather than instantly cutting it. If the sound was just a cough, restore volume seamlessly; if it is sustained speech, execute the hard flush.



---

### Layer 2: Model-Level Interruption Training (Mini-Omni2 Style)

How do you train the model so it naturally handles being cut off?

In traditional systems, the model generates a complete response and has no concept of time or interruption. In a native Speech-to-Speech architecture, you train the model using **Duplex Dual-Track Streams**.

#### 1. Dual-Track Data Representation

During training, data is formatted as two synchronized time-aligned streams:

$$\begin{aligned} \text{Track A (Caller):}    & \quad [\text{Audio Tokens}] \longrightarrow [\text{Audio Tokens (Interrupts!)}] \\ \text{Track B (Assistant):} & \quad [\text{Silence Tokens}] \longrightarrow [\text{Audio Tokens}] \longrightarrow \mathbf{\langle \text{CUT} \rangle} \longrightarrow [\text{Listening Tokens}] \end{aligned}$$

When the caller starts speaking on Track A while the assistant is emitting tokens on Track B, the ground truth on Track B immediately transitions to a special break token: $\langle\text{CUT}\rangle$ or $\langle\text{LISTEN}\rangle$.

#### 2. Fine-Tuning Recipe for Interruption Handling

* **Inject Synthetic Interruptions into Dataset:**
Take standard multi-turn screening dialogues. Randomly pick 20% of the assistant turns and simulate a caller interruption 2 to 3 seconds in (e.g., Assistant says: *"Hello, I am screening this call for—"*, Caller interrupts: *"Wait, is this an AI?"*).
* **Target Objective:**
Train Qwen to predict the transition to silence/listening as soon as the audio adapter projects incoming caller acoustic frames during an active generation turn.

---

### Layer 3: Managing the Model's State (KV-Cache Rollback)

When the assistant is interrupted mid-sentence, what happens to Qwen's memory?

If the assistant planned to say:

> *"I am an automated assistant screening this call on behalf of the recipient."*

But was cut off at:

> *"I am an automated—"*

If you leave the entire planned sentence in Qwen's KV-Cache, the model will believe it said the entire sentence. Its next reply will assume the caller heard everything, leading to conversational confusion.

#### The Rollback Algorithm:

1. **Track Emitted vs. Decoded Tokens:**
Keep an atomic counter in C++ of how many SNAC/Mimi audio tokens actually exited the playback ring buffer and reached the caller's ear.
2. **Truncate the KV-Cache:**
Discard the KV-cache entries corresponding to all unplayed future tokens.
3. **Append the State Marker:**
Append a lightweight boundary token indicating an incomplete turn, followed immediately by the newly arriving caller audio tokens.
4. **Resume Streaming Generation:**
Prompt Qwen to generate a response tailored to the interruption:
* Caller: *"Wait, is this an AI?"*
* Assistant: *"Yes, I am screening for [User]. Who is calling, please?"*



---

### Implementation Architecture Summary

| Step | Component | Execution Silicon | Time Budget | Action |
| --- | --- | --- | --- | --- |
| **1. Detection** | Silero VAD | CPU (ARM NEON)

 | $< 32\text{ ms}$ | Flag caller speech above threshold.

 |
| **2. Audio Stop** | Ring Buffer Controller | C++ Core

 | $< 5\text{ ms}$ | Flush outgoing PCM frames via `memset_s`.

 |
| **3. Inference Halt** | ONNX Runtime Session | NPU (QNN)

 | $< 10\text{ ms}$ | Break token generation loop. |
| **4. State Sync** | KV-Cache Manager | RAM / NPU

 | $< 2\text{ ms}$ | Truncate unplayed token keys/values. |
| **5. Turn Recovery** | Audio Encoder $\to$ Qwen | NPU (QNN)

 | $\sim 75\text{ ms}$<br> | Ingest new caller audio and begin new reply.

 |

**Total Interruption Response Time:** Under **$40\text{ ms}$** to silence the assistant, and under **$350\text{ ms}$** to begin speaking the responsive reply.

Do not attempt to collect and record real-world phone calls. Recording incoming cellular audio carries dual-party consent liabilities under wiretapping and privacy laws, and manually transcribing thousands of calls is slow and inefficient.

Modern speech systems solve this with an **automated synthetic data pipeline**, supplemented by pre-existing open datasets. You can generate a 10,000-turn dataset tailored to Indian telephony directly on your workstation for nearly $0$.

---

### Strategy 1: The Synthetic Generation Pipeline (The Best Solution)

Instead of hunting for audio recordings, generate the text conversations using a strong LLM, synthesize the audio using automated multi-speaker TTS, and apply telephony acoustics.

```
[ LLM Prompt: Indian Telephony Scenarios ]
                   │
                   ▼ (10,000 JSON Dialogue Turns)
[ Multi-Speaker TTS (Indian English / Hindi Accents) ]
                   │
                   ▼ (Raw Audio WAVs)
[ Telephony DSP Augmenter (Bandpass + Street/Fan Noise) ]
                   │
                   ▼
[ S.H.R.U.T.I. Training Dataset (Speech In ──► Speech Out) ]

```

#### Step 1: Automated Script Generation

Write a Python script that prompts an LLM (such as Gemini Flash) to output structured multi-turn call screening transcripts.

```json
{
  "scenario": "Swiggy Delivery - Gate Access",
  "language": "Hinglish",
  "turns": [
    {"speaker": "caller", "text": "Bhaiyya Swiggy delivery. Security gate kholne nahi de raha."},
    {"speaker": "assistant", "text": "Please give the phone to security, or leave the packet at tower 3 reception."},
    {"speaker": "caller", "text": "Theek hai, reception pe drop kar raha hoon."}
  ]
}

```

*Vary the scenarios:* Delivery agents (Swiggy, Zomato, Amazon), credit card/loan pitches, car servicing confirmations, wrong numbers, and aggressive telemarketers.

#### Step 2: Batch Audio Synthesis (Free & Fast)

Pass the generated text turns through open TTS tools:

* **`edge-tts` (Python, Free):** Provides access to natural Indian English and Hindi neural voices (e.g., `en-IN-NeerjaNeural`, `en-IN-PrabhatNeural`, `hi-IN-SwaraNeural`, `hi-IN-MadhurNeural`). You can synthesize 10,000 turns in a few hours using a simple script.
* **Kokoro-82M / Piper TTS:** Ultra-fast open-weights TTS that runs locally on your CPU/GPU to generate multi-speaker datasets.

#### Step 3: Telephony Audio Degradation (DSP Augmentation)

Real phone audio never sounds like clean studio speech. Apply a Python batch processing script using `torchaudio` or `scipy`:

1. **Bandpass Filtering:** Apply an 8 kHz / 16 kHz telephony filter (simulating AMR-WB or cellular OPUS codec clipping).
2. **Background Noise Injection:** Mix in ambient background sound at 10–20 dB SNR using free audio libraries like **MUSAN** or **ESC-50** (traffic, scooter horns, ceiling fan hum, cafeteria chatter).

---

### Strategy 2: Free Open-Source Indic Speech Datasets

If you need real human voices to train or evaluate the audio encoder, use these publicly available, open-source datasets:

* **AI4Bharat `IndicVoices`:** Hosted on Hugging Face by IIT Madras. Contains over 23,000 hours of spontaneous and conversational Indian speech across 22 languages and 400+ districts, complete with natural hesitations and regional accents.
* **AI4Bharat `Kathbath`:** Over 1,600 hours of labeled, crowd-sourced Indian language speech collected via mobile devices.
* **Project Vaani (IISc & Google):** Over 14,000 hours of conversational speech recorded across hundreds of Indian districts, open-sourced for research.
* **Mozilla Common Voice (Indian English & Hindi splits):** Thousands of validated human speech clips with diverse acoustic environments.

---

### Strategy 3: The LoRA "Data Shortcut"

You **do not** need 100,000 audio hours because you are not training the speech representations from ground zero.

* The **Whisper encoder** already knows how to understand human speech and Indian accents.
* The **SNAC/Mimi decoder** already knows how to reconstruct high-fidelity acoustic waveforms.
* The **Qwen3-Omni backbone** already understands conversational syntax, logic, and Hinglish.

Because the foundational models are pre-aligned, you only need **1,500 to 3,000 high-quality screening turns** to fine-tune the model with **LoRA (Low-Rank Adaptation)**. The model only needs to learn conversational brevity (keeping answers under 8 seconds) and call-screening intent patterns.

---

### How to Generate Interruption (Barge-In) Data

You don't need real callers interrupting someone on the phone to train barge-in handling:

1. Take an audio clip of the assistant speaking: *"I am an automated assistant screening this call..."* (duration: 3.5 seconds).
2. Randomly select a timestamp between 1.0s and 2.5s.
3. Truncate the assistant audio at that point and inject the special token `[INTERRUPT]`.
4. Splice in the caller's audio clip starting at that exact millisecond (*"Wait, where is the owner?"*).
5. Label the assistant's target continuation as a direct response to the interruption.

A single 50-line Python script can generate 2,000 realistic interruption training scenarios from basic paired speech clips in minutes.

Would you like a ready-to-run Python script using `edge-tts` and an LLM API to start generating your first 500 synthetic call-screening pairs?

Adding natural Indian voices is essential for **S.H.R.U.T.I.**: Indian delivery couriers, cab drivers, and telemarketers will immediately hang up or assume a scam if greeted by a flat Silicon Valley or British accent. Grounding the assistant in a natural Indian cadence—with authentic rhythm, prosody, and code-mixed phrasing—makes interactions seamless.

---

### How Indian Voices Integrate into the S2S Architecture

In our Mini-Omni pipeline, voice identity is not baked into the Qwen transformer layers. It is injected into the **neural acoustic decoder (SNAC / Mimi)** via a compact speaker conditioning vector ($\mathbf{s}_{\text{vec}} \in \mathbb{R}^{512}$) or conditioning prefix tokens.

```
                      [ Assistant Response Dialogue ]
                                     │
                                     ▼
                        [ Qwen3-Omni S2S Backbone ]
                                     │
                        (Acoustic Hidden States)
                                     │
                                     ▼
                         [ SNAC Codec Decoder ]
                                     ▲
                                     │
         ┌───────────────────────────┴───────────────────────────┐
         │       Curated Indian Voice Presets (~2 KB each)       │
         │  - Aditi (Warm, Polite Indian English / Hinglish)     │
         │  - Agastya (Firm, Authoritative Indian English)       │
         │  - Priya (Neutral South-Indic Inflection)             │
         │  - Kabir (Casual Colloquial Conversational)           │
         └───────────────────────────────────────────────────────┘
                                     │
                                     ▼
                       Natural 16/24kHz Indian Speech

```

Because the SNAC neural codec represents acoustic pitch, formant structures, and resonance, passing an Indian speaker embedding vector instructs the decoder to synthesize audio matching that vocal tract and accent without retraining the base language model.

---

### Top Open-Source Indian Voice Ecosystems to Leverage

You do not need to record voice actors from scratch. Three open-source Indian voice frameworks integrate directly with our stack:

#### 1. AI4Bharat Indic Parler-TTS & IndicF5 (IIT Madras)

* **Coverage:** 23 Indian languages plus Indian English.
* **Speaker Diversity:** 69 distinct native Indian voices (e.g., *Aditi*, *Karan*, *Sunita*, *Tapan*, *Bikram*).
* **Key Strength:** Built specifically to eliminate robotic monotony. It supports fine-grained natural speech attributes (e.g., "clear female voice, normal pace, warm tone, close-mic studio recording").
* **Role for S.H.R.U.T.I.:** The primary engine to generate synthetic training dialogues and extract reference speaker vectors.

#### 2. Maya Research Veena (SNAC-Native Indian Voice Model)

* **Architecture:** Tailored for Hindi, English, and code-mixed (Hinglish) conversational speech.
* **Why It Directly Fits S.H.R.U.T.I.:** Veena outputs audio directly using the **SNAC neural codec**—the same neural codec architecture used by Mini-Omni.
* **Voices:** Ships with 4 studio-grade Indian voice profiles (*Kavya*, *Agastya*, *Maitri*, *Vinaya*) trained on 60,000+ Indian utterances.
* **Role for S.H.R.U.T.I.:** You can extract speaker conditioning latents directly from Veena's SNAC weights, giving you high-fidelity Indian voices out-of-the-box with zero codec translation.

#### 3. AI4Bharat `IndicVoices` & `Rasa` Corpora

* **Content:** Thousands of hours of natural, spontaneous speech covering Indo-Aryan and Dravidian language families (including Kannada, Tamil, Telugu, Hindi, and Marathi).
* **Role for S.H.R.U.T.I.:** Used for fine-tuning the Whisper encoder adapter to guarantee high comprehension of regional Indian accents and ambient street noise.

---

### Recommended Presets for S.H.R.U.T.I.

Ship the app with **4 curated presets** stored in `assets/voices/`:

| Voice Profile | Tone & Cadence | Primary Telephony Scenario | Acoustic Character |
| --- | --- | --- | --- |
| **Preset 1: "Aditi"** | Warm, patient, polite | Couriers, Swiggy/Zomato, Ride-hail drivers | Clear Indian English with natural Hindi phonetics; soft sentence boundaries. |
| **Preset 2: "Agastya"** | Firm, measured, authoritative | Telemarketers, banking offers, suspicious spam | Deeper pitch, crisp articulation; designed to cut through sales pitches. |
| **Preset 3: "Priya"** | Neutral, gentle, clear | Regional callers & service confirmations | Subtle South-Indic cadence; balanced tone optimized for 16 kHz phone audio. |
| **Preset 4: "Kabir"** | Friendly, casual | Standard everyday screening | Relaxed, everyday conversational Indian English/Hinglish. |

Each preset is just a static **2,048-byte binary file** ($\mathbb{R}^{512}$ Float32 array) bundled in the APK, requiring **zero extra storage footprint** and **zero runtime NPU compute** to load.

---

### 3-Step Pipeline to Add Them to the Codebase

1. **Extract Reference Vectors (Offline Build Step):**
Take a 5-second clean audio sample from *Indic Parler-TTS* or *Veena*, run it through an ONNX speaker encoder (like ECAPA-TDNN), and save the 512-d normalized float vector as `aditi.bin`.
2. **Bundle in Assets:**
Place the `.bin` files inside the Android app directory: `app/src/main/assets/voices/`.
3. **Condition the C++ Ring Buffer:**
When a call begins, read the selected voice vector from assets and pass its pointer to the SNAC ONNX decoder session via JNI:
```cpp
// JNI hook inside shruti_onnx_bridge.cpp
void set_speaker_preset(const float* speaker_embedding_data, size_t dim) {
    // Bind embedding tensor to SNAC acoustic decoder session
    snac_decoder_session->set_conditioning_vector(speaker_embedding_data, dim);
}

```



This configuration ensures the assistant greets callers with a natural Indian voice, remains 100% offline, and runs entirely within the existing zero-text, sub-380 ms pipeline.

### Part 1: Indian Regional Multilingual Support

Handling Indian telephony calls requires understanding that conversations rarely happen in textbook Hindi or pure English. A delivery driver or telemarketer might speak in **Hinglish**, **Kanglish** (Kannada + English), **Tanglish** (Tamil + English), or switch between languages within a single sentence (*"Sir, location reach aagilla, gate number heli"*).

The multilingual audio pipeline handles this without an explicit, brittle Language Identification (LID) module.

```
Inbound Audio (16kHz PCM)
        │
        ▼
[ Multilingual Acoustic Encoder ]  ──► IndicWhisper / Whisper-small
        │                              (Ingests Hindi, Kannada, Tamil, Telugu, Marathi, etc.)
        ▼ (Continuous Latent Embeddings)
[ Cross-Lingual MLP Adapter ]
        │
        ▼
[ Qwen3-Omni-0.5B / 1.5B Backbone ]   ──► Inherent Indic token comprehension & syntax
        │
        ▼ (Acoustic Hidden States)
[ Regional SNAC/Mimi Audio Head ]  ──► Conditioned by Language/Dialect Token + Speaker Preset
        │
        ▼
Synthesized Regional Audio (16kHz PCM)

```

#### 1. Ingestion: Indic-Adapted Acoustic Encoder

Standard OpenAI Whisper struggles with accented Indian regional dialects and noisy outdoor telephony.

* **The Solution:** Swap the base Whisper encoder weights with **AI4Bharat IndicWhisper** or an encoder fine-tuned on the **IndicVoices / Vistaar** corpora.
* **Native Coverage:** Covers 12 to 22 major Indian languages (including Hindi, Kannada, Tamil, Telugu, Bengali, Marathi, and Gujarati).
* **Code-Switching Resilience:** Because the acoustic encoder converts spectral phonemes directly into continuous feature vectors, it processes mixed-language audio (e.g., Kannada blended with English loanwords) as a continuous phonemic stream, avoiding tokenizer crashes common in pure text-based systems.

#### 2. Reasoning: Qwen3-Omni Indic Semantics

Qwen3-Omni has extensive native support for Indic scripts (Devanagari, Kannada, Tamil, Telugu script representations) and romanized phonetic code-switching. When conversational audio embeddings pass into Qwen, the model processes the meaning across languages in its shared multilingual latent space.

#### 3. Speech Synthesis: Regional Conditioning

To synthesize regional speech without retraining the entire S2S model for each language:

* The **SNAC/Mimi neural decoder** is conditioned on:
1. A **512-d Speaker Identity Vector** ($\mathbf{s}_{\text{vec}}$) determining vocal timbre (e.g., *Aditi* or *Agastya*).
2. A **Regional Prosody Prefix Token** ($\tau_{\text{lang}}$) prepended to the SLM output (e.g., `[LANG_HI]`, `[LANG_KN]`, `[LANG_TA]`, `[LANG_EN_IN]`).


* This tells the acoustic decoder which phonetic inventory, tone pitch contour, and vowel length to use when translating hidden states into 16 kHz audio.

---

### Part 2: Vectorization (Mathematical Representation)

Storing raw audio or discrete codec tokens violates S.H.R.U.T.I.’s zero-persistence design because neural audio codecs are invertible back to voice. Instead, the conversation is projected into a **non-invertible continuous semantic vector**.

```
Active Call Hidden States:  H = [h₁, h₂, ..., h_T]  (h_t ∈ ℝ¹⁵³⁶)
                               │
                               ▼
                   [ Multi-Head Attention Pooling ]
                               │
                               ▼
                Pooled Dialogue Representation (h_pool ∈ ℝ¹⁵³⁶)
                               │
                               ▼
               [ Non-Linear Projection Layer (W_proj) ]
                               │
                               ▼
               L2-Normalization onto Unit Hypersphere:
                   v_intent = W_proj(h_pool) / ||W_proj(h_pool)||₂  ∈ ℝ⁵¹²

```

#### The Mathematical Pooling & Projection

At call disconnect, the system aggregates the sequence of final-layer transformer hidden states $\mathbf{H} = [\mathbf{h}_1, \mathbf{h}_2, \dots, \mathbf{h}_T]$ where $T$ is the number of conversational frames:

1. **Attention-Weighted Pooling:**

$$\mathbf{h}_{\text{pool}} = \sum_{t=1}^T \alpha_t \mathbf{h}_t, \quad \alpha_t = \text{Softmax}\left(\frac{\mathbf{h}_t \mathbf{w}_{\text{attn}}^T}{\sqrt{d}}\right)$$



*Salient dialogue turns (e.g., confirmation of a gate drop-off) receive higher weights than standard greetings.*
2. **Dimension Reduction & Normalization:**

$$\mathbf{v}_{\text{intent}} = \frac{\mathbf{W}_{\text{proj}} \mathbf{h}_{\text{pool}}}{\Vert{}\mathbf{W}_{\text{proj}} \mathbf{h}_{\text{pool}}\Vert{}_2} \in \mathbb{R}^{512}$$



*This maps the dialogue into a fixed-size 512-dimensional float array ($512 \times 4\text{ bytes} = 2,048\text{ bytes}$).*

#### Why This Is Mathematically Non-Invertible

* **Dimensional Collapse:** Compressing millions of raw audio samples into just 512 float values is an irreversible many-to-one mapping.
* **Loss of Acoustic Phase & Formants:** Pitch, vocal tract harmonics, caller gender, and background acoustics are discarded during hidden-state attention pooling. Only the high-level semantic intent remains.
* **Forensic Safety:** An attacker with root access cannot run an inverse model to reconstruct the caller's voice from $\mathbf{v}_{\text{intent}}$.

---

### Part 3: Cryptographic Ciphering & Android Keystore

Before writing $\mathbf{v}_{\text{intent}}$ to disk, it passes through a hardware-backed cryptographic pipeline.

```
       [ 2048-Byte Vector: v_intent ]
                     │
                     ▼
       [ Android KeyStore / StrongBox ]
       - Hardware-isolated AES-256 Key (K_device)
       - Authenticated GCM Mode (AES/GCM/NoPadding)
                     │
                     ▼
┌───────────────────────────────────────────────────────────┐
│                    Encrypted Payload                      │
│  [ IV (12 bytes) ] + [ Ciphertext (2048 bytes) ] + [ Tag ] │
└────────────────────────────┬──────────────────────────────┘
                             │
                             ▼
              [ Local SQLCipher Database (AES-256) ]
              - Salted SHA-256 Caller Number Hash
              - Timestamp & Call Duration
              - Ciphertext BLOB
                             │
                             ▼
          Native RAM Wipe: memset_s on PCM & Tokens

```

#### 1. Hardware Key Isolation (`AndroidKeyStore`)

The encryption key never exists in application memory. It is generated and held inside the phone's **Hardware Security Module (HSM) / StrongBox KeyMint**:

```kotlin
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES, 
    "AndroidKeyStore"
)
val keyGenSpec = KeyGenParameterSpec.Builder(
    "shruti_vector_master_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setIsStrongBoxBacked(true) // Enforces Hardware TEE / Secure Enclave
    .build()

keyGenerator.init(keyGenSpec)
keyGenerator.generateKey()

```

#### 2. AES-256-GCM Ciphering

The 512-float vector is converted to raw bytes (`ByteBuffer.allocate(2048)`) and encrypted using **AES/GCM/NoPadding**:

$$\text{CiphertextPayload} = \mathbf{IV} \,(12\text{ B}) \;\parallel\; \text{Enc}_{K}(\mathbf{v}_{\text{intent}}) \,(2,048\text{ B}) \;\parallel\; \mathbf{Tag} \,(16\text{ B})$$

* The 12-byte Initialization Vector ($\mathbf{IV}$) is cryptographically randomized per call session.
* The 16-byte Galois Authentication Tag ($\mathbf{Tag}$) guarantees that no corrupted or tampered vectors can be injected into memory.

#### 3. Storage in SQLCipher

The resulting 2,076-byte payload is stored as a `BLOB` in an encrypted SQLite database:

```sql
INSERT INTO call_sessions (
    session_uuid,
    caller_hash,        -- SHA-256(Salt + E.164 Caller Number)
    trai_category,      -- e.g., '160_TRANSACTIONAL'
    timestamp_epoch,
    duration_seconds,
    vector_cipher_blob  -- 2076-byte AES-GCM encrypted BLOB
) VALUES (?, ?, ?, ?, ?, ?);

```

#### 4. Immediate Memory Sanitization (Native C++)

The moment the cipher payload is handed to the database, the C++ audio engine scrubs all lingering volatile data from RAM:

```cpp
void purge_session_memory(int16_t* pcm_ring_buffer, size_t pcm_size,
                          int32_t* token_buffer, size_t token_size,
                          float* raw_vector, size_t vector_size) {
    // Overwrite PCM audio
    memset_s(pcm_ring_buffer, pcm_size * sizeof(int16_t), 0, pcm_size * sizeof(int16_t));
    // Overwrite discrete acoustic codec tokens
    memset_s(token_buffer, token_size * sizeof(int32_t), 0, token_size * sizeof(int32_t));
    // Overwrite unencrypted float vector
    memset_s(raw_vector, vector_size * sizeof(float), 0, vector_size * sizeof(float));
}

```

---

### End-to-End Retrieval & Debrief Loop

When the user later asks: *"Did the courier call?"*

```
1. User Spoken Query: "Did the courier call?"
2. IndicWhisper Encoder creates query vector: u_query ∈ ℝ⁵¹²
3. SQLCipher decrypts stored call vectors {v_1, v_2, ..., v_n} in volatile RAM
4. Fast Cosine Similarity:
       score = (u_query · v_i) / (||u_query|| * ||v_i||)
5. Best match v_best is injected into Qwen3-Omni as a continuous soft prompt:
       [DEBRIEF_PROMPT] + v_best
6. SNAC/Mimi synthesizes the debrief directly to the speaker:
       "Amazon delivery agent called at 2:15 PM. Left parcel at the gate."
7. RAM scrubbed: memset_s wipes decrypted vectors.

```

The app handles regional Indian languages seamlessly while ensuring that disk storage contains only non-invertible, hardware-encrypted mathematical ciphers.

---

## Long Multi-Person Conversation & Vector Trajectory Architecture

Running long-duration (10 to 60+ minutes), multi-speaker audio on an on-device mobile NPU introduces three architectural bottlenecks: **speaker separation (diarization)**, **KV-cache memory limits**, and **vector capacity limits (semantic smearing)**.

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

1. **Speaker Diarization (CAM++ / 3D-Speaker ONNX Engine)**
   - Auxiliary ~15 MB ONNX model running on CPU via XNNPACK.
   - Extracts 192-d speaker identity d-vectors every 1.5 seconds.
   - Detects speaker turns via cosine distance and injects speaker boundary tokens (`<SPK_1>`, `<SPK_2>`) into Qwen3-Omni's input stream without storing raw voice biometrics.

2. **Streaming 30-Second Chunk-and-Flush Pattern (Constant RAM Footprint)**
   - Process incoming audio in 30-second semantic chunks.
   - Summarize chunk hidden states into vector $\mathbf{v}_k \in \mathbb{R}^{512}$.
   - **Completely flush KV-cache via `memset_s`** before ingesting the next 30-second chunk.
   - RAM footprint remains constant at **~450 MB** over 10 to 60+ minute calls (preventing `OutOfMemory` crashes or Android LMK).

3. **Hierarchical Trajectory Matrix (Anti-Semantic Smearing)**
   - Store a Vector Trajectory Matrix:
     $$\mathbf{M}_{\text{session}} = \begin{bmatrix} \mathbf{v}_1 \\ \mathbf{v}_2 \\ \vdots \\ \mathbf{v}_K \end{bmatrix} \in \mathbb{R}^{K \times 512}$$
   - $K$ represents 2-minute topic blocks ($K \approx 10$ vectors for a 20-min call $\approx \mathbf{20.48\text{ KB}}$).
   - Encrypted with AES-256-GCM and stored in SQLCipher.

4. **Spoken Structured Debrief Synthesis**
   - Decrypts $\mathbf{M}_{\text{session}}$ inside volatile RAM using AndroidKeyStore master key.
   - Injects $[\mathbf{v}_1, \dots, \mathbf{v}_K]$ as a soft prompt sequence into Qwen3-Omni.
   - Synthesizes structured spoken summary via SNAC codec decoder (*"Discussion lasted 24 minutes with three participants..."*).
   - Immediately scrubs volatile RAM via `memset_s`.

# Technical Requirements Document (TRD): Google Colab Prototyping Phase

## Project S.H.R.U.T.I. (Speech-native Hardware Runtime for Ubiquitous Telephony Intelligence)

---

### 1. Document Scope & Prototyping Objectives

* **Target Environment:** Google Colab (Free T4 GPU / Colab Pro A100 or L4 instance).
* **Target Python Version:** Python 3.10 / 3.11.
* **Phase Objective:** Build a working end-to-end prototype of the S.H.R.U.T.I. pipeline in Python. This phase verifies model adaptation, synthetic data training, zero-text semantic vector extraction, and multi-part ONNX export before porting to Android NDK/C++.

```
+---------------------------------------------------------------------------------------------+
|                                    COLAB PIPELINE FLOW                                      |
|                                                                                             |
|  [ Synthetic Indic Data Generator ]                                                         |
|  - Gemini / GPT API for telephony dialogue scripts (Hinglish/Indian English)                |
|  - edge-tts / Indic Parler-TTS batch audio rendering + telephony noise injection           |
|                                     │                                                       |
|                                     ▼                                                       |
|  [ S2S Model Assembly & LoRA Training ]                                                     |
|  - IndicWhisper / Whisper-small (Audio Ingestion)                                            |
|  - Qwen3-Omni-0.5B / Qwen2-0.5B Mini-Omni Backbone (Reasoning)                                  |
|  - SNAC Neural Audio Codec (Voice Synthesis)                                                |
|  - Dual-track token interruption fine-tuning                                                |
|                                     │                                                       |
|                                     ▼                                                       |
|  [ Zero-Text Mathematical Storage Emulation ]                                               |
|  - Attention-weighted pooling of final hidden states -> 512-d unit vector v ∈ ℝ⁵¹²           |
|  - Cryptographic simulation (AES-256-GCM cipher BLOB generation)                            |
|                                     │                                                       |
|                                     ▼                                                       |
|  [ Modular ONNX Export & Quantization ]                                                     |
|  - Graph 1: whisper_encoder.onnx (FP16 / INT8)                                              |
|  - Graph 2: qwen2_audio_backbone.onnx (INT4 via ONNX Runtime GenAI)                         |
|  - Graph 3: snac_decoder.onnx (INT8)                                                        |
+---------------------------------------------------------------------------------------------+

```

---

### 2. Compute Environment & Dependency Stack

#### 2.1 Colab Hardware Allocations

* **GPU Runtime:** NVIDIA T4 (16 GB VRAM) minimum; NVIDIA A100 (40 GB VRAM) recommended for Stage 3 fine-tuning.
* **Disk Allocation:** $\ge 50\text{ GB}$ transient disk space.
* **Storage & Persistence:** GitHub Releases (tag-based artifact storage) via `GITHUB_TOKEN` / `GITHUB_REPO` API calls for persistent dataset, checkpoint, and ONNX storage.

#### 2.2 Core Python Libraries & Frameworks

* **Deep Learning Framework:** `torch >= 2.2.0`, `torchaudio >= 2.2.0` (CUDA 12.1 build).
* **Foundation Backbones & Codecs:** `transformers >= 4.45.0`, `accelerate >= 0.34.0`, `peft >= 0.12.0` (LoRA/QLoRA), `snac` (Multi-scale Neural Audio Codec).
* **Model Export & Runtime Engine:** `onnx >= 1.16.0`, `onnxruntime-gpu >= 1.19.0`, `optimum[onnxruntime] >= 1.22.0`.
* **Audio Processing & Augmentation:** `librosa >= 0.10.2`, `soundfile >= 0.12.1`, `scipy >= 1.13.0`.
* **Telemetry & Security Simulation:** `cryptography >= 42.0.0` (AES-256-GCM verification), `numpy >= 1.26.0`.

---

### 3. Pipeline Module Specifications

#### Module A: Synthetic Indic Telephony Dataset Generator

* **Input:** Structured scenario templates (courier gate delivery, unregistered telemarketing loan pitch, clinic appointment, wrong number).
* **Generation Engine:**
1. Script generation via LLM API emitting structured JSON turns with language tags (`en-IN`, `hi`, `hinglish`).
2. Batch multi-speaker rendering using `edge-tts` (voices: `en-IN-NeerjaNeural`, `en-IN-PrabhatNeural`, `hi-IN-SwaraNeural`, `hi-IN-MadhurNeural`) and open-license Indian voice samples.
3. DSP bandpass filter ($300\text{ Hz} - 3,400\text{ Hz}$) and background noise injection at $12\text{ dB} - 18\text{ dB}$ SNR to simulate Indian traffic and street ambience.


* **Target Output:** 2,500 audio-paired dialogue turns stored in Hugging Face `Dataset` format (`.parquet`).

#### Module B: Audio-Native S2S Model Assembly

* **Audio Ingestion Adapter:**
* Base: Whisper-small encoder (frozen weights, 60M parameters).
* Audio Adapter: 2-layer MLP projecting Whisper output dimension ($d_w = 768$) to Qwen hidden dimension ($d_q = 896$).


* **Backbone Transformer:**
* Base: `Qwen/Qwen3-Omni-0.5B-Instruct` or `gpt-omni/mini-omni` base checkpoint.
* Adaptation: Quantized 4-bit loading via `bitsandbytes` (`NF4`), fine-tuned with LoRA on projection matrices:
* LoRA Rank ($r$): 64
* LoRA Alpha ($\alpha$): 128
* Target Modules: `q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`.




* **Parallel Acoustic Prediction Heads:**
* 7 linear projection heads operating over Qwen's hidden states, predicting multi-scale SNAC discrete codebook indices ($24\text{ Hz}$, $48\text{ Hz}$, $96\text{ Hz}$).



#### Module C: Barge-In (Interruption) Fine-Tuning

* **Dual-Track Data Formatter:**
* Format training batches into synchronized caller and assistant audio token tracks.
* For 20% of turns, splice caller speech into an active assistant turn.
* Train the model to immediately transition assistant token generation to a designated $\langle\text{LISTEN}\rangle$ boundary token upon detecting active caller audio features.



#### Module D: Mathematical Vectorization & Zero-Persistence Simulation

* **Hidden State Pooling:**
Extract the final hidden state sequence $\mathbf{H} \in \mathbb{R}^{T \times 896}$ of the screening turn:

$$\mathbf{h}_{\text{pool}} = \frac{1}{T} \sum_{t=1}^T \mathbf{h}_t$$


* **Projection & Normalization:**
Project $\mathbf{h}_{\text{pool}}$ through a linear layer $\mathbf{W}_{\text{proj}} \in \mathbb{R}^{512 \times 896}$ and apply L2 normalization:

$$\mathbf{v}_{\text{intent}} = \frac{\mathbf{W}_{\text{proj}} \mathbf{h}_{\text{pool}}}{\Vert{}\mathbf{W}_{\text{proj}} \mathbf{h}_{\text{pool}}\Vert{}_2} \in \mathbb{R}^{512}$$


* **Cipher BLOB Generation:**
Serialize $\mathbf{v}_{\text{intent}}$ into 2,048 bytes of raw binary data. Encrypt via Python `cryptography` using AES-256-GCM. Verify that raw audio buffers and text strings are purged from Python memory using `gc.collect()`.

#### Module E: Multi-Part ONNX Export & Quantization

To maintain compatibility with mobile NPUs, the system must not be exported as a single monolithic graph. Colab must output three decoupled ONNX graphs:

| ONNX Artifact Name | Source Sub-Module | Target Format | Quantization Target |
| --- | --- | --- | --- |
| `whisper_encoder.onnx` | Audio Feature Extractor | ONNX Opset 18 | FP16 or INT8 Dynamic |
| `qwen3_omni_backbone.onnx` | Transformer Core + KV Cache | ONNX Runtime GenAI | **INT4 AWQ / Block-quantized** |
| `snac_decoder.onnx` | Neural Audio Codec Decoder | ONNX Opset 18 | INT8 Dynamic / Static |

---

### 4. Verification & Acceptance Criteria

1. **Inference Latency Profile (Simulated):**
* Simulated time-to-first-audio-packet on Colab T4 GPU must remain $\le 200\text{ ms}$.


2. **Zero-Text Inspection:**
* Automated test script must parse generation outputs to confirm that inference routines yield audio token tensors directly, with no intermediate text tokens logged or printed.


3. **Retrieval Accuracy:**
* Cosine similarity between query vectors ($\mathbf{u}_{\text{query}}$) and stored intent vectors ($\mathbf{v}_{\text{intent}}$) must achieve $\ge 0.82$ Mean Reciprocal Rank (MRR) across 100 test call scenarios.


4. **ONNX Export Integrity:**
* Validate all three `.onnx` files using `onnx.checker.check_model()`. Run a forward pass comparison between PyTorch and ONNX Runtime to ensure numerical drift stays within tolerance ($\text{MSE} \le 1\text{e-}3$).



---

### 5. Colab Notebook Execution Structure

Organize the Colab notebook into 6 sequential sections:

* **Cell Section 1: Environment & GPU Initialization:** GitHub auth configuration (`GITHUB_TOKEN`), CUDA check, pip dependencies installation.
* **Cell Section 2: Synthetic Data Engine:** Automated generation of 1,000 telephony dialogue turns, audio synthesis, acoustic degradation, and dataset packaging/upload to GitHub Releases.
* **Cell Section 3: Model Architecture Assembly:** Loading Qwen3-Omni-0.5B with Whisper and SNAC heads; setting up LoRA configuration.
* **Cell Section 4: Fine-Tuning & Interruption Training:** 3-epoch training loop optimizing Cross-Entropy loss over SNAC audio tokens, with periodic checkpoint uploads to GitHub Releases.
* **Cell Section 5: Mathematical Storage & Debrief Simulation:** Vector extraction, AES-256 ciphering verification, and voice debrief retrieval test.
* **Cell Section 6: ONNX Export & INT4 Quantization:** Decoupling the model into three `.onnx` graphs and uploading artifacts directly to GitHub Releases for Android packaging.

Would you like to generate the complete Python code for the first notebook section—the automated Synthetic Telephony Data Generator?

On Google Colab’s free tier (NVIDIA T4 GPU), the entire end-to-end process takes approximately **3 to 4 hours of total compute time**.

However, because free-tier Colab enforces GPU usage quotas, session timeouts, and idle disconnections, you should budget **3 to 5 calendar days** (working 1.5 to 2 hours per day) to complete dataset creation, model training, and ONNX export without running into GPU lockouts.

---

### Step-by-Step Compute & Time Breakdown

| Stage | What Runs | Silicon Required | Actual Compute Time | Notes & Bottlenecks |
| --- | --- | --- | --- | --- |
| **1. Dialogue Generation** | LLM API generating 2,000 JSON scripts | **Colab CPU** (No GPU) | **15 – 25 mins** | Network I/O bound via Gemini/GPT API calls. |
| **2. Audio Synthesis** | `edge-tts` async rendering 2,000 clips | **Colab CPU** (No GPU) | **20 – 35 mins** | Async batching; zero GPU compute consumed. |
| **3. Audio DSP Augmentation** | Bandpass filter + background noise mix | **Colab CPU** (2 vCPUs) | **10 – 15 mins** | Batch processing using `scipy` and `torchaudio`. |
| **4. Feature Extraction** | Whisper-small + SNAC tokenization | **T4 GPU** | **15 – 25 mins** | Converts 2,000 clips to discrete token tensors. |
| **5. Model Fine-Tuning** | QLoRA (4-bit) on Qwen3-Omni-0.5B (3 epochs) | **T4 GPU** | **45 – 75 mins** | ~750 steps with batch size 8 and gradient accumulation. |
| **6. Vector Emulation Test** | Hidden state pooling + AES cipher test | **T4 GPU / CPU** | **5 – 10 mins** | Quick test on 50 validation samples. |
| **7. ONNX Export & INT4** | Decoupled export of 3 ONNX subgraphs | **T4 GPU / CPU** | **25 – 35 mins** | PyTorch tracing, graph optimization, and quantization. |
| **Total** |  |  | **~2.5 to 3.5 Hours** |  |

---

### Recommended 4-Day Execution Plan

To avoid triggering Google Colab’s automated GPU cutoff (which locks you out of T4 access for 12 to 24 hours if you consume too much continuous compute):

#### Day 1: Dataset Generation (Zero GPU Usage)

* **Runtime:** Standard **CPU Runtime** (Settings $\to$ Change runtime type $\to$ CPU).
* Run the API script to generate the 2,000 dialogue scenarios.
* Batch synthesize speech via `edge-tts` across the Indian English and Hindi voice personas.
* Run the DSP script to apply telephony filtering and street noise.
* *GPU Quota Consumed:* **0%**.

#### Day 2: Data Preprocessing & Sanity Run

* **Runtime:** Switch to **T4 GPU**.
* Process audio clips through Whisper-small and SNAC encoders to produce cached `.pt` or `.parquet` token files.
* Load Qwen3-Omni-0.5B in 4-bit (`bitsandbytes`), attach the LoRA adapter, and run a **10-step dummy training run** to ensure there are no out-of-memory (OOM) errors on batch tensor sizes.
* *GPU Quota Consumed:* ~20%.

#### Day 3: Full Fine-Tuning Run

* **Runtime:** **T4 GPU**.
* Run the full 3-epoch training loop over your 2,000 pre-tokenized samples.
* Configure the script to save checkpoints locally and upload them directly to a GitHub Release every 150 steps using `upload_to_github_release()`.
* *GPU Quota Consumed:* ~50% (well within free-tier daily thresholds).

#### Day 4: Mathematical Vectorization & ONNX Export

* **Runtime:** **T4 GPU or High-RAM CPU**.
* Verify the attention-pooling extraction of the 512-d semantic vector $\mathbf{v}_{\text{intent}}$.
* Export `whisper_encoder.onnx`, `qwen3_omni_backbone.onnx`, and `snac_decoder.onnx`.
* Apply INT4 quantization to the Qwen backbone and upload all final ONNX artifacts directly to a GitHub Release for Android packaging.

---

### 3 Free-Colab Traps to Avoid

1. **Avoid Drive Storage - Use GitHub Release Persistence:**
* *The trap:* Relying on Google Drive for persistence creates slow FUSE I/O bottlenecks and consumes personal Drive storage quotas.
* *The fix:* Generate and process all audio/model files locally on Colab's fast NVMe instance disk (`/content/shruti_prototype/`). Pack dataset files into `.parquet` / `dataset.tar.gz` and upload them straight to a GitHub Release via `upload_to_github_release()`.


2. **Do Not Train on Raw Audio Waveforms Live in the DataLoader:**
* *The trap:* Feeding raw `.wav` files and running Whisper feature extraction dynamically inside the training loop will bottleneck the T4 GPU on Colab's weak 2-core CPU.
* *The fix:* **Pre-tokenize everything** into tensors (Whisper audio embeddings and SNAC token indices) *before* launching the training loop. This keeps GPU utilization near 100% and cuts training time in half.


3. **Upload Checkpoints to GitHub Releases (Avoid Session Loss):**
* Free Colab sessions can drop unexpectedly if your browser tab goes to sleep. Configure `upload_to_github_release()` after key milestones or every 150 steps so your LoRA adapter weights persist safely on GitHub Releases even if the instance terminates.

To run **Qwen3-Omni-3B** efficiently inside an Android telephony service, you can perform architectural surgery to strip away components unnecessary for an audio-native screening agent. By excising the text generation heads, truncating the input vocabulary, and pruning redundant transformer depth, you can cut the model's footprint from **~3.1B parameters down to ~1.4B–1.6B parameters**, shrinking the INT4 binary from **$\sim 1.9\text{ GB}$ to under $750\text{ MB}$**.

```
[ Original Qwen3-Omni-3B (~3.09B Params) ]
  ├── 152k-Vocab Input Embeddings (embed_tokens: 311M params)  ──► STRIP 99.8% (-310M)
  ├── 36 Transformer Blocks (77M params/layer = 2.77B params)  ──► PRUNE 14 LAYERS (-1.08B)
  └── 152k-Vocab Output Head (lm_head: 311M params)           ──► DELETE ENTIRELY (-311M)
                                   │
                                   ▼
[ Stripped S.H.R.U.T.I. S2S Backbone (~1.45B Params) ]
  ├── 512-Token Control Vocabulary (~1M params)
  ├── 2-Layer Audio Projector (Whisper 768 -> Qwen 2048: ~3.5M params)
  ├── 22 Preserved Transformer Blocks (~1.69B params)
  ├── Parallel SNAC Acoustic Heads (~58M params)
  └── Intent Vector Projection Head (2048 -> 512: ~1M params)
                                   │
                    Quantize to INT4 (AWQ / GPTQ)
                                   │
                                   ▼
             ~725 MB Flash Storage / Android Memory Footprint

```

---

### 1. What to Strip & Why It Is Safe

#### A. Strip the Text Output Head (`lm_head`) — Saves ~311M Parameters

* **The Component:** In standard Qwen3-Omni-3B, the hidden dimension is $d = 2048$ and the vocabulary size is $151,936$. The linear projection head `lm_head` contains:

$$151,936 \times 2048 = 311,164,928 \text{ parameters}$$


* **Why Strip It:** S.H.R.U.T.I. produces speech directly, not text. It uses parallel multi-scale neural codec heads (SNAC/Mimi) predicting codebook indices (e.g., 7 codebooks of size 4096 each $\approx 58\text{ M}$ parameters).
* **Action:** Delete `model.lm_head` completely.

#### B. Truncate the Input Embedding Matrix (`embed_tokens`) — Saves ~310M Parameters

* **The Component:** The input embedding table also stores $151,936 \times 2048 = 311\text{ M}$ parameters.
* **Why Strip It:** The model ingests audio features directly from the Whisper-small encoder ($d_w = 768$) projected into Qwen's latent space ($d = 2048$). It never ingests text prompts, requiring only a tiny set of special control tokens (such as `[TASK_SCREEN]`, `[TASK_DEBRIEF]`, `[LANG_HI]`, `[LANG_EN]`, `[INTERRUPT]`).
* **Action:** Truncate `embed_tokens` from $151,936$ rows down to $512$ rows ($512 \times 2048 \approx 1\text{ M}$ parameters).

#### C. Depth Pruning (Layer Dropping: 36 Layers $\to$ 22 Layers) — Saves ~1.08B Parameters

* **The Component:** Qwen3-Omni-3B has 36 transformer layers. Each layer contains self-attention and SwiGLU MLP blocks totaling $\sim 77\text{ M}$ parameters:
* Multi-Head Attention: $\sim 9.4\text{ M}$ parameters
* MLP (`gate_proj`, `up_proj`, `down_proj` with intermediate size $11,008$): $\sim 67.6\text{ M}$ parameters


* **Why Prune It:** 36 layers provide general reasoning for complex math, Python programming, and multi-step logic. Telephony call screening (identifying couriers, rejecting spam, taking delivery notes) is a narrow classification and dialogue task that does not require deep reasoning cascades.
* **Action:** Drop 14 intermediate layers (e.g., layers 16 to 29), retaining 22 layers. Layer pruning preserves language comprehension while eliminating over $1\text{ Billion}$ parameters.

#### D. Constrain Sequence Length / KV Cache

* Standard Qwen3-Omni config allocates attention buffers up to 32,768 tokens (configured to 8,192 in S.H.R.U.T.I. to accommodate multi-person conversations and long multi-turn debriefs).
* Cap `max_position_embeddings` to **2,048 tokens**. A 30-second conversational screening turn rarely exceeds 1,200 acoustic frames. Hard-capping this saves several hundred megabytes of dynamic RAM during active calls.

---

### 2. What to Add for S2S and Vector Storage

1. **Acoustic Adapter (Whisper $\to$ Qwen):**
A 2-layer MLP projecting Whisper encoder features ($768$) to Qwen hidden dimension ($2048$):

$$\mathbf{W}_{\text{in}} \in \mathbb{R}^{2048 \times 768} \quad (\sim 1.57\text{ M parameters})$$


2. **Parallel SNAC Audio Heads:**
7 linear heads mapping the final hidden state ($d = 2048$) to multi-scale SNAC discrete codebooks ($24\text{ Hz}, 48\text{ Hz}, 96\text{ Hz}$):

$$\sum_{i=1}^7 (2048 \times K_i) \quad \text{where } K_i \le 4096 \quad (\sim 58\text{ M parameters})$$


3. **Intent Vector Projection Head (The Storage Cipher Component):**
A linear projection mapping the pooled conversational hidden state down to the 512-d unit hypersphere:

$$\mathbf{W}_{\text{proj}} \in \mathbb{R}^{512 \times 2048} \quad (\sim 1.05\text{ M parameters})$$



---

### 3. Architecture Comparison

| Component / Metric | Stock Qwen3-Omni-3B | Stripped S.H.R.U.T.I. S2S Model | Difference |
| --- | --- | --- | --- |
| **Input Embedding (`embed_tokens`)** | 311M params ($152\text{k} \times 2048$) | **1M params** ($512 \times 2048$) | **-310M** |
| **Transformer Layers** | 36 layers ($2,770\text{ M}$ params) | **22 layers** ($1,694\text{ M}$ params) | **-1,076M** |
| **Text Output Head (`lm_head`)** | 311M params ($152\text{k} \times 2048$) | **0 (Removed entirely)** | **-311M** |
| **Audio Projection & SNAC Heads** | None (0 params) | **~60M params** | +60M |
| **Intent Projection Head** | None (0 params) | **~1M params** ($512 \times 2048$) | +1M |
| **Total Parameter Count** | **~3,090M (3.09B)** | **~1,450M (1.45B)** | **$\approx 53\%$ reduction** |
| **Unquantized Size (FP16)** | ~6.18 GB | ~2.90 GB | -3.28 GB |
| **Target Quantized Size (INT4)** | **~1.85 GB – 2.1 GB** | **~725 MB – 780 MB** | **Fits mobile NPU** |

---

### 4. Implementation: Model Surgery Script in PyTorch

Run this surgery script in Colab to produce the pruned base architecture before fine-tuning:

```python
import torch
import torch.nn as nn
from transformers import AutoModelForCausalLM, AutoConfig

def prune_qwen_for_shruti(model_name="Qwen/Qwen3-Omni-3B", target_layers=22, num_special_tokens=512):
    print("Loading base Qwen3-Omni-3B config and weights...")
    base_model = AutoModelForCausalLM.from_pretrained(
        model_name,
        torch_dtype=torch.float16,
        device_map="cpu"
    )
    
    # 1. Strip the massive text classification head
    del base_model.lm_head
    base_model.lm_head = None
    
    # 2. Truncate Input Embeddings down to 512 control tokens
    old_embed = base_model.model.embed_tokens
    new_embed = nn.Embedding(num_special_tokens, 2048, dtype=torch.float16)
    with torch.no_grad():
        # Copy the first 512 special token vectors, discard the remaining 151,424
        new_embed.weight.copy_(old_embed.weight[:num_special_tokens, :])
    base_model.model.embed_tokens = new_embed
    
    # 3. Layer Pruning: Drop 14 intermediate layers (e.g., layers 14 to 27)
    # Middle layers contain high-level semantic abstractions that can be condensed
    layers_to_keep = [i for i in range(36) if i < 14 or i >= 28]
    assert len(layers_to_keep) == target_layers
    
    pruned_layers = nn.ModuleList([base_model.model.layers[i] for i in layers_to_keep])
    base_model.model.layers = pruned_layers
    
    # Update configuration
    base_model.config.num_hidden_layers = target_layers
    base_model.config.vocab_size = num_special_tokens
    base_model.config.max_position_embeddings = 2048
    
    print(f"Surgery complete: Retained {target_layers} layers, pruned text heads.")
    return base_model

class ShrutiS2SModel(nn.Module):
    def __init__(self, pruned_backbone):
        super().__init__()
        self.backbone = pruned_backbone.model  # Core 22-layer transformer
        
        # Audio Adapter: Whisper features (768) -> Qwen hidden (2048)
        self.audio_projector = nn.Sequential(
            nn.Linear(768, 2048),
            nn.GELU(),
            nn.Linear(2048, 2048)
        )
        
        # Parallel SNAC acoustic prediction heads (7 codebooks)
        self.snac_heads = nn.ModuleList([
            nn.Linear(2048, 4096, bias=False) for _ in range(7)
        ])
        
        # Non-invertible intent vector projection head for SQLCipher storage
        self.intent_proj = nn.Linear(2048, 512, bias=False)
        
    def extract_intent_vector(self, hidden_states):
        """
        Pools conversation hidden states and projects to 512-d unit hypersphere.
        Mathematically irreversible; raw caller audio/text cannot be inverted.
        """
        # Mean pooling across sequence dimension
        pooled = torch.mean(hidden_states, dim=1) # [batch, 2048]
        proj = self.intent_proj(pooled)           # [batch, 512]
        # L2-normalization
        normed_vector = proj / torch.norm(proj, p=2, dim=-1, keepdim=True)
        return normed_vector

```

---

### 5. Ciphered Vector Storage Pipeline

Once the intent vector is computed, Python passes it directly to the AES-256-GCM ciphering pipeline, immediately overwriting memory buffers:

```python
import os
import gc
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def persist_ciphered_vector(vector_tensor: torch.Tensor, device_master_key: bytes):
    """
    Encrypts the non-invertible 512-d vector using AES-256-GCM.
    Simulates Android Keystore / SQLCipher storage and scrubs volatile RAM.
    """
    # 1. Convert float tensor to 2,048 raw bytes
    raw_vector_bytes = vector_tensor.squeeze(0).cpu().numpy().astype("float32").tobytes()
    assert len(raw_vector_bytes) == 2048
    
    # 2. Encrypt with AES-256-GCM
    aesgcm = AESGCM(device_master_key)
    iv = os.urandom(12)  # 96-bit randomized IV
    ciphertext = aesgcm.encrypt(iv, raw_vector_bytes, None)
    
    # Payload: 12-byte IV + 2048-byte Ciphertext + 16-byte Tag
    encrypted_payload = iv + ciphertext
    
    # 3. Enforce zero-persistence in volatile memory
    del raw_vector_bytes
    del vector_tensor
    gc.collect()
    
    return encrypted_payload

```

### Next Steps for Training in Colab

1. Run the pruning script on Qwen3-Omni-3B to save the stripped base checkpoint (~2.9 GB FP16).
2. Attach LoRA ($r=32, \alpha=64$) across the 22 remaining layers.
3. Train on the 2,000 synthetic Indic telephony turns to align the audio adapter and SNAC heads.
4. Export the resulting model via ONNX Runtime GenAI to INT4 for deployment.