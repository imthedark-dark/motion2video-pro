# Phase 7 audit — 20 November 2026

Baseline: main at `ba52e38b8e0fea18e5f20650716b2db32d270e72`. The [main CI run](https://github.com/imthedark-dark/motion2video-pro/actions/runs/44714175325) passed both build and OWASP analysis. The public APK is the earlier 1.4.1 release. This audit concerns the merged source.

## Help and error messages

- The manifest launches StudioActivity. The trusted studio HTML and the imported animation use separate WebViews. The studio already has troubleshooting, compatibility and About controls. MainActivity.showTroubleshooting displays four tips on WebView updates, particles/textures, software mode and external resources/workers. About displays the WebView provider. Renderer recovery already offers retry and compatibility options.
- PublicErrors maps exceptions to an exact allowlist and sanitizes unknown errors. Examples that need an actionable next step are “HTML یا دارایی‌های آن آماده نشدند.” and “ثبت فهرست دانلودها انجام نشد.” Existing actionable messages cover memory, audio formats and a smaller H.264 export. Changes must account for producers, allowlist, persisted job statuses and tests together. Also review the HTML message handler, browser-recording errors, native dialogs/toasts, service notifications, and queue/download recovery. Preserve private-path and content redaction.
- FontWorkflow already implements consent, fallback, cancellation, a cache list, per-family deletion, clear-all and clearing remembered permissions. FAQ should explain what these actions do, offline fallback and how native font consent differs from allowing online resources for imported HTML.
- The four-tip dialog does not cover the complete requested FAQ. Add focused recovery steps for codec/size/fps, system or battery interruption, notifications on 13+, legacy storage on 8/9, online HTML, audio input, large files, WebGL, and missing/failed/cancelled/offline fonts. Include a safe problem-report entry. SECURITY.md already specifies a private route and redaction guidance. A read-only GitHub API check returned `enabled: false` for private vulnerability reporting. Until enabled, help must distinguish the private route from a public issue. The trusted studio currently rejects external navigation; do not grant arbitrary URL launching to imported content to implement this link.

## Accessibility baseline

The HTML already uses label/for associations, Persian RTL, text buttons, names on icon buttons, a preview frame title and a polite status region. The capability and online-warning HTML dialogs have visible headings but no explicit accessible name association. Check focus entry, return, navigation order, progress announcements and the separate native preview. Native Fonts settings use a scrollable list and named controls; consent combines a message, checkbox and three buttons. In particular test long family lists, confirmation text, switch labels and all actions on small screens with large type.

A temporary headless Chromium probe served local app assets at 393 × 852 CSS px with reduced motion requested. Visible FPS, clear-code and fullscreen controls measured 135 × 26, approximately 37 × 34 and 37 × 40 px. None meets a 48 × 48 layout target in this configuration. The viewport disables user scaling. Doubling computed font sizes in the HTML shell increased document scroll width from 393 to 454 px; the clear-audio control also showed horizontal text overflow. This is a browser stress test, not Android system font scaling. Native dialogs were not part of it. Existing studio-ui tests cover normal mobile/desktop layouts and capture screenshots but do not establish Android 200% or TalkBack results.

The footer CSS uses #686872 on the dark base #070709 (3.65:1, below 4.5:1 for normal text). Check actual composited surfaces, focus and interactive states during implementation. The splash checks prefers-reduced-motion and CSS suppresses some effects; message(), progress() and result() still invoke GSAP. Apply reduced motion to studio chrome independently of the user's animation, clock and output.

## Lint and CI

Ran `./gradlew --no-daemon --offline :app:lintDebug` with JDK 17 and SDK 35. Result: BUILD SUCCESSFUL, 0 errors and 28 warnings. Counts by ID:

| Warning | Count | Review in Phase 7 |
| --- | ---: | --- |
| SetTextI18n | 16 | Inventory dynamic native labels; reserve language work for Phase 8. |
| SetJavaScriptEnabled | 3 | Check each WebView trust boundary and document why scripting is required. |
| InlinedApi | 2 | Verify platform guards for the actual call paths. |
| GradleOverrides | 2 | Check duplicate SDK declarations against manifest/build verification. |
| GradleDependency | 1 | Review the dependency recommendation separately from renderer behavior. |
| DiscouragedApi | 1 | RenderEngine watchdog scheduling; assess lifecycle before changing. |
| DataExtractionRules | 1 | Review backup and transfer exclusions without relaxing privacy. |
| ObsoleteSdkInt | 1 | Candidate for removing a redundant API check after checking affected tests. |
| RtlHardcoded | 1 | LTR code editor in RTL activity; preserve text editing direction. |

These are triage notes, not warning suppressions or a dead-code removal list. No deletion justified by this audit. Add explicit full lint to push/PR CI. build.yml already builds debug/unsigned release and runs UI (including security), clock, YUV, platform, input, privacy, feature, font, asset integrity, supply-chain checks and npm audit. Preserve pinned actions, read-only build permissions and blocking dependency-check.

## Repository presentation and physical validation

Both READMEs have introductory artwork, test evidence and limitations dispersed through feature sections. Add screenshots with provenance, a clearly labelled demo placeholder, and a consolidated limitations section on audio sync, WebGL, hardware encoders, 1080p60, physical coverage and release status. GitHub currently reports no description or topics. Suggested description: “Android studio for previewing HTML animations and rendering them to MP4, with Persian controls and offline font support.” Suggested topics: android, java, webview, mediacodec, mp4, animation, persian, rtl.

VALIDATION.md already records API 30 emulator evidence, reproducible commands and phase-specific audio, queue and font cases. Historical claims such as “No microphone/audio track” need scope labels now that attached audio is supported. Preserve the distinction between the owner's Xiaomi confirmation for the font regression/Phase 9 and acceptance of all features on the merged APK.

Add fillable results rows for Android 8–15 (including 8.0/8.1 and 12/12L where relevant), Android/WebView build, app SHA, settings, pass/fail/not run and sanitized observations. Cover install, silent render, audio sync, range, cancel/continue queue, Home/return, denial of notifications where applicable, share, legacy storage permission, low space, rotation, force stop and font consent/offline reuse/cancel/delete. Document filtered logcat and observed network destinations, with no private HTML, paths, credentials, payloads or unfiltered logs in shared reports.

No new phone results. Physical TalkBack order, real 200% system type, Android reduced-motion propagation to WebView, native font dialogs, OEM battery handling and on-device A/V sync remain open. Proposed implementation commits after approval: help/errors, accessibility and tests, lint/CI and decisions, repository presentation and device validation. Language selection belongs to Phase 8.
