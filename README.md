# PRINCE AI STUDIO

**Short Name:** PRINCE AI  
**Tagline:** *Imagine. Create. Innovate.*  
**Platform:** Android (Kotlin, Jetpack Compose, Material 3) + Firebase Auth, Cloud Firestore & Firebase AI Logic

---

## Overview

**PRINCE AI STUDIO** is a private-access, role-based AI creation studio built natively for Android. It separates creator workflows and platform administration into dedicated dashboards protected by Google Sign-In (`CredentialManager`), Firebase App Check, and zero-trust Firestore Security Rules.

### Access & Security Architecture

- **Private Access Mode:** Public self-registration is disabled in `firestore.rules`. New accounts are provisioned only through valid invitation codes (`PRINCE-XXXXXX`, email-locked or open) redeemed atomically via `WriteBatch` against `/invitations/{inviteCode}`.
- **Platform Owner:** `shahprince0021@gmail.com` (verified Google email) automatically bootstraps `/users/{uid}`, `/admins/{uid}`, `/system_config/global`, and default `/subscription_plans` (`free`, `basic`, `pro`, `custom`).
- **Role-Based Dashboards:**
  - **User Dashboard:** Personal overview, AI Video Planner, AI Voice Studio, AI Image Studio, AI Script Assistant, My Projects & Generation History, Subscription Details, Usage Statistics, Notifications, and Admin Support inbox.
  - **Owner / Admin Console:** Platform analytics, User management (search, invite, pre-provision, activate/suspend/deactivate, delegated Admin grants by Owner only), Subscription plan editor & upgrade request approval/rejection, custom per-user limits & plan recommendations, AI tool & invite toggles, Maintenance Mode, Announcements, and Admin/Service Error Audit Logs.

---

## Active AI Providers & Transparency

| Studio | Provider / Engine | Status | Capabilities |
| :--- | :--- | :--- | :--- |
| **AI Script & Chat Assistant** | Firebase AI Logic (`gemini-3.8-flash`) | **Active** | Streaming multi-turn creator chat for scripts, documentaries, prompts, narration polish, and scene organization. |
| **AI Image Studio** | Firebase AI Logic (`gemini-nano-banana-2.1`) | **Active** | Runtime image generation (1:1, 16:9, 4:3, 9:16), gallery preview, MediaStore PNG export, and Android share sheet. |
| **AI Voice Studio** | Android `TextToSpeech` + `gemini-3.8-flash` | **Active** | Multilingual voice synthesis (English, Hindi `hi-IN`, Nepali `ne-NP`), speed/pitch controls, live preview, WAV synthesis, playback, and WAV export. |
| **AI Video Studio (Planner)** | `gemini-3.8-flash` + `gemini-nano-banana-2.1` | **Active** | Structured JSON storyboards (`Schema.obj`), documentary/short-video scene planning, narration handoff to Voice Studio, and per-scene keyframe previews. |
| **Direct MP4 Video Rendering** | External Video Render Provider | **Not Configured** | Intentionally disabled in UI and marked unconfigured in `system_config/global` until a dedicated video-rendering backend is integrated. |

---

## Verification & Testing

- **Security Rules (Tier 1 - JS):** `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
- **Repository & Rules (Tier 2 - Robolectric):** `gradle :app:testDebugUnitTest --tests '*Rule*'`
- **Unit & Robolectric Tests:** `gradle :app:testDebugUnitTest`
