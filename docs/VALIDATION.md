# Reproducible validation and device status

The application is an experimental Android renderer. This page records evidence, not a promise that all Android devices, codecs or animations work.

## Recorded native result

Tests ran on 2026-11-17 using an **Android 11 / API 30 software emulator**, not a physical phone. A one-second, 640×360, 30fps H.264 job completed with thirty frames, YUV420P, BT.709 primaries/matrix/transfer and limited range. `moov` precedes `mdat`. Full FFmpeg decoding succeeded. Persian joining, Arabic diacritics and English text were visually reviewed in decoded frames. A moving square changes position from first to last frame.

The service-only test pressed Home during rendering and verified matching output bytes through MediaStore.Downloads. A UI-driven run continued after `finishAndRemoveTask()`. The dedicated recovery/cancel test passed playable-result/path/settings restoration, immediate cancellation, notification cancellation, removal of partial output and unchanged Downloads history. Native precompiled-CSS startup and bridge connection also passed.

Evidence: [`verified-background-360p.mp4`](../tests/fixtures/verified-background-360p.mp4), [`background-validation.json`](../tests/fixtures/background-validation.json), and the instrumentation sources. Raw device logs, private paths and identifiers are not published.

| Platform / path | Evidence | Status |
| --- | --- | --- |
| Android 11 / API 30 emulator, H.264 software-capture path, 360p30 | Background, MediaStore, Activity removal, restore, cancel, decoded MP4 | Tested |
| Physical Android phones, any manufacturer | No complete version-by-version acceptance run | Partial owner-reported Xiaomi font checks; no certified phone list |
| Android 8/9 storage permission | Implemented, no device acceptance run | Not tested |
| Android 13+ notification prompt | Implemented, no device acceptance run | Not tested |
| Android 15+ foreground-service timeout | Implemented, no device acceptance run | Not tested |
| 1080p60 / HEVC / AV1 / VP8 / VP9 combinations | Capability checks depend on device | Not acceptance-tested |
| WebGL / experimental PixelCopy path | Experimental | Not acceptance-tested |
| Chromium mobile/desktop layout and native-message/download simulation | `tests/studio-ui.cjs` | Passed; not a physical Android test |
| Animation clock: Web Animations and legacy CSS fallback | `tests/motion-clock.cjs` | Passed |
| 72 YUV sizes/strides/offsets/shared-plane cases | `tests/java/Yuv420ConverterTest.java` | Byte-identical to previous converter |

The historical release tested above did not include an audio track. Current source supports an optional attached audio file. No microphone capture, ProRes/MOV alpha output or automatic recovery after force stop is implemented. Home/Activity closure is different from force stop or process termination. Battery policies can interrupt work. The desktop JVM conversion benchmark in [PERFORMANCE.md](PERFORMANCE.md) is not a whole-export/device speed claim.

Version 1.4.1 packages the same rendering implementation with license notices and a version increment. Its APK/asset integrity, signature and source build are checked separately. Comparing the APKs found byte-identical `classes.dex` and `assets/studio.html`, with the new license files present; the recorded native rendering results above are from v1.4.0, not a new device acceptance run of v1.4.1.

## Subsequent Phase 2 emulator evidence

On 2026-11-19, the non-debuggable R8/resource-shrunk release completed the UI/native bridge, 360p30 H.264 render, automatic save and playable-result smoke test on the Android 11/API30 software emulator. FileProvider read access succeeded and write access was rejected. FFprobe and full FFmpeg decode confirmed 30 frames, one second, 640×360, YUV420P, BT.709 limited, with moov before mdat. See `tests/fixtures/verified-phase2-release-360p.mp4` and `phase2-release-validation.json`.

Debug fixtures also passed actual WebView/worker/bridge checks, manual invocation of the service timeout handler during an active render (stopped service, deleted partial output, retained terminal error), and actual service WebView renderer termination with cleanup. The timeout invocation on API30 is not proof of Android 15's real six-hour quota scheduling. No physical-phone acceptance was added. [Phase 2 instructions](PHASE2_ANDROID.md).

## Repeat source and browser checks

Prerequisites: JDK 17+, Python 3, Node/npm, SDK Platform 35 / Build Tools 35.0.0 for APK builds; Chromium for browser checks.

```sh
python3 tools/audit_source.py
python3 tools/test_yuv.py
python3 tools/verify_release.py
npm ci
npx playwright-core install chromium
npm run test:ui
npm run test:clock
python3 build.py
```

