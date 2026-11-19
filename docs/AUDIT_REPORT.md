# Security audit — initial findings and Phase 1

Baseline: `c8d9d19`, reviewed 2026-11-19. This initial report was written before Phase 1 implementation changes. Scope: all first-party Android Java sources/resources/manifest, canonical HTML/clock/preset, desktop exporter, Python/Node tools, Gradle/custom builders, workflows and existing JVM/browser/instrumentation tests. Bundled minified dependencies/fonts were inventoried through headers, notices and hash manifest; this is not a full upstream dependency security audit or a physical-device penetration test.

## Verified baseline findings

| ID | Finding verified in the code | Phase |
| --- | --- | --- |
| W1 | Imported preview (`MainActivity` WebViewClient) and service renderer (`OffscreenStage` → `StageAssets.response`) return null/super for unrecognized requests. That delegates to WebView networking. No online-resource consent exists. | 1 |
| W2 | No CSP is installed for imported HTML. Browser preview has `sandbox="allow-scripts"`, but native imported WebViews have no equivalent CSP. Subframes, network APIs and redirects are not comprehensively constrained. | 1 |
| W3 | All three native WebViews disable file/content access and mixed content. Neither file-URL cross-access flag is explicitly set; Safe Browsing/debug policy is not explicitly configured. Imported DOM storage is disabled; trusted studio storage is enabled for its editor state. | 1 |
| W4 | Repository search and all WebView setup confirm **no `addJavascriptInterface` / `@JavascriptInterface` API**. Trusted studio receives a private MessagePort at `https://studio.motion.local/index.html`; imported content is at `https://motion.local/` in another WebView. The render clock port acknowledges frames only. Preserve this smaller surface instead of adding a Java interface. | 1 |
| W5 | `StudioActivity.command` has a 3 MiB message bound, 2 MiB HTML bound, duration/FPS/size/bitrate/codec checks and busy guards. However JSONObject getters coerce types, unknown commands are silently ignored, layout x/y magnitude is not bounded and download URI strings have no explicit bridge length/shape check. `DownloadRepository.find` already restricts opening to registered downloads. | 1 |
| W6 | No native WebViewClient overrides `onRenderProcessGone`. WebView renderer loss is therefore not handled with explicit cleanup/error propagation. | 1 |
| M1 | Manifest already has allowBackup=false, cleartext disabled, launcher exported=true, internal Activity/service/provider exported=false, storage maxSdk=28, notification permission and dataSync/mediaProcessing FGS declarations. No network_security_config, standard FileProvider or release shrinker configuration exists. | 2 |
| I1 | HTML imports use a 2 MiB bound; extension/MIME/strict UTF-8 validation is not enforced. There is no ZIP/folder importer. `RenderRequest` already limits duration to 1–300s, FPS to 30/60, four dimensions, three bitrates and known codecs. | 3 |
| I2 | Offscreen readiness/frame/encoder waits are bounded; there is no overall memory budget/render watchdog. `onTimeout` stops the service while worker code still references request state; lifecycle/race handling needs separate Phase 2/3 review. | 2/3 |
| S1 | Signed APKs are committed in downloads. Builders use local development signing; Gradle has no release signing/minify block. Actions use version tags, not full SHAs. CI audits/checks APKs/YUV/build but does not run browser suites/npm audit/dependency checks. Release-asset publisher requires contents:write and verifies committed bytes. | 4/7 |
| P1 | No app telemetry/crash-reporting SDK is present. Imported resources can currently contact their referenced servers. Service state/history intentionally contain local paths; instrumentation prints some state/paths. This does not support an unconditional "no data leaves device" claim. | 1/5 |
| T1 | Existing tests cover UI/native-message simulation, animation clock, 72 exact YUV cases, legacy/native render and background recovery/cancel. They contain no hostile-network/undeclared-command/renderer-loss security regression fixtures. No physical Android device is connected to this environment. | 1/7 |

## Phase 1 implementation record

Implemented on `security/phase-1` (one review commit, no release/tag replacement):

