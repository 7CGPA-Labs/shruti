# S.H.R.U.T.I. UI/UX Design Specification

---

## 1. Executive Summary & Design Philosophy

**Target Package:** `org.seven_cgpalabs.shruti`  
**S2S SLM Engine:** `Llama-3.2-1B-Audio-Instruct` (Quantized via INT4 AWQ)  
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
                                  │     (Powered by Llama-3.2-1B-Audio)    │
                                  │   * Voice-to-Voice Search Bar          │
                                  │   * ROLE_DIALER Dialpad & Emergency    │
                                  └────────────────────────────────────────┘
```

---

## 3. Screen 2: Full Assistant & ROLE_DIALER Telephony App

### Component Details
1. **Emergency Pass-Through Button (`112` / `911`):** Direct one-tap emergency call action bypassing AI screening.
2. **Spoken Debrief Audio Cards (`ShrutiDebriefCard.kt`):** Tapping Play button (`▶`) invokes on-device **`Llama-3.2-1B-Audio-Instruct`** to synthesize a 5-to-10 second spoken voice debrief on demand (*"Delivery agent called regarding Amazon package delivery at gate"*).
3. **Voice-to-Voice Query Bar (`ShrutiVoiceSearchBar.kt`):** Voice query projected into 512-d unit vector $\mathbf{u}$ and matched via cosine similarity against SQLCipher embeddings.