Set `ANDROID_SDK_ROOT` and, for system Chromium, `CHROMIUM_PATH`. Test output is generated locally and ignored by Git. The [v1.4 source CI run](https://github.com/imthedark-dark/motion2video-pro/actions/runs/44077572540) independently built the APK and passed the source audit/YUV check; it did not perform physical-phone testing.

## Repeat native tests on a test device/emulator

Use an expendable test install. These tests create real files in Downloads, alter the app's saved state and use UI automation. Build app and test APK with the same local signing key. Installing a new local debug build over a published APK usually fails because the keys differ; use a separate test device/profile or uninstall the old test app after saving needed exports.

```sh
python3 build.py
python3 tests/build-instrumentation.py
adb install -r dist/Motion2Video-1.4.1-Dark.apk
adb install -r tests/instrumentation/build/signed.apk
adb shell am instrument -w dev.motion2video.test/.NativeStartupTest
adb shell am instrument -w dev.motion2video.test/.BackgroundRenderTest
adb shell am instrument -w dev.motion2video.test/.RestoreCancelTest
```

Each must return `result=PASS`. `RestoreCancelTest` requires the completed job from `BackgroundRenderTest`; its final state is cancelled. For an additional UI-driven Activity-removal/restore test, run `.BackgroundUiTest`; it performs a fresh export. On Android 13+ grant notification permission to test notification visibility. Record permission-denial behavior separately. Some software emulators are extremely slow; do not use their elapsed render time as a phone performance benchmark.

## Independently inspect the published sample

```sh
ffprobe -v error -count_frames -select_streams v:0 \
  -show_entries stream=codec_name,width,height,pix_fmt,color_range,color_space,color_transfer,color_primaries,r_frame_rate,avg_frame_rate,nb_read_frames \
  -show_entries format=duration,size -of json tests/fixtures/verified-background-360p.mp4
ffmpeg -v error -i tests/fixtures/verified-background-360p.mp4 -f null -
python3 tools/verify_release.py --remote
```

Compare the probe output with `background-validation.json`. Remote verification checks actual public tagged APK bytes, not only the presence of a download link. It does not establish a security certification or replace APK signature verification.

## Add a physical-device report

Open an issue with model, Android/API version, WebView version, app tag/SHA-256, codec/settings, test animation, background/notification steps, resulting frame count/duration and pass/fail evidence. Remove personal data. Only add a device to the tested list after a reproducible report; do not infer support from an SDK minimum or a codec name.

## Phase 3 (2026-11-19)

Debug and R8/resource-shrunk unsigned release builds passed. UI/security probes, clock, all 72 byte-identical YUV cases and platform/copy regressions passed. Production import/memory/deadline policies passed 23 host cases; bridge/request sources passed 159 host schema/output-setting cases using the external JVM JSON test runtime. Compiled APK component/resource/R8 checks and asset hashes also passed.

Phase 3 instrumentation compiled and installed but could not execute reliably: the API30 emulator's Android system process suffered network-stack crashes and ActivityManager remained unavailable at instrumentation startup. No new native or physical-device PASS is claimed. In particular, malicious-script termination/cancellation, preview timeout teardown, memory-pressure cancellation, large-import Activity recreation and the final minified APK's actual export remain acceptance gaps. See [Phase 3 reproduction and caveats](PHASE3_INPUT.md).

## Phase 4 (2026-11-19)

Debug and unsigned minified builds passed. Explicit signing without credentials fails; environment and ignored-local-properties signing both built successfully using a newly generated seven-day QA key outside Git. Apksigner verified the QA signature; all non-META-INF entries matched the restored unsigned release. Private properties and the QA distributable were removed from the checkout. No production identity or public APK was changed.

UI/clock/security probes, all72 YUV byte-identical cases, platform, 23 input/budget and159 request-schema host checks passed. Asset verification covers36 unchanged files/13 component records;17 negative supply-chain guards passed. Npm audit returned zero known vulnerabilities. Workflow YAML/SHA/permission checks and actionlint1.7.12 passed. Existing v1.4.1 Release download checksum/ZIP checks passed. Native runtime scan inputs contain20 resolved artifacts.

OWASP's full analysis remains blocked by NVD missing/anonymous-key access and CISA403 feed responses; this is not a clean vulnerability result. Private vulnerability reporting remains disabled after integration403. No new native/physical test PASS is claimed; Phase3 gaps carry forward. See [release process and gates](RELEASE.md).

Publication guard verification passed with a throwaway QA candidate; wrong signer/tag, debuggable and unsigned APK cases were rejected. This only tests the local verifier. Guarded GitHub draft publishing was not invoked and remains gated on a green tagged-source audit and real native acceptance; it does not itself provide reproducible-build provenance.

## Phase 5 (2026-11-19)

Debug and minified unsigned builds pass; actual APK metadata requests WebView metrics opt-out and R8 retains console callback overrides. Host tests pass: UI/security (zero default TLS hits and positive consented synthetic POST), clock, all72 byte-identical YUV cases, platform,23 input/budget,159 request schema,22 public-error privacy cases and17 supply-chain guards. Desktop diagnostic success/failure/default/debug cases pass with identical encoding/audio arguments. A real desktop H.264640×36030fps export has30 frames, YUV420P/BT.709 limited and decodes fully. The studio preconnect removal is the only bundled byte change; its reviewed hash/size is updated.

Native PrivacyTest compiled and packaged but was not executed. Full native logcat/network/provider/Safe Browsing behavior and physical-phone acceptance remain unverified; source/host TLS probes are not a device-wide packet capture. Existing Phase3/4 release gates remain separate. See [privacy evidence and manual checks](PHASE5_PRIVACY.md) and [bilingual policy](../PRIVACY.md).

Phase 6 audio acceptance (pending physical device): run dev.motion2video.test.AudioExportTest on debug API29+; manually import MP3/AAC/PCM16 WAV/M4A on API26–35; verify clap/lip-sync at start/middle/end, trim/fade amplitude, mono/stereo and 44.1→48k conversion, short-audio padding, silent full-range byte regression, cancellation during decode/AAC/remux, low disk/memory and corrupted/header-only/large provider files. Verify release UI picker separately because debug reflection fixtures do not target R8 class names.

Phase 6 queue acceptance (pending physical devices): fresh test install, run dev.motion2video.test.QueueExportTest (API29+, debug). Manually test API26–28 storage-denied fallback, notification-denied API33+, multi-select batch bounds, different per-batch settings/audio, current and waiting cancellation, failure then valid job, process death/force-stop→paused recovery, no old online consent on Resume, missing-cache/source/audio, old preview eviction with public Downloads preserved, Android15 service quota and teardown gate. Verify no second encoder overlaps cleanup and verify original default 1280×720/60/16Mbps/full-range silent output. Do not run destructive test setup against a production install.

## Phase 9 Android phone checks

- Import the original motion fixture offline and a version referencing an unbundled Google Font. Verify the font summary and see that a bare font-family only gives a status. Reject the consent once and check that the fallback plays without network requests.
- On a test phone with internet available, approve the missing font. Check status updates and playback, then enable airplane mode and confirm that a fresh preview and export both use the saved face. Verify that the imported HTML's online-resources switch stayed off.
- Cancel a throttled download and try a disconnected device. Verify fallback playback, responsive controls and no corrupt cache entry. Repeat with the feature switched off.
- Remember an approved family, confirm a new family still asks, and confirm that an automatic draft reload does not start a download. Delete the family in Fonts and check that the remembered approval is removed.
- Try clearing the cache during preview and during a long export or queue job. Verify that an active render is protected, the settings state updates after deletion, and subsequent previews fall back. Check scope isolation, any differences in installed-font match and minified builds on API 26–35. Compare a device capture of Google-bound requests against [FONTS.md](FONTS.md).

## Physical-device matrix

Use a test profile, synthetic HTML/audio and an APK from the exact branch/commit. Record the APK SHA-256, Android/API, model, WebView provider/version and codec settings. Keep personal device identifiers out of shared notes. For each group record pass, fail (with step), not run, or not applicable.

| OS (API) | Model / WebView / APK SHA | Install / silent / range | Audio / sync | Queue / lifecycle | Permissions / share / storage | Fonts / network | Accessibility / private form |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 8.0 / 8.1 (26/27) | | | | | | | |
| 9 (28) | | | | | | | |
| 10 (29) | | | | | | | |
| 11 (30) | | | | | | | |
| 12 / 12L (31/32) | | | | | | | |
| 13 (33) | | | | | | | |
| 14 (34) | | | | | | | |
| 15 (35) | | | | | | | |

1. Install and open the app. Render a known short animation with default settings. Verify dimensions, fps, duration and silence with a player or ffprobe. Export a start/end range and inspect the first and last frames.
2. Attach a synthetic clap track with a visible marker. Check the beginning, middle and end for A/V drift. Repeat with trim and a fade, then detach and confirm silent export.
3. Queue three files. Cancel one waiting and one active job; verify the next valid job runs. Press Home and return, rotate during preview and export, then separately force stop the test installation. Reopen and check the queue status, explicit resume and absence of partial outputs.
4. Deny storage on Android 8/9 and retry saving after granting it. On Android 10+ check Downloads without legacy permission. On 13+ deny notifications and check in-app progress/cancel. Test sharing a completed output, low free space on a disposable test volume, and OEM battery limits on a background job.
5. Run the Phase 9 font checks above with first consent, cancel, offline reuse after reopening, delete one and clear all. On a controlled network observe destinations and timing. With online resources off, imported code should make no external requests. With explicit font consent look for the native Google stylesheet and font hosts. Distinguish system/WebView traffic; do not submit raw captures.
6. Set Android text size to 200% where supported, enable TalkBack and disable animations. Navigate the studio, editor, queue, FAQ, consent and Fonts settings by swipe. Verify spoken label, focus order/return, scroll reachability, status announcements, no clipped text, and all actions reachable in portrait and landscape. Confirm the animation itself still moves. Open Troubleshooting → Report a problem → private form and verify the GitHub repository and private advisory form. Submit nothing during testing.

Collect minimal diagnostics on a test device (use the debug build for the app's diagnostic tag):

```sh
adb shell getprop ro.build.version.sdk
adb shell settings get system font_scale
adb logcat -d -s Motion2VideoWebView:D '*:S'
```

Note the WebView version from About, renderer crash/kill category and CSP category if present. Redact identifiers and share only the relevant reviewed lines. Do not attach private animations, paths, account details, tokens, full logcat or packet captures.
