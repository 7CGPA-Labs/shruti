# S.H.R.U.T.I. UI/UX Design Specification

---

## 1. Executive Summary & Design Philosophy

**Target Package:** `org.seven_cgpalabs.shruti`  
**S2S SLM Engine:** `Qwen3-Omni-3B` (Quantized via INT4 AWQ)  
**Design Vision:** Gemini-inspired modern mobile AI assistant experience built entirely with Jetpack Compose.  
**Core Invariant:** **Zero-Text Transcripts, Mandatory AI Transparency, and Human-in-the-Loop Regulatory Control.**  
S.H.R.U.T.I. utilizes organic visual feedback (Gemini-style dynamic glow and harmonic acoustic orbits) to reflect conversational turns, sentiment, and system states without exposing written call content.

---

## 2. Component Architecture & UI Flow Overview

```
                                  ┌────────────────────────────────────────┐
                                  │          Incoming PSTN Call            │
                                  └───────────────────┬────────────────────┘
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │   3-Action Incoming Call Screen        │
                                  │   (ShrutiInCallActivity.kt)            │
                                  │   [ Decline ] [ Answer ] [ Screen AI ] │
                                  └───────────────────┬────────────────────┘
                                                      │
                                    [User Taps "Screen with S.H.R.U.T.I. AI"]
                                                      │
                                                      ▼
                                  ┌────────────────────────────────────────┐
                                  │   Gemini-Style Voice Activation Overlay│
                                  │   (ShrutiBottomSheetOverlay.kt)        │
                                  │   * Multi-color fluid glow border      │
                                  │   * Dynamic Acoustic Orbit particle wave│
                                  │   * Real-time one-tap action chips     │
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
                                  │     (Powered by Qwen3-Omni-3B)    │
                                  │   * Voice-to-Voice Search Bar          │
                                  │   * ROLE_DIALER Dialpad & Emergency    │
                                  └────────────────────────────────────────┘
```

---

## 3. Screen 2: Full Assistant & ROLE_DIALER Telephony App

### Component Details
1. **Emergency Pass-Through Button (`112` / `911`):** Direct one-tap emergency call action bypassing AI screening.
2. **Spoken Debrief Audio Cards (`ShrutiDebriefCard.kt`):** Tapping Play button (`▶`) invokes on-device **`Qwen3-Omni-3B`** to synthesize a 5-to-10 second spoken voice debrief on demand (*"Delivery agent called regarding Amazon package delivery at gate"*).
3. **Voice-to-Voice Query Bar (`ShrutiVoiceSearchBar.kt`):** Voice query projected into 512-d unit vector $\mathbf{u}$ and matched via cosine similarity against SQLCipher embeddings.



---

## 4. First-Time Setup & Voice Onboarding UI Flow (`ShrutiVoiceOnboardingScreen.kt`)

The onboarding process is a voice-first interactive calibration session requiring zero manual text typing:

1. **Harmonic Acoustic Orbit Visualizer:** Dynamic Jetpack Compose canvas displaying fluid multi-colored glowing orbits reacting to user spoken input and assistant prompts.
2. **Step Progress Chips:** 5 visual progress indicators corresponding to setup milestones:
   - `[1. Identity]` $ightarrow$ `[2. Location & Tower]` $ightarrow$ `[3. Gate & Landmarks]` $ightarrow$ `[4. Handover Rules]` $ightarrow$ `[5. Confirmation]`
3. **Double-Confirmation Action Sheet:** Displays extracted grounding card summary (Owner Name, Address, Landmarks, Delivery Rules) during Step 5 with voice and single-tap affirmation controls (`[Confirm & Activate]`, `[Re-record Spoken Details]`).
4. **Encrypted System Embedding Generator:** Upon affirmation, compiles facts into static System Embedding Tensor $\mathbf{E}_{	ext{sys}}$ encrypted via AES-256-GCM in SQLCipher (`profile_vector.blob`).

---

## 5. Live Delivery Guidance & Wayfinding State Machine UI (`ShrutiDeliveryWayfindingCard.kt`)

During active call screening for delivery partners (Swiggy, Zomato, Amazon, Blue Dart, Blinkit):

```
+-----------------------------------------------------------------------+
|  LIVE DELIVERY SCREENING (Swiggy / Amazon)              [00:42]       |
|  Current State: Wayfinding Landmark Guidance                          |
|                                                                       |
|  [ Gate 2 Entry ] ──► [ Pool / Clubhouse ] ──► [ Tower 14, Flat 804 ] |
|                            ▲ (Active Step)                            |
|                                                                       |
|  Landmark Instruction Sent:                                           |
|  "Enter Gate 2 near clubhouse, take 2nd right past pool to Tower 14"  |
|                                                                       |
|  Actions:                                                             |
|  [ Bridge Call to Speaker ]  [ Send Gate Pass OTP ]  [ Take Over Call]|
+-----------------------------------------------------------------------+
```

1. **Wayfinding State Stepper:** Real-time visual tracking of delivery driver progress (`State 1: Gate Localization` $ightarrow$ `State 2: Turn-by-Turn Wayfinding` $ightarrow$ `State 3: Final Mile Delivery`).
2. **Barge-In Visual Feedback:** Dynamic pulse indicator showing Silero VAD speech interruption (<32 ms) and instant KV-cache truncation.
3. **One-Tap Escalation Chips:**
   - `[Bridge Call to Speaker]`: Instant DND breakthrough call bridge to handset speaker when OTP or 3-turn failure occurs.
   - `[Send Gate Pass OTP]`: Pre-fills gate pass SMS / intent for rapid entry approval.
   - `[Take Over Call]`: Immediate JNI ring buffer handover to handset microphone/speaker.
