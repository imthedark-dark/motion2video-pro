# Android studio startup

## Reproduction and diagnosis

Phase 7 at `34c9956` entered `StudioActivity.onCreate`, ran the entire legacy `MainActivity.onCreate`, detached its preview WebView, created the trusted studio WebView and replaced the content view. Android could only draw the activity after that synchronous call returned. The earlier emulator ANR reported that StudioActivity had no focused window. In the instrumented reproduction, `am start -W` timed out and the activity eventually drew. That run did not produce a new ANR stack trace.

We used the same Android 11 x86_64 AVD with WebView 83.0.4103.120, two emulated CPUs, 2 GiB of guest RAM, SwiftShader and software CPU emulation. The app package was cleared **only on the disposable test device** for each empty-data sample. The emulator and Android filesystem caches remained running across installs. The baseline ran earlier in the guest's boot. These are individual observations, not a device performance distribution.

| Milestone or interval (milliseconds) | Phase 7, empty app data | Fixed, empty app data |
|---|---:|---:|
| Process start to activity entry marker | 2,841 | 1,066 |
| Process start to first app draw | 15,282 | 2,414 |
| Provider class loading on worker | Included in first constructor | 905 |
| Worker preferences, preview assets and restore slot after provider load | — | 107 |
| First WebView constructor on main | 6,539 (preview) | 1,860 (studio) |
| Second WebView | 211 (studio constructor) | 156 (preview constructor and security configuration) |
| Process start to studio navigation queued | 13,168 | 6,944 |
| Process start to native bridge hello | 94,434 | 38,770 |
| Process start to JS confirming native controls enabled | Not instrumented | 39,665 |
| Largest main-queue delay through enabled controls | Not instrumented | 2,075 |

Temporary baseline probes bracketed the two constructors, preview security setup, editor setup, `loadUrl`, first draw and bridge hello on the otherwise unchanged Phase 7 path. Clock origin was `Process.getStartElapsedRealtime()`. The old draw marker ran in `onDraw`. The fixed one runs just after the first draw traversal returns, before submitting the worker. Neither measures physical pixels reaching a display. The fixed empty-data launch also reported `LaunchState: COLD`, `Status: ok` and Android `TotalTime: 3731` ms.

**Largest measured pre-draw app operation:** the old preview WebView constructor occupied the main thread for 6,539 ms. The same call stack then spent 2,257 ms from the end of preview setup through building the remaining legacy UI, including 270 ms around constructing and populating the hidden code editor. That editor evaluated a preset asset read even when a preferences value existed. The second WebView added another 211 ms before navigation. This explains how the app could miss a first-window input deadline on this emulator. A trace of the original phone or emulator incident would be needed for a definitive historical attribution.

**Largest elapsed interval to usability:** studio navigation to bridge hello took 81,266 ms before and 31,826 ms after. This interval includes WebView loading, renderer work and process scheduling. In the fixed sample the app main thread accumulated 5,002 ms CPU during it. Assigning the entire interval to a blocked Java main thread would be incorrect. The first fixed WebView constructor still took 1,057 ms of main-thread CPU (1,860 ms wall). Native controls becoming available also does not guarantee the user's animation has produced its first preview frame.

A second force-stopped launch of the final build retained app data. Android reported a cold launch in 3,914 ms. The first draw was at 2,730 ms, the bridge connected at 29,562 ms, and the studio controls were enabled at 31,050 ms. Its longest queue delay was 1,717 ms. Both final launches obtained a window and Android reported no ANR since boot. Both exceeded the 500 ms queue budget.

## Startup work inventory

| Work | Execution and boundary |
|---|---|
| System startup | Framework and manifest providers run before activity creation. The process-relative clock includes this time. There is no custom Application startup routine. |
| First window | Main displays one Persian loading label. After its first draw, a worker requests the public provider class loader on API 28+, waits for preview preferences, reads the 1,060-byte font CSS and 3,259-byte clock script, and validates/restores a pending private request if one exists. API 26–27 have no public class-loader prewarm here. |
| Studio and preview | Both WebViews are constructed on the UI thread. Setup is split across main-queue turns. Each receives its original restrictions before loading any content. The trusted studio receives the origin-targeted port. Its HTML (174,199 bytes) and subresources are streamed by WebView request-interception threads. The legacy activity still constructs its own controls when explicitly launched. |
| Bundled asset integrity | All 38 asset digests are verified against the lockfile by the host supply-chain check. No branch iterates over 38 files at activity launch. Packaged assets come from the installed APK. The original host checks and local asset allowlists still apply. |
| Downloaded fonts | The first font demand goes through FontWorkflow's worker. Bundled-only requests return before opening the private cache. Matching cache entries are SHA-256 checked during scope creation and verified on read by the local request-serving path. Corrupt entries are rejected before serving. No eager whole-cache verification or asset copying was found. |
| Preferences and JSON | Studio no longer inflates a hidden EditText or restores an extra copy of the previous HTML into it. The trusted HTML reads its own local storage. The bridge still validates commands on main. The last render's status JSON is consulted at connection. Download history parsing runs on the worker. A restored permission request can contain a large HTML job and is read and validated on the worker before use. |
| Codecs and notifications | No MediaCodec enumeration or notification channel setup on an empty launch. Opening the studio capability dialog runs its query on the worker. Per-render codec selection still uses the existing capability worker. The foreground service creates its notification channel when started. |
| Language and FAQ | Persian loading text is native. The FAQ builds its dialogs on demand. There is no i18n catalog load at launch. |
| Rendering | The existing clock, capture, encoder, frame budget and YUV conversion remain in place. The studio passes validated settings directly instead of round-tripping them through hidden legacy controls. |

## Verification

Both debug and unsigned release assembled. Android lint completed with zero errors and 28 warnings. The native startup/CSS/bridge, WebView security and privacy fixtures passed on API 30. Host and Chromium checks passed for the UI, clock, WebView restrictions, imports, font cache integrity and consent, the 38 bundled hashes, the 246 bridge/request schema cases, and all 72 byte-identical YUV cases. A synthetic one-second export on the emulator produced a decodable 640 × 360 H.264 MP4 with 30 frames. The per-frame checksums of all 30 decoded frames matched the saved Phase 7 ApkStart output for the same input. The latency budget and physical-device checks are reported separately below.

## Repeating the measurement

Install the debug APK on a test device, unlock it and run the following from the repository. The script force-stops this app, preserves its data, launches it, scopes logs to the new process and waits for the trusted studio to report enabled controls.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 tools/test_startup.py
```

Use `ANDROID_SERIAL` or pass an ADB wrapper with `--adb` to select a device. The debug logger emits fixed milestones, elapsed milliseconds, a main-thread flag, thread CPU milliseconds and maximum main-queue lateness. The 50 ms Handler probe measures delays beyond its due time. CPU contention, scheduler delays and GC also contribute. It starts at activity entry, so it cannot diagnose pre-activity stalls. Release builds do not sample or emit these logs.

The default 500 ms budget is strict. The empty-data fixed run failed it (`FAIL: main queue late by 2075 ms (budget 500 ms)`) even though Android confirmed the cold launch and the native controls became usable. Software CPU emulation has substantially different scheduling and graphics costs from a Xiaomi handset. Keep this failure visible when comparing future builds.

## Xiaomi verification

Record the Android and WebView versions and battery mode. Test a fresh install on a test profile and several force-stopped launches with retained data. Compare first-window time, controls-ready time, the 500 ms budget and any system ANR. Include a populated font cache and a saved large animation. Leave or rotate during the loading label, return with a render in progress, and verify a short MP4 export. Collect only the startup milestones when sharing results.
