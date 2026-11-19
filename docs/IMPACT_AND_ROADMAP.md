# Purpose, demonstrated impact and development plan

## The problem and current result

A developer may have a motion graphic written in HTML/CSS/SVG/GSAP but need a portable video rather than a browser preview. On Android, recording a visible screen can include interface controls, depend on screen state and miss frames. Motion2Video provides an HTML editor/preview and an Android frame-by-frame export path with controlled duration, dimensions, frame rate and bitrate. The interface is Persian and right-to-left; bundled Persian/Arabic fonts and browser text shaping preserve typography in the rendered pixels.

For example, a one-second HTML animation at 30fps produces a thirty-frame H.264 MP4 in the tested 360p case. A service-owned rendering view lets that job continue when the studio Activity is removed; reopening retrieves the saved result and Downloads path. This behavior has been demonstrated on an Android 11 emulator. It is not evidence of broad physical-device adoption, production reliability or universal animation compatibility.

## Evidence and limits

- [Reproducible checks and compatibility matrix](VALIDATION.md) identify exactly what was tested.
- [Public metrics snapshot](METRICS.md) separates Stars/Forks from actual usage. Active users, raw APK download counts and user outcomes are unknown; the application has no usage telemetry.
- [Performance notes](PERFORMANCE.md) explain reduced processing overhead. The reported converter benchmark does not measure total render speed on phones.
- [License review](LICENSING.md) licenses original project work under MIT while preserving GSAP's custom terms and other upstream licenses.

Exports are currently silent. MediaCodec capabilities, Android foreground-service limits, battery management and WebView behavior vary. The application has no production-support or all-device guarantee. Imported HTML can fetch external resources it references; the app does not send it to a dedicated rendering server.

## Development plan

This is a prioritized plan, not a delivery-date commitment or a list of finished features.

| Priority | Work | Acceptance evidence needed |
| --- | --- | --- |
| 1 | Physical-device reliability and background behavior | Reports across Android versions/vendors covering screen off, app switching, Activity removal, permission denial, low storage, cancellation and interruption |
| 1 | Distribution and dependency hygiene | v1.4.1 APK + checksum now attached and checked; retain that verified process for later releases; required notices retained in subsequent builds; pinned dependency review |
| 2 | Broader output quality/capability coverage | Decode and frame-count checks at 720p/1080p/portrait and 30/60fps; device-specific codec results; fonts/color comparisons |
| 2 | Better failure and resource handling | Reproducible memory/thermal/timeout failures, clear UI recovery, no orphaned temporary files or stuck progress |
| 3 | Audio export design and implementation | Explicit timing/mixing model, synchronization tests and codec/container compatibility; no audio support claimed until implemented |
| 3 | Optional GPU/WebGL path hardening | Reproducible capture tests, decoded reference frames, supported-device documentation |
| Before any visual no-code editor | Re-evaluate animation-library licensing | License permitting the intended builder or written permission where required; no assumption that the MIT project license covers GSAP |

Contributors can use [CONTRIBUTING.md](../CONTRIBUTING.md). New claims of performance, compatibility or adoption must cite measurements or public evidence rather than estimates presented as facts.
