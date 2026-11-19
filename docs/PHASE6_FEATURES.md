# Phase 6 — feature audit and implementation record

Baseline audited at815cb4f before feature changes. User accepted Phases1–5 and subsequently approved continuing the whole requested sequence. main remains c8d9d19; security/phase-5 is five commits ahead and contains Phases1–4. A read-only merge-tree simulation is conflict-free. No merge/main push, production signing-key creation or Release publication is authorized/performed here. feature/phase-6 starts at815cb4f; accepted phase branches remain frozen.

| Feature | Existing implementation / missing work |
| --- | --- |
| a. Capability | CodecSupport.encoder already queries MediaCodecList, flexible YUV, size/rate/bitrate and AV1 OS support. It rejects unsupported requests with fixed guidance; no ranked alternative or apply-suggestion flow exists. |
| b. Audio | Mp4Encoder adds one video track. No native audio picker, decoding/DSP/AAC/mux path exists. Desktop FFmpeg can attach audio but is a separate tool. Browser recording explicitly requests audio:false. |
| c. Range | RenderRequest has duration only; frames=round(duration*fps), capture=i/fps and PTS=i*1e6/fps. No start/end fields exist. |
| d. Estimates | Service stores elapsedMs/done/total; UI shows progress and average frame/s. Size appears only after export. No ETA or pre-render size estimate exists. |
| e. Queue | One active request. The single queued Intent only bridges the completion/start race; active-job submissions are otherwise ignored. It is not a durable multi-job queue. |
| f. Share | DownloadRepository opens registered content URIs through ACTION_VIEW with read grant/ClipData; no ACTION_SEND chooser exists. Narrow read-only FileProvider and MediaStore foundations already exist. |
| g. Presets/settings | Four sizes, one sample HTML and draft autosave exist. No Square size or per-project settings store exists. |
| h. Extra output | Native supports existing MP4/WebM video codecs only. No native GIF/WebP/PNG-sequence path. Optional extras will be skipped if they risk/delay the default pipeline. |

Implementation constraints: preserve default silent full-range encoding and all YUV/colour/timestamp math; suggestions require user application, not silent quality changes. Additive Persian controls retain the single-file UI and previous privacy/network/watchdog policies. Audio and queue data stay app-private and bounded. Each required feature gets its own reviewed commit and validation record. Native hardware/codec/A-V timing acceptance requires a working device; host tests and compilation must not be called native PASS.

Pre-feature revalidation on security/phase-5: debug and unsigned/R8 release builds PASS; UI/security, clock,72 YUV, platform/copy,23 input/budget,22 privacy,159 request-schema and17 supply-chain cases PASS; all36 asset hashes/13 records and compiled privacy/manifest/R8 checks PASS. npm audit reports zero known vulnerabilities. Prior OWASP feed/admin-reporting/device gaps remain; not all external/native acceptance is complete.

## 6a — capability suggestions

Implemented native preflight on a worker thread, positive encoder cache and deterministic nearest-profile ranking. Unsupported profiles show a Persian reason plus an explicit Apply suggestion dialog; settings are never silently changed or auto-rendered. Cancellation invalidates a pending probe. Existing encoder checks remain enforced at execution. No encode, dimensions, bitrate, timing or YUV changes.

Validation: debug and unsigned/R8 release builds; UI/security, clock, 72 YUV, platform, input, privacy, request-schema, supply-chain, asset and compiled-manifest/privacy checks all PASS. Production capability ranking tests PASS (codec/orientation/upscale/AVC fallback). Native MediaCodec availability and dialog acceptance still require a physical device.

## 6f — share exported video
Added a closed shareDownload command, generated-name/canonical-parent validation, read-only FileProvider URI/ClipData, and a system chooser from each history row. No provider roots expanded. The history URI is only an identity lookup; callers cannot supply a file path. Deleted/moved files fail safely; scoped-storage/OEM direct-path failures require re-export or the system Downloads manager. Host schema rejects non-content URIs; browser transport verifies the selected registered URI and safe text rendering. Actual receiving-app grants need device acceptance.

6f validation: debug and unsigned/R8 release builds and full feature-check suite PASS (UI sharing transport, 165 request-schema cases, clock, 72 YUV, platform, input, privacy, feature ranking, supply-chain, asset/manifest checks). No production signing or publication.

## 6g — size presets and project settings
Appended Square 1080×1080 to all native/schema/UI size sets without shifting default indices. Explicit Story/Reels/YouTube/Square presets affect size only. Named settings use a bounded local array (20 entries, 64-character names, 64KB read ceiling), safe option text, strict revalidation on load and rollback on invalid data. No HTML, audio reference or online consent persists in project settings; existing draft autosave is unchanged.

6g validation: full debug/release and feature-check suite PASS; 195 request/bridge cases include all five sizes. UI tests apply Square, preserve fps/bitrate, safely display a markup-like project name, save/load/delete settings and check excluded sensitive fields. No physical-device claim.