- W1/W2: fail-closed asset routing and native network blocking by default; session-scoped Persian opt-in captured per job; CSP precedes imported scripts; static asset aliases normalized to local HTTPS. Browser iframe navigation is restricted and its default GSAP engine is bundled inline.
- W3: shared explicit settings cover all three WebViews: file/content/cross-file access OFF, Safe Browsing ON, third-party cookies/popups denied, debugging tied to the APK flag. Service-worker loads have a separate fail-closed native client/settings.
- W4/W5: keep private MessagePort separation; add closed command/key/type schemas and bounded HTML, URI, numeric/layout arguments. Animation gets only a bounded frame-ack channel. No JavaScriptInterface was introduced. Invalid commands do not unlock an active render.
- W6: each WebView handles renderer loss, detaches/destroys the failed view and cancels/errors active/pending work with Persian UI feedback. Cleanup and process-loss acceptance require the native checks below.
- Frame capture/codec/YUV conversion implementation, dimensions, FPS, bitrate, frame count/timestamps and manifest/build/workflows are unchanged in this phase. Offline resource availability is intentionally stricter; inline/eval script execution remains supported for motion code. See DECISIONS.md.

### Executed host checks

| Command / check | Actual result |
| --- | --- |
| `python3 build.py` with SDK35/JDK17 | PASS: APK built, aligned and signature verified |
| `npm run test:ui` | PASS: mobile/desktop/native-CSS layouts, four preset frames, transport/downloads, forged port/unknown messages, opt-in confirmation and session restore |
| Included `tests/security.cjs` / production `ImportedContent.java` | PASS: zero requests at real TLS server offline; fetch/XHR/beacon/images/scripts/styles/WebSocket/frame/worker/navigation blocked; explicit HTTPS online requests received |
| `npm run test:clock` | PASS: Web Animations and legacy negative-delay CSS paths |
| `python3 tools/test_yuv.py` | PASS: all 72 cases byte-identical, including shared UV buffers |
| Instrumentation APK build | PASS: existing tests plus WebSecurityTest and RendererGoneTest compile/sign |
| `python3 tools/audit_source.py` / `git diff --check` | PASS at the time of verification; rechecked before commit |
| Canonical HTML / APK asset comparison | PASS: packaged studio.html matches ui/Motion2Video-Studio.html |

`npm ci --ignore-scripts` initially hit this environment's unavailable default npm cache. Retrying with a writable task cache installed the locked dependency successfully; no dependency version/integrity changes were made. Browser tests used `/usr/bin/chromium`; native API behavior is a separate acceptance boundary.

### Native checks and remaining work

Android runtime acceptance is **not verified**. The API30 software emulator booted, but `pm install -r` timed out on its transport after 180 seconds before instrumentation began. No WebSecurityTest, NativeStartupTest, BackgroundRenderTest or RendererGoneTest runtime pass is claimed for Phase 1. Both new native fixtures compile/sign; the emulator was stopped after the failed installation attempt. No physical Android target is connected.

In particular, vendor Android behavior of intercepted local HTTPS assets with `setBlockNetworkLoads(true)` and actual process-death callback cleanup still needs the device commands in PHASE1_SECURITY.md. The Chromium tests exercise the production Java CSP against an unknown external TLS origin; native CDN aliases additionally rely on StageAssets and its worker/page interception boundaries. These host passes are not a substitute for that Android acceptance run.

M1/I1/I2/S1/P1 follow-up work remains assigned to its original later phase. No manifest/FileProvider/R8, import-limit/watchdog, release migration, privacy feature, audio/queue or i18n work was started. Phase 2 requires the user's confirmation.

## Phase 2 — verified baseline before implementation

Baseline: `624e48b`. The user authorized Phase 2 on 2026-11-19.

