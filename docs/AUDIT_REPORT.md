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

## Phase 4 baseline (before implementation)

Verified at `eb534c0`: three signed APK binaries and SHA256SUMS are tracked in downloads/; verify_release.py and the release-assets workflow depend on those tracked bytes and raw tagged URLs. build.py delegates to Gradle and emits an unsigned minified release; no production signing inputs exist. Debug keystores are ignored, but downloads/*.apk is explicitly unignored. Both workflows use floating action tags; build permissions are read-only while release publishing grants contents:write workflow-wide. No Dependabot configuration, npm audit or OWASP dependency-check CI step exists. DEPENDENCIES.lock.json is a flat asset-to-SHA256 map without version/provenance metadata; no reusable hash verifier enforces coverage. GSAP custom-license terms, MIT library notices and OFL font notices already exist; current SECURITY.md documents phase boundaries but lacks a supported-version/reporting/response policy. Font versions were inspected from actual font name tables, not inferred from filenames. Phase 3 native acceptance remains blocked and must not be represented as completed by release automation.

### Phase 4 implementation and verification

- Removed the three tracked downloads APKs and checksum manifest from the current tree; historical Git tags/history and GitHub Release assets remain unchanged. Source audit now rejects APK/AAB binaries and private release-signing properties even if force-added. The upload workflow now defaults to read-only Release verification and preserves publication in a separate guarded draft-publishing job; no binaries are fetched from Git.
- Added explicit environment/local-properties release signing while preserving debug and unsigned builds. Packaging reads actual Gradle version/output metadata; local checksum generation/verification rejects changed bytes, inventory drift, unsafe/duplicate names and symlinks.
- Added weekly Dependabot for npm/Gradle/Actions, full upstream action SHAs, default-read-only tokens/non-persistent checkout credentials (least-privilege write only for explicit publishing), npm audit and isolated OWASP13.0.0 CI. Actual resolved native runtime artifacts are copied for scanning; scanner dependencies are isolated from AGP.
- Recorded all 36 unchanged asset hashes/sizes and 13 component version/license records; verifier checks font name-table versions, library version markers, canonical UI and embedded offline GSAP. Reviewed MIT/OFL/Apache notices and flagged GSAP historical/current custom-license concerns without claiming legal clearance.
- Rewrote SECURITY.md with version status, private-reporting setup status, response targets, threat boundaries and limitations. Added RELEASE.md and updated both READMEs/distribution/validation guidance.

| Verification | Actual result |
| --- | --- |
| Debug and unsigned minified release | PASS: both Gradle builds; default contributor key works; build.py forces signing OFF even with a global Gradle signing property; APK packaging and checksums |
| Missing signing inputs | PASS: explicit signed build fails closed |
| Environment-based release signing | PASS: temporary seven-day QA key outside Git; apksigner verifies v2 signature |
| Ignored local properties signing | PASS: assembleRelease with release environment inputs removed; private file removed afterward |
| Signed/unsigned equivalence | PASS: every non-META-INF ZIP entry identical; unsigned non-debuggable/R8/resource checks pass |
| Required UI / clock / YUV suites | PASS: UI/security probes, modern/legacy clock, all 72 byte-identical YUV cases |
| Platform / input / request schema regressions | PASS: permission/copy checks, 23 input/budget cases, 159 actual bridge/request host cases |
| Supply-chain negative tests | PASS: 17 corruption/inventory/symlink/traversal/duplicate/version/private-file/binary guards |
| Bundled inventory | PASS: 36 asset hashes and 13 version/license records; fonts and embedded GSAP checked |
| npm audit (includes development tools) | PASS: zero known vulnerabilities returned by the registry on 2026-11-19 |
| Workflows / Dependabot | PASS: YAML parse, full-SHA/least-privilege checks, actionlint1.7.12 (official archive checksum verified) |
| Candidate publication guard | PASS: signed QA verification only; wrong signer, wrong tag/file, debuggable and unsigned APKs rejected. No publishing mode was invoked |
| Historical v1.4.1 Release download | PASS: public Release asset SHA-256 and ZIP integrity; published bytes not replaced |
| Native runtime scan inputs | PASS: 20 actual resolved AAR/JAR runtime artifacts copied |
| OWASP Dependency-Check full scan | BLOCKED: task/configuration runs but NVD anonymous/missing-key access fails and CISA KEV feed returns403; no clean dependency scan is claimed. CI remains fail-closed |
| GitHub private reports | BLOCKED: GET verified disabled; enable request returned403; owner must enable in repository Code security settings |
| Native/physical acceptance | NOT RUN in Phase4; Phase3 runtime gaps remain. Signing/static checks do not establish device behavior |

No production key, password, token or new public APK was committed/published. Render/converter/clock/UI asset bytes are unchanged. Phase5 was not started. Feed access/NVD setup, private reporting activation and native acceptance remain gates before a hardened production release.

## Phase 5 baseline (before implementation)

Verified at `9bc7677`: first-party Android Java has no Log calls, printStackTrace, System.out/err or analytics/crash-reporting SDK. Production Gradle declares AndroidX Core only; the resolved runtime graph from Phase4 contains AndroidX/Kotlin/Guava/annotation artifacts, not an app telemetry client. Network access is through WebView: trusted UI requests are intercepted locally and imported resources are blocked by default, but explicit online consent allows HTTPS resources/connections. Safe Browsing stays enabled and belongs to platform behavior. The standalone HTML requests Tailwind/GSAP/Google Fonts and includes two preconnect hints. No metrics opt-out is declared in the manifest. WebViews have no console-suppression handler; StudioActivity explicitly installs a default WebChromeClient. Raw exception getMessage values reach native UI/notifications and persisted render errors/warnings, potentially including provider paths or text. Private preferences retain editor HTML, render status and download metadata; job JSON/output copies use private cache, and exports remain in public Downloads. The optional desktop FFmpeg tool forwards default FFmpeg diagnostics and raises CalledProcessError with full arguments on failure. A blanket claim of zero off-device traffic in every mode would be false; privacy documentation must distinguish local rendering, opt-in remote content, standalone CDNs and platform/provider behavior.

### Phase 5 implementation and verification

- Centralized a console-consuming WebChromeClient for all three native WebViews; removed Studio's default-client override. No runtime logger, analytics or crash reporter was added. Release remote debugging remains disabled; debug builds retain explicit developer inspection.
- Added a reviewed exact public-error catalogue and fixed permission/file/memory/timeout fallbacks. Unknown provider/codec text, paths, HTML and credential-shaped URLs are not forwarded into new errors/notifications. Cause traversal is bounded/cycle-safe. Legacy error/warning display is filtered without a destructive/racing storage migration; intentional file metadata is retained.
- Requested WebView metrics opt-out through application metadata, verified against official Android documentation and actual compiled APKs. Safe Browsing stays enabled. Removed only the two speculative studio font-preconnect hints; CDN resources required by standalone HTML stay. Only studio.html's approved hash/size changed; other35 bundled files are unchanged.
- Optional desktop FFmpeg encoding arguments are unchanged. Default diagnostic streams are suppressed and failure output is fixed; --debug explicitly enables local diagnostics. A real export/decode regression was checked as well as fake-process diagnostic tests.
- Added bilingual PRIVACY.md covering default local rendering, retained private/cache/Downloads data, deletion, opt-in network content, standalone CDN and platform/provider boundaries. A blanket all-device/all-mode no-traffic claim is explicitly not made.

| Verification | Actual result |
| --- | --- |
| Debug / minified unsigned release | PASS: both builds; non-debuggable/R8/resource/merged-component regressions pass |
| Compiled privacy metadata | PASS: both APKs contain WebView.MetricsOptOut=true; R8 retains both console callback overrides |
| Privacy error tests | PASS: 22 actual production filtering cases, including paths/HTML/URL credentials, oversized text, cyclic/throwing causes and useful fixed messages |
| Desktop diagnostic contract | PASS: default success/failure suppress fixture output; explicit --debug shows it; full encoding/audio argument vector unchanged |
| Real desktop H.264 export | PASS: 640×360, 30 frames at30fps, YUV420P/BT.709 limited; full FFmpeg decode succeeds |
| Required UI/security suite | PASS: layouts/motion/download flows; actual Java CSP blocks all default TLS probes and permits a synthetic POST body only after online opt-in |
| Clock / YUV | PASS: modern/legacy clock and all72 byte-identical converter cases |
| Platform / input / schema | PASS: permission/copy regressions,23 input/budget cases and159 production bridge/request host cases |
| Asset / supply-chain regressions | PASS: all36 assets/13 component records and17 negative supply-chain guards; no silent dependency update |
| Source privacy / credential audit | PASS: no first-party diagnostic sink/raw exception bypass/known telemetry client; source scan has zero findings |
| Workflow validation | PASS: actionlint; new privacy checks added with existing pinned actions/least privileges unchanged |
| Native privacy fixture | Compiled/packaged only. Not executed; no native logcat/packet-capture/provider-behavior PASS is claimed |

Native/physical acceptance and the previously recorded Phase4 advisory-feed/private-reporting gates are not resolved by these changes. Android/Chromium/vendor diagnostics, Safe Browsing, selected providers and explicitly allowed imported/standalone/external-tool traffic remain separate boundaries. No production key or new Release APK was published. Phase6 was not started.
