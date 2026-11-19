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
