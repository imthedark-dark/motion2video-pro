<div align="center">

**English** · [فارسی](README.fa.md)

<img src="docs/assets/hero.svg" width="100%" alt="Motion2Video Pro — a kinetic studio built by Dark" />

# Motion2Video Pro

**Turn code into motion. Turn motion into video.**

<img src="docs/assets/badge-version.svg" height="28" alt="Version 1.4.1" />
<img src="docs/assets/badge-android.svg" height="28" alt="Android 8 and later" />
<img src="docs/assets/badge-output.svg" height="28" alt="H.264 MP4 output" />
<img src="docs/assets/badge-author.svg" height="28" alt="Created by Dark" />

[Download Android APK](https://github.com/imthedark-dark/motion2video-pro/releases/download/v1.4.1/Motion2Video-1.4.1-Dark.apk) · [Release files](https://github.com/imthedark-dark/motion2video-pro/releases/tag/v1.4.1)

[Overview](#overview) · [Quick start](#quick-start) · [Downloads](#downloads) · [Build](#build) · [Validation](#validation)

</div>

---

<div dir="ltr" lang="en">

<a id="overview"></a>
## A motion studio in your pocket

**Motion2Video Pro** is an Android app with a Persian, right-to-left interface. Import an HTML animation, preview it, and export video with Android's native media engine. The project includes a dark studio UI, an original vector logo, GSAP motion, and the **Dark** signature.

```text
animation.html  →  preview  →  frame-by-frame render  →  video.mp4
                                                        │
                                         Download/Motion2Video/
```

| Create | Export | Keep |
| :--- | :--- | :--- |
| HTML / CSS / SVG / GSAP | H.264 in MP4 as the primary output | Automatic saving to Downloads |
| Bundled Persian and Arabic fonts | Selectable 30 or 60fps | File names and saved paths |
| Preview and replay | Landscape and portrait sizes | Open files and system Downloads |
| Background render service | Two reusable frame buffers | Progress notification and cancellation |

<a id="quick-start"></a>
## Three steps to your first video

1. **Import:** choose a standalone animation HTML file or run the included preset.
2. **Preview and configure:** check the motion, duration, size, frame rate, and codec.
3. **Render:** start the job, then switch apps if needed. Check its notification or return to the app for the result and saved path.

> Android 8 or later is required. APKs and SHA-256 manifests are distributed through GitHub Releases; binaries are no longer committed on this branch. Get the signed debug APK from the [release page](https://github.com/imthedark-dark/motion2video-pro/releases/tag/v1.4.1), or build it from source.

<a id="downloads"></a>
## Your exports, together

```text
Download/
└── Motion2Video/
    └── Motion2Video_20261117_120000_001_abc123.mp4
```

**My Downloads** lists the name, path, size, and date of exports registered by this app. Open a file in a compatible player, or open its folder with a compatible file manager. If opening the specific folder is unsupported, the app falls back to system Downloads. The list persists after closing the app.

Android 10+ uses MediaStore.Downloads. Android 8 and 9 require storage permission. VP8/VP9 exports keep their actual `.webm` extension.

## Security hardening — Phase 1 review

On `security/phase-1`, imported content is offline by default. Allow online resources requires Persian confirmation; enabling it lets user code contact HTTPS servers. Bundled native libraries/fonts remain local. Separate WebViews, a closed MessagePort schema, early CSP, explicit safety settings and renderer-loss cleanup harden the boundary. See [audit findings](docs/AUDIT_REPORT.md), [decisions](docs/DECISIONS.md) and [security checks/limits](docs/PHASE1_SECURITY.md). This source work is not yet in the published v1.4.1 APK; Physical-device acceptance remains unverified; subsequent Phase 2 emulator checks are recorded below.

## Android hardening — Phase 2 review

`security/phase-2` adds narrow, read-only AndroidX FileProvider URIs, explicit network security, permission-denial notices and safe foreground-service timeout cleanup. Debug and non-debuggable, R8/resource-shrunk release builds use the checked Gradle wrapper through `python3 build.py` (`--release` creates an unsigned release). Use a full JDK 17; initial dependency downloads need network access.

The minified release rendered/saved/played a 360p30 H.264 sample on an Android 11 emulator; native FileProvider, timeout and renderer-loss checks passed. Physical devices and actual Android 15 quota dispatch remain unverified. See [Phase 2 details](docs/PHASE2_ANDROID.md) and [audit evidence](docs/AUDIT_REPORT.md). Published APKs are unchanged.

## Background rendering and speed

Rendering runs in a foreground service with a private virtual display, independently of the Activity. Home, app switching, and removing the app screen do not cancel the job. Reopening the app restores progress or the completed result. Allow notifications to see progress and cancel from the notification.

Two reusable frame buffers overlap capture with encoding. Frame-ready messages replace polling and extra Activity frame waits. The RGB-to-YUV converter uses cached planes and row writes while retaining the exact previous integer color math. Output dimensions, frame rate, bitrate and frame count are not reduced for speed.

In one desktop JVM test, converting thirty 720p frames took 152.7ms in the original path and 93.3ms in the optimized path (1.64× for this conversion stage only). This is not a whole-export or phone benchmark; actual export speed depends on the HTML and device.

**Force stop, process termination and some device battery policies can still interrupt a job.** A force-stopped job is not automatically resumed. Android also applies foreground-service time limits. [Performance and background details](docs/PERFORMANCE.md).

## Inside the studio

| Area | Tools |
| :--- | :--- |
| Interface | Single-file HTML, Tailwind, GSAP, SVG |
| Android rendering | Java, MediaCodec, MediaMuxer, FastStart |
| Bundled motion libraries | GSAP Core and five plugins, Lottie, Three.js |
| Typography | Vazirmatn, Amiri, Noto Naskh Arabic, Scheherazade New, Anton, Inter |
| Optional desktop path | FFmpeg conversion and audio mixing |

Android export is currently **silent**. HEVC/AV1 and VP8/VP9 depend on device capabilities. ProRes/MOV/alpha export is not implemented in the APK. Selecting 1080p or 60fps does not guarantee that every device supports that combination.

<a id="build"></a>
## Build from source

Install JDK 17+, Python 3, Android SDK Platform 35, and Build Tools 35.0.0.

```sh
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
python3 build.py
```

The debug APK is created in `dist/`. The local `build/debug.jks` signing key is excluded from Git. Builds produced on different machines use independent debug signing keys.

<details>
<summary><strong>UI and animation clock tests</strong></summary>

```sh
npm ci
npx playwright-core install chromium
npm run test:ui
npm run test:clock
python3 tools/test_yuv.py
```

To use a system Chromium installation, set `CHROMIUM_PATH` to its executable. The [full technical guide, in Persian](docs/BUILD.md) covers Android builds, the code structure, and detailed limitations.

</details>

<a id="validation"></a>
## What has been tested

| Check | Status |
| :--- | :--- |
| Independent source build and APK signature verification | Passed |
| Mobile/desktop UI and Downloads messages | Passed with a simulated native bridge |
| Animation clock: modern API and CSS fallback | Passed |
| Actual one-second MP4 render at 360p/30fps | Verified on an Android 11 emulator in v1.2 |
| Background service render + Home + MediaStore Downloads save | Verified on an Android 11 emulator at 360p30 |
| Activity removal, restored playable result/path/settings, immediate and notification cancellation | Passed on the Android 11 emulator |
| 72 YUV stride/offset/shared-plane regression cases | Byte-identical to the previous converter |
| Android 8/9 storage and Android 13+ notification permission | Not acceptance-tested on devices |
| Physical phones, every codec, 1080p60, and WebGL | Not acceptance-tested |

The native render sample is in [`tests/fixtures`](tests/fixtures). MP4 recording in the standalone browser HTML depends on that browser's capture and encoding support.

## Project map

```text
app/       Android app, media engine, fonts & libraries
ui/        Single-file Persian studio interface
desktop/   Optional FFmpeg export tool
tests/     UI, animation clock & Android test sources
tools/     Source audit & dependency update checker
docs/      Build guide & original README graphics
```

## License, evidence and development

Original project work is [MIT licensed](LICENSE). GSAP retains a custom license; the complete dependency stack is not entirely MIT/OSI. See [license scope](docs/LICENSING.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

[Contributing](CONTRIBUTING.md) · [Reproducible validation and device matrix](docs/VALIDATION.md) · [Public metrics](docs/METRICS.md) · [Purpose and development plan](docs/IMPACT_AND_ROADMAP.md) · [Readiness checklist](docs/READINESS.md)

Version 1.4.1 adds license packaging and documentation; rendering behavior is unchanged from 1.4.0.

## Privacy and credits

Private signing keys, tokens, local settings, environment logs, and user runtime data are excluded from publication. Imported animation HTML is not sent to an app rendering server; external resources referenced by that HTML can still access the network. See the [source audit policy](SECURITY.md) and [third-party font and library notices](THIRD_PARTY_NOTICES.md).

This README takes visual inspiration from **Wow Profile**, featured in the [GitSkins post](https://www.instagram.com/p/Dep83b7oDEb/). Its banner and badges were created specifically for this project.

</div>

---

<div align="center">

**Dark** · Code in. Motion out.

</div>

### Phase 3 security review

HTML imports now allow up to 20 MiB with filename/MIME checks and strict UTF-8 decoding. Render settings are validated at each boundary. Memory preflight, a periodic heap/system-memory watchdog, two-hour render deadline and cancellable script waits stop oversized or stalled jobs without changing frame timing or quality. See [limits and reproducible checks](docs/PHASE3_INPUT.md). Renderer/GPU memory and blocked platform calls cannot be fully bounded by the app. This review branch does not replace published APKs.

Phase 3 builds and host checks pass. Native acceptance is pending: the test emulator's system process/ActivityManager failed before instrumentation startup; physical phones were not tested.

### Phase 4 release and supply-chain review

Release signing is explicit (`python3 build.py --release --sign`) and reads environment settings or ignored local properties; debug and unsigned contributor builds remain available. APK versioned filenames come from Gradle output metadata. APK binaries are removed from this branch; existing tags/Release assets stay unchanged. Actions are pinned to full SHAs with read-only permissions by default (only an explicit guarded publication job can write Releases), Dependabot covers npm/Gradle/Actions, CI audits npm and runs isolated OWASP Dependency-Check, and schema-2 bundled version/hash/license checks reject drift. See [release process](docs/RELEASE.md), [security reporting status](SECURITY.md) and [license concerns](docs/LICENSING.md). No production release or completed native acceptance is implied.

Remaining setup: private vulnerability reporting is still disabled after GitHub403; an owner must enable it. The full OWASP scan is blocked by NVD/CISA feed access, so no clean dependency-scan result is claimed.

### Phase 5 privacy review

Native rendering has no project-owned upload service, analytics or crash-reporting SDK. WebView console forwarding is suppressed, public errors use reviewed fixed messages, and the manifest requests WebView metrics opt-out while keeping Safe Browsing enabled. Desktop FFmpeg diagnostics are off unless `--debug` is chosen. Drafts/cache/history remain local; Downloads remain outside private app storage.

Explicit online resources can send content, standalone HTML loads CDNs, and OS/provider behavior is separate. Read the [Persian/English privacy policy](PRIVACY.md) and [evidence/limits](docs/PHASE5_PRIVACY.md). These source/host checks do not establish zero device traffic; native logging/network acceptance is still pending. No new public APK was published.

Phase6 feature work is isolated on feature/phase-6; capability proposals are shown before rendering and applied only by the user. Accepted phases remain frozen on security/phase-5. See [feature audit](docs/PHASE6_FEATURES.md).

Registered exports now have a Share action using the narrow read-only FileProvider and Android chooser. If a file was moved/deleted or direct access is unavailable, re-export or use the Downloads manager.

Explicit Story/Reels, YouTube and Square size presets are available. Up to 20 named project settings are stored on this device; animation HTML, audio files and online permission are excluded. Defaults remain 1280×720, 60 FPS, 16 Mbps, H.264.

Optional source start/end ranges are validated against the configured/detected animation duration. Full-range export is unchanged; selected ranges keep output PTS at zero and round frame count at the selected fps.

The studio estimates encoded size before rendering and remaining frame-render time from measured progress. Previous same-profile render speed is retained locally for a rough preflight estimate. Estimates are not guarantees and exclude unpredictable loading/audio/save delays.

Optional Android audio accepts MP3/AAC/WAV/M4A (≤100 MiB, ≤30 min, mono/stereo, device decoder dependent). Trim, volume and fades feed streaming 48kHz stereo PCM16 and AAC-LC 192kbps; a separate MP4 remux preserves encoded video samples/PTS. Short audio pads with silence. WebM audio and >2 channels/non-PCM16 decoded formats fail clearly; silent output stays the default. Temporary audio assets (≤8, ≤160 MiB total) can be cleared in-app when idle. Native A/V acceptance remains pending.
