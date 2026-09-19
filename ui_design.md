# S.H.R.U.T.I. UI/UX Design Specification

---

## 1. Executive Summary & Design Philosophy

**Target Package:** `org.seven_cgpalabs.shruti`  
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
                                  │   * Voice-to-Voice Search Bar          │
                                  │   * ROLE_DIALER Dialpad & Emergency    │
                                  └────────────────────────────────────────┘
```

---

## 3. Screen 0: 3-Action Incoming Call Screen (`ShrutiInCallActivity.kt`)

When an incoming PSTN / cellular call rings, S.H.R.U.T.I. presents a modern caller screen with **three distinct user action controls**:

```
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|                                       👤 Unknown                                        |
|                                   +91 98765 43210                                       |
|                                Mumbai, Maharashtra                                      |
|                                                                                         |
|                                 [ 🛡️ TRAI 160 Check: Pass ]                              |
|                                                                                         |
|                                                                                         |
|                                                                                         |
|        🔴                       ✨                        🟢                            |
|    [ DECLINE ]       [ SCREEN WITH S.H.R.U.T.I. AI ]   [ ANSWER ]                     |
|  (Swipe Left / Red)    (Center Glowing Gemini Button)   (Swipe Right / Green)           |
|                                                                                         |
+-----------------------------------------------------------------------------------------+
```

### 3.1 Primary Action Controls
1. **`[ Decline / Reject ]` (Red Button / Swipe Left):** Instantly terminates incoming ringing call and routes caller to voicemail/busy tone.
2. **`[ Answer / Pick Up ]` (Green Button / Swipe Right):** Connects user directly to caller audio through handset earpiece/speaker (Traditional Call Answer).
3. **`[ Screen with S.H.R.U.T.I. AI ]` (Center Glowing Purple/Cyan Pill):**
   * Features a pulsating **Gemini-Style Acoustic Orbit Icon** with a multi-gradient glowing border (`#7C4DFF` Violet $\to$ `#00E5FF` Cyan).
   * Tapping this button accepts the call in AI bridge mode, mutes local user microphone, triggers mandatory Turn 1 AI disclosure (*"I am an automated voice assistant screening this call for..."*), and transitions seamlessly into the **Gemini-Style Voice Activation Overlay**.

---

## 4. Screen 1: Voice-Activated Screening Overlay (Gemini-Style)

Surfaced immediately when the user taps `[Screen with S.H.R.U.T.I. AI]` or when automatic screening of unknown numbers is engaged.

### 4.1 Visual Anatomy & Features
1. **Dynamic Gemini Aura Border:**
   * An animated multi-gradient border (`#7C4DFF` Violet, `#00E5FF` Cyan, `#FF4081` Magenta) sweeps along the top edge of the overlay sheet using Compose `Brush.sweepGradient`.
   * Pulse frequency scales dynamically with caller audio amplitude.
2. **Acoustic Orbit Visualizer (`ShrutiAcousticOrbit.kt`):**
   * A central, fluid 3D particle sphere that deforms organic control points based on real-time FFT frequency bands extracted by Silero VAD / Codec Encoder.
   * State Animations: **Idle/Listening**, **Caller Speaking**, **AI Reasoning**, **AI Speaking**.
3. **Floating Action Chips (`ShrutiActionChips.kt`):**
   * `[Take Over Call]`: Connects call to user handset earpiece.
   * `[Preset Voice]`: Dispatches pre-synthesized voice response.
   * `[Send 1909 Report]`: Terminates call and opens pre-filled system SMS app (`Intent.ACTION_SENDTO` to `smsto:1909`).

---

## 5. Screen 2: Full Assistant & ROLE_DIALER Telephony App

The primary application interface (`ShrutiMainActivity.kt` / `ShrutiConversationActivity.kt`) provides a full Android telephony client (`ROLE_DIALER`) alongside an audio-first dashboard.

### 5.1 Component Details
1. **Emergency Pass-Through Button (`112` / `911`):** Direct one-tap emergency call action bypassing AI screening.
2. **Spoken Debrief Audio Cards (`ShrutiDebriefCard.kt`):** Tapping Play button (`▶`) synthesizes a 5-to-10 second spoken voice debrief on demand.
3. **Voice-to-Voice Query Bar (`ShrutiVoiceSearchBar.kt`):** Voice query projected into 512-d unit vector $\mathbf{u}$ and matched via cosine similarity against SQLCipher embeddings.

---

## 6. Design System Tokens

| Token Name | Hex Code | Purpose |
| :--- | :--- | :--- |
| `BackgroundDark` | `#0D0F14` | Main screen background |
| `SurfaceDark` | `#161922` | Card & Overlay container background |
| `GeminiViolet` | `#7C4DFF` | Primary aura gradient color 1 & AI Screen Button |
| `GeminiCyan` | `#00E5FF` | Primary aura gradient color 2 & Active listening state |
| `GeminiMagenta` | `#FF4081` | Dynamic speaking state glow |
| `DeclineRed` | `#FF3D00` | Decline call button |
| `AnswerGreen` | `#00E676` | Answer call button |
| `AlertCoral` | `#FF5252` | TRAI 1909 spam warning & call drop button |