- Manifest already limits WRITE_EXTERNAL_STORAGE to API28, declares notification/FGS permissions, exports only the launcher, disables backup and cleartext, and uses dataSync/mediaProcessing service types. No receivers exist. A dedicated network-security XML and explicit release debuggable/minify/resource-shrink settings are missing.
- DownloadRepository already opens registered `content://` URIs. Its legacy authority uses a custom read-only ContentProvider, not AndroidX FileProvider. Keep that authority and `/file/` root mapping so recorded Android 8/9 URIs remain compatible; do not widen file roots or grant writes.
- `RenderService.onTimeout` invokes `finishService`, which nulls request while `runJob` dereferences request in saving/finally. Cancellation does not stop the Downloads copy. Worker callbacks can outlive a stopped service and need a publication guard. Handle the OS deadline without waiting for a long worker join on the main thread.
- Storage requests use SDK<29 (correct for this app's minSDK26); notification requests use SDK>=33. Permission-denial callbacks already continue rendering, but denial should be explicitly explained without unlocking the job or treating it as a render failure.
- The SDK-only builder has no AndroidX dependency/resource merge or R8/resource-shrinking pipeline. Standard Gradle builds are needed for AndroidX FileProvider and a genuinely minimized release. Keep `python3 build.py` as the contributor entry point and preserve local debug-key override support. Production signing remains Phase 4.

Implementation/verification results will be recorded below. No Phase 3 work is authorized yet.

### Phase 2 implementation and measured verification

- Replaced the custom provider with AndroidX FileProvider and a read-only generated-export guard. Existing authority/root URIs are retained. No cache/storage root is shared; MediaStore remains for API29+. Verified the merged ProfileInstallReceiver is non-exported, alongside both providers, internal Activity and service. Only the launcher is exported.
- Added explicit system-CA-only, cleartext-denying network XML; kept backup false and correct FGS permissions/types. Shared permission API bounds and informational denial notices preserve rendering without granting access or unlocking the active UI.
- Fixed the timeout request-lifetime race: worker uses its captured job, OS timeout cancels work/releases wake/stops promptly, cancelled Downloads copy cleans its own pending output, stale service publishers are ignored and interrupted private files have recovery cleanup. No main-thread worker join was added.
- Moved the Python entry point onto the checked Gradle 8.9 wrapper, AndroidX dependency merge and real AGP8.7.3 R8/resource shrinking. Default debug builds remain signed locally; release is non-debuggable and unsigned until Phase 4. Keep rules preserve framework/MessagePort entry points without preventing app obfuscation. The whole encoder/frame/YUV pipeline and output settings remain unchanged.

| Verification executed on 2026-11-19 | Result |
| --- | --- |
| `python3 build.py` | PASS: Gradle debug APK |
| `python3 build.py --release` | PASS: actual minifyReleaseWithR8 / shrinkReleaseRes / release vital lint tasks; unsigned release produced |
| `tools/verify_phase2.py` | PASS: compiled debug/release manifests, all component flags, permission cap/FGS bits, FileProvider roots, network XML, canonical UI, renamed release XML resolution and actual obfuscated R8 class mappings |
| `npm run test:ui` | PASS: existing UI/download/security cases plus permission-denial message retaining the render lock |
| `npm run test:clock` | PASS: both animation-clock paths |
| `python3 tools/test_yuv.py` | PASS: all 72 byte-identical cases |
| `python3 tools/test_platform.py` | PASS: API boundaries, byte-identical successful copy, pre-cancel and mid-copy cancellation |
| Native `ReleaseSmokeTest`, minified non-debuggable APK on API30 emulator | PASS: UI/bridge, 30-frame 640×360 H.264 export, saved playback, FileProvider read and write rejection |
| Native `WebSecurityTest`, debug APK on API30 emulator | PASS: native page/worker settings, intercepted resources and strict bridge schema |
| Native `ServiceTimeoutTest`, debug API30 emulator | PASS: manual timeout callback during active frames, service stopped, partial output removed, terminal state not overwritten |
| Native `RendererGoneTest`, debug API30 emulator | PASS: actual renderer terminated, app survived, partial output removed |
| Independent FFprobe/full FFmpeg decode of minified-release output | PASS: 1s, 30 frames, 640×360, H.264/YUV420P, BT.709 limited, faststart |

Release QA used a temporary development-signed copy outside Git, not production signing or a new public release. Raw runtime logs/private paths and keys are excluded; a generated sample and sanitized JSON evidence are in tests/fixtures. These later tests close the inherited WebView/runtime evidence gap on this particular Phase 2 emulator build; they do not retroactively claim that the exact Phase 1 binary ran or that physical phones were tested.

Environment preparation used a checksum-verified full JDK17 because the workspace's original stripped JDK lacked jlink, plus local proxy/trust configuration outside the repository. TLS verification was not disabled in the app or build. The source wrapper distribution is SHA-256 pinned. Library license notices were added for AndroidX and related runtime dependencies.

Remaining acceptance gaps: physical devices; actual API26/28 storage prompts, API33+ notification denial, Android15 real quota dispatch, Windows helper execution, WebGL/1080p60/all codec combinations. Overall watchdog/memory/import hardening is Phase 3; signing/supply chain is Phase 4. The user must confirm before Phase 3.

## Phase 3 baseline (before implementation)

Verified on commit `8f65384`: MainActivity and StudioActivity read imports with a 2 MiB byte cap and replacement UTF-8 decoding, without checking provider filename or MIME. Browser uploads also use 2 MiB and replacement decoding. BridgeValidation and RenderRequest cap HTML at 2 MiB; request JSON is capped at 3 MiB. No ZIP or directory import exists. RenderRequest already restricts duration to 1–300 seconds, FPS to 30/60, bitrate to 12/16/20 Mbps, and four output sizes up to 1920×1080 or 1080×1920. StudioActivity repeats these enum checks; BridgeValidation's numeric ranges alone are broader. OffscreenStage has 45-second operation waits and a 120-second readiness deadline. RenderEngine has encoder waits and cleanup, but no memory preflight, ongoing memory ceiling or overall deadline. User cancellation does not wake a pending JavaScript readiness future. The legacy MainActivity path has 15-second JavaScript/frame waits and a 30-second preparation deadline.

### Phase 3 implementation and host verification

- Added shared 20 MiB HTML admission policy, provider metadata checks, bounded streaming and strict UTF-8/BOM handling in both native import paths; browser uploads mirror it. Invalid input preserves editor content. Native imports run off the UI thread with one concurrent worker. No ZIP/folder import was added.
- Applied payload limits to the closed bridge and private jobs; closed enum/type/range validation now rejects fractional/coerced/overflow settings before native configuration.
- Added Java heap allocation preflight, one-second heap/system-low-memory checks, a monotonic two-hour service-render deadline and stalled-pipeline deadline. Existing 45-second operations/120-second readiness limits remain. Pending JavaScript/capture waits wake on cancellation. Deadline teardown attempts renderer termination on API29+. PixelCopy-owned bitmaps are not prematurely recycled.
- Added a generation-scoped native preview responsiveness probe with timeout/teardown, so automatic preview does not rely on a render job being started before detecting a stuck script.
- Removed HTML from Activity saved-state Bundles to avoid Binder-size failures with large imports; the existing private editor preferences retain restoration.
- Preserved native Persian error messages when exceptions are wrapped. Rendering choices, frame timestamps/counts, converter, clock and encoder calculations are unchanged.

| Verification | Actual result |
| --- | --- |
| `python3 build.py` | PASS, debug APK built |
| `python3 build.py --release` | PASS, non-debuggable R8/resource-shrunk unsigned release built |
| `npm run test:ui` | PASS: layouts, motion frames, bridge/download flows, import type/encoding/size rejection and valid imports above old 2 MiB limit; offline probes reached zero external TLS endpoints |
| `npm run test:clock` | PASS: modern CSS clock and legacy WebView fallback |
| `python3 tools/test_yuv.py` | PASS: all 72 byte-identical cases |
| `python3 tools/test_platform.py` | PASS: permission boundaries and copy/cancel regression cases |
| `python3 tools/test_input.py` | PASS: 23 production import/encoding/byte-limit/memory/deadline checks, including exact 20 MiB and multibyte overflow |
| `python3 tools/verify_phase2.py` | PASS: actual compiled debug/release component boundaries, network XML, provider roots, R8 and canonical HTML |
| `tools/test_requests.py` with supplied Android35 API jar and org.json 20240303 JVM jar | PASS: 159 actual production bridge/request setting/schema cases; no Android framework/codec calls |
| Phase 3 instrumentation APK | Compiled and packaged successfully; runtime checks unverified |
| Phase 3 native execution | BLOCKED: API30 emulator system process crashed with `Lost network stack`; after APK installation, `am instrument` repeatedly failed before test startup with `Can’t connect to activity manager`. No native PASS is claimed for this phase |
| Asset hashes / source audit | PASS: all locked hashes match; no credential findings |

See [Phase 3 limits](PHASE3_INPUT.md). These checks do not establish a total renderer/GPU memory quota, physical-device behavior or timely interruption of blocked platform calls. No production-signed APK was published and no Phase 4 changes were started.
