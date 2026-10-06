# S.H.R.U.T.I. UI/UX Design Specification

---

## 1. Executive Summary & Design Philosophy

**Target Package:** `org.seven_cgpalabs.shruti`  
**S2S / Debrief Engine:** `Qwen3-Omni-3B` (Q4_K_M GGUF format via `llama.cpp` + Vulkan Engine)  
**Design Vision:** Gemini-inspired modern mobile AI experience built entirely with Jetpack Compose.  
**Core Invariant:** **Zero-Text Transcripts, Passive In-Call Listener (Never Intercepts or Speaks Live), and Post-Call Expressive Spoken Debriefs.**  
S.H.R.U.T.I. utilizes organic visual feedback (Gemini-style dynamic glow borders and harmonic acoustic orbit spheres) to reflect conversational turns, speaker diarization, and system states without ever exposing or persisting written call transcripts.

---

## 2. Component Architecture & UI Flow Overview

```
                                  ┌────────────────────────────────────────┐
                                  │          Incoming PSTN Call            │
                                  └───────────────────┬────────────────────┘
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │       Default Dialer Incoming Call     │
                                  │       [ Decline ]        [ Answer ]    │
                                  └───────────────────┬────────────────────┘
                                                      │
                                           [User Answers Call]
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │   In-Call Passive Vectorizer UI        │
                                  │   (ShrutiInCallActivity.kt)            │
                                  │   * Minimal ambient privacy pulse      │
                                  │   * Zero live AI speech / interception │
                                  │   * Passive Vulkan GPU vector stream   │
                                  └───────────────────┬────────────────────┘
                                                      │
                                           [Call Disconnects]
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │   Post-Call Debrief Notification Card  │
                                  │   (ShrutiDebriefNotification.kt)       │
                                  │   * 1-Tap "Play Voice Debrief"         │
                                  │   * Human-in-the-loop action chips     │
                                  └───────────────────┬────────────────────┘
                                                      │
                                    [Tap Debrief / App Launch]
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │    Full Assistant & Conversation App   │
                                  │    (ShrutiConversationScreen.kt)       │
                                  │   * Hero Acoustic Orbit sphere         │
                                  │   * Spoken Debrief Audio Cards         │
                                  │     (llama.cpp Vulkan Qwen3-Omni)      │
                                  │   * Voice-to-Voice Search Bar          │
                                  │   * ROLE_DIALER Dialpad & Emergency    │
                                  └────────────────────────────────────────┘
```

---

## 3. Screen: Full Assistant & ROLE_DIALER Telephony App

### Component Details
1. **Emergency Pass-Through Button (`112` / `911`):** Direct one-tap emergency call action bypassing all AI pipelines with instant PSTN connection.
2. **Spoken Debrief Audio Cards (`ShrutiDebriefCard.kt`):** Tapping the Play button (`▶`) invokes on-device **`llama.cpp` Vulkan SLM (`Qwen3-Omni-3B`)** to synthesize a 5-to-10 second expressive spoken voice debrief on demand (*"Delivery agent called regarding Amazon package delivery; package left at building reception"*).
3. **Voice-to-Voice Query Bar (`ShrutiVoiceSearchBar.kt`):** User speaks a query (*"What did the doctor recommend yesterday?"*), projected into a vector embedding and matched via cosine similarity against SQLCipher trajectory matrices.

---

## 4. First-Time Setup & Voice Onboarding UI Flow (`ShrutiVoiceOnboardingScreen.kt`)

The onboarding process is a voice-first interactive calibration session requiring zero manual text typing:

1. **Harmonic Acoustic Orbit Visualizer:** Dynamic Jetpack Compose canvas displaying fluid multi-colored glowing orbits reacting to user spoken input and assistant prompts.
2. **Step Progress Chips:** 5 visual progress indicators corresponding to setup milestones:
   - `[1. Identity]` $\rightarrow$ `[2. Location & Tower]` $\rightarrow$ `[3. Gate & Landmarks]` $\rightarrow$ `[4. Handover Rules]` $\rightarrow$ `[5. Confirmation]`
3. **Double-Confirmation Action Sheet:** Displays extracted grounding card summary (Owner Name, Address, Landmarks, Delivery Rules) during Step 5 with voice and single-tap affirmation controls (`[Confirm & Activate]`, `[Re-record Spoken Details]`).
4. **Encrypted System Embedding Generator:** Upon affirmation, compiles facts into static System Embedding Tensor $\mathbf{E}_{\text{sys}}$ encrypted via AES-256-GCM in SQLCipher (`profile_vector.blob`).

---

## 5. Post-Call Delivery & Logistics Narrative Debrief Card (`ShrutiDeliveryDebriefCard.kt`)

When the passive in-call vectorizer identifies a delivery or courier interaction (Swiggy, Zomato, Amazon, Blue Dart, Blinkit), it renders a structured post-call debrief card:

```
+-----------------------------------------------------------------------+
|  DELIVERY CALL DEBRIEF (Swiggy / Amazon)              [14:22]         |
|  Summary: Package Left at Security Gate                               |
|                                                                       |
|  [ Gate 2 Entry ] ──► [ Security Desk ] ──► [ Flat 804 Notification]  |
|                                                     ▲ (Status: Dropped)|
|                                                                       |
|  Key Spoken Detail:                                                   |
|  "Amazon courier arrived at Gate 2 and handed package to guard Ramesh"|
|                                                                       |
|  Actions:                                                             |
|  [ ▶ Play Voice Debrief ]   [ Send Gate Pass SMS ]   [ Callback Driver]|
+-----------------------------------------------------------------------+
```

1. **Logistics Stepper:** Visual tracking of delivery milestones identified from the call (`Gate Entry` $\rightarrow$ `Lobby / Security` $\rightarrow$ `Handover Complete`).
2. **Post-Call Voice Playback:** 1-tap playback of expressive SLM spoken debrief synthesized with speaker attribution and natural prosody via `llama.cpp` Vulkan.
3. **One-Tap Escalation Chips:**
   - `[Play Voice Debrief]`: Streams on-device audio narration of the call context.
   - `[Send Gate Pass SMS]`: Pre-fills gate pass SMS / intent for rapid entry approval.
   - `[Callback Driver]`: Opens dialer with the courier's number for instant callback.
