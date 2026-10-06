# Regulatory & Store Policy Analysis (S.H.R.U.T.I.)

Launching **S.H.R.U.T.I.** involves navigating regulatory and store-policy gatekeepers. Because the app operates at the intersection of cellular telephony, native in-call audio ingress, synthetic speech debriefs, and background processing, both Google Play Review and Indian telecom regulations impose specific operational boundaries.

---

### 1. Google Play Store Policy Gatekeepers

#### A. The Background SMS Rejection (`SEND_SMS` Policy)

* **The Restriction:** Google Play restricts the `android.permission.SEND_SMS` permission. Under Play Store policy, only an app selected as the user's **Default SMS Handler** is permitted to transmit SMS messages programmatically in the background. If S.H.R.U.T.I. silently fires complaint texts to `1909` without making itself the default texting app, Google will reject the build during automated policy review.
* **The Solution:** Decouple SMS transmission from background execution. Instead of firing an automated background SMS, populate an explicit Android system intent (`Intent.ACTION_SENDTO` with `smsto:1909`) and present it to the user as a 1-tap floating notification chip (`[Send 1909 Report]`). The user taps it, the native Messages app opens pre-populated, and the user hits send.

#### B. The Default Dialer (`ROLE_DIALER`) Scrutiny

* **The Restriction:** Accessing in-call audio streams on Android requires `android.app.role.DIALER` privileges via `InCallService`. Google requires any app holding `ROLE_DIALER` to be a **complete telephony client**:
  * Must implement a full dialpad, in-call screen, missed-call handling, contact picker, and direct integration with Android emergency services (`911` / `112`).
  * If the app fails to provide full dialer capabilities, Google will revoke the permission and reject the submission.
* **The Solution:** Ship a production-grade dialer UI for everyday calling with full contact management and instant emergency call pass-through (`112` / `911`), allowing S.H.R.U.T.I. to passively vectorize calls safely within Google's policy framework.

#### C. Android 14/15 Foreground Service Type Audits

* **The Restriction:** Ingress audio capture requires `FOREGROUND_SERVICE_MICROPHONE`. Google Play policy requires:
  * A persistent, non-dismissible notification stating clearly that the microphone is active.
  * The OS will permanently show the bright green microphone privacy dot in the status bar while active.
  * Google reviewers routinely reject apps using continuous microphone foreground services if they run when the user is not actively engaged with an ongoing call or recording session.
* **The Solution:** Avoid continuous background audio recording. Restrict `FOREGROUND_SERVICE_MICROPHONE` strictly to active PSTN calls managed via `InCallService` or user-initiated deliberate ambient sessions (e.g., triggered via double-tap back gesture $> 14\text{ m/s}^2$ or Quick Settings tile).

#### D. AI Transparency & Deceptive Behavior Policies

* **The Restriction:** Google Play's AI & Synthetic Media policy prohibits generative AI agents from deceptively impersonating real humans or deceiving remote callers.
* **The Solution:** S.H.R.U.T.I. operates strictly as a **passive listener** on live calls. It **never speaks to, answers, or converses with remote callers**. The human user speaks directly with the caller. For post-call spoken debriefs, the assistant communicates solely with the phone owner, and its AI assistant identity is established transparently during initial voice onboarding.

#### E. Hardware NPU Fragmentation & Vendor Lock-In Bypass

* **The Restriction:** Deploying models via proprietary vendor NPU runtimes (Qualcomm QNN HTP, MediaTek NeuroPilot) introduces severe fragmentation and incompatibility crashes on mid-range/budget devices lacking vendor firmware blobs, risking high Google Play crash rates.
* **The Solution:** S.H.R.U.T.I. relies on **`llama.cpp` + Khronos Vulkan 1.1+ (`libvulkan.so`)**, backed by universal Android CTS conformance across Adreno, Mali, PowerVR, and Xclipse GPUs, with automatic multithreaded ARM NEON CPU fallback.

---

### 2. Indian Regulatory & Government Constraints (TRAI, DoT, MeitY)

#### A. DoT Regulations on PSTN-to-VoIP Bridging (The "Toll Bypass" Trap)

* **The Restriction:** The Department of Telecommunications (DoT) enforces strict regulations regarding the interconnection of the Public Switched Telephone Network (PSTN / cellular) and Voice-over-IP (VoIP / Internet).
* Under the Indian Telegraph Act and Unified License (UL) guidelines, routing a local cellular call onto the public internet (e.g., via a standard WebRTC gateway) and bridging audio between cellular lines and data networks without an authorized telecom license is prohibited if it bypasses licensed carrier switching.
* **The Solution:** S.H.R.U.T.I. performs on-device vectorization directly via Android's local telephony audio pipeline (`InCallService` + `AudioRecord`). No cellular audio is forwarded across unauthorized VoIP gateways.

#### B. Digital Personal Data Protection Act, 2023 (DPDPA)

* **The Advantage:** S.H.R.U.T.I.’s **zero-persistence architecture** is a major legal asset under DPDPA:
  * Section 3(c) of DPDPA provides exemptions for personal data processed by an individual for a **personal or domestic purpose**.
  * Because the app stores zero audio files, zero plain text, and runs ML models on local silicon without exfiltrating caller audio to centralized cloud servers, S.H.R.U.T.I. avoids the legal liabilities of being a "Data Fiduciary" handling biometric voice data.
* **The Requirement:** If the app is distributed commercially through an incorporated entity, you must still provide a transparent privacy policy declaring that voice audio is processed in volatile RAM and converted into non-invertible latent embeddings without transmission to third parties.

#### C. Telemarketer Reporting (TRAI TCCCPR Regulations)

* **The Restriction:** TRAI's Telecom Commercial Communications Customer Preference Regulations (TCCCPR, 2018) enforce strict formatting and spam complaint limits. Submitting falsified, automated spam complaints to `1909` from arbitrary calls (such as legitimate personal or courier calls flagged incorrectly by an AI hallucination) can lead to the user's mobile number facing scrutiny or temporary DND suspension for abuse.
* **The Solution:** Always enforce human-in-the-loop validation for 1909 complaints. The AI suggests the complaint body; the human approves the dispatch.

---

### Compliance Checklist for Launch

| Component | Potential Blocker | Required Implementation Fix |
| --- | --- | --- |
| **TRAI 1909 SMS** | Google Play `SEND_SMS` ban | Pre-fill SMS via `Intent.ACTION_SENDTO`; require user tap. |
| **In-Call Audio Access** | Google Play `ROLE_DIALER` rejection | Build full production dialer UI with emergency `112`/`911` pass-through. |
| **Ambient Listening** | Android FGS Mic policy & Green Dot | Gate listening via deliberate sensor triggers; avoid continuous background mic. |
| **PSTN $\to$ SIP Trunk** | DoT Interconnect / Toll Bypass rules | Purely on-device processing via Android telephony stack; no unauthorized VoIP bridging. |
| **AI Impersonation Risk** | Play Store Anti-Impersonation policy | AI is strictly a passive listener on calls (never speaks to remote callers); on-device assistant persona declared during voice onboarding. |
| **Device Compatibility** | Proprietary NPU driver crashes | Standard Khronos Vulkan 1.1+ via `llama.cpp` (`libvulkan.so`) with ARM NEON fallback. |
| **DPDPA Compliance** | Biometric / Voice processing liability | Highlight on-device zero-cloud architecture in Terms of Service and privacy policy. |