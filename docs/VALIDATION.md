# Reproducible validation and device status

The application is an experimental Android renderer. This page records evidence, not a promise that all Android devices, codecs or animations work.

## Recorded native result

Tests ran on 2026-11-17 using an **Android 11 / API 30 software emulator**, not a physical phone. A one-second, 640×360, 30fps H.264 job completed with thirty frames, YUV420P, BT.709 primaries/matrix/transfer and limited range. `moov` precedes `mdat`. Full FFmpeg decoding succeeded. Persian joining, Arabic diacritics and English text were visually reviewed in decoded frames. A moving square changes position from first to last frame.

The service-only test pressed Home during rendering and verified matching output bytes through MediaStore.Downloads. A UI-driven run continued after `finishAndRemoveTask()`. The dedicated recovery/cancel test passed playable-result/path/settings restoration, immediate cancellation, notification cancellation, removal of partial output and unchanged Downloads history. Native precompiled-CSS startup and bridge connection also passed.

Evidence: [`verified-background-360p.mp4`](../tests/fixtures/verified-background-360p.mp4), [`background-validation.json`](../tests/fixtures/background-validation.json), and the instrumentation sources. Raw device logs, private paths and identifiers are not published.

| Platform / path | Evidence | Status |
| --- | --- | --- |
| Android 11 / API 30 emulator, H.264 software-capture path, 360p30 | Background, MediaStore, Activity removal, restore, cancel, decoded MP4 | Tested |
| Physical Android phones, any manufacturer | No reported device acceptance run | **Not tested; no certified phone list** |
| Android 8/9 storage permission | Implemented, no device acceptance run | Not tested |
| Android 13+ notification prompt | Implemented, no device acceptance run | Not tested |
| Android 15+ foreground-service timeout | Implemented, no device acceptance run | Not tested |
| 1080p60 / HEVC / AV1 / VP8 / VP9 combinations | Capability checks depend on device | Not acceptance-tested |
| WebGL / experimental PixelCopy path | Experimental | Not acceptance-tested |
| Chromium mobile/desktop layout and native-message/download simulation | `tests/studio-ui.cjs` | Passed; not a physical Android test |
| Animation clock: Web Animations and legacy CSS fallback | `tests/motion-clock.cjs` | Passed |
| 72 YUV sizes/strides/offsets/shared-plane cases | `tests/java/Yuv420ConverterTest.java` | Byte-identical to previous converter |

No microphone/audio track, ProRes/MOV alpha output or automatic recovery after force stop is implemented. Home/Activity closure is different from force stop or process termination. Battery policies can interrupt work. The desktop JVM conversion benchmark in [PERFORMANCE.md](PERFORMANCE.md) is not a whole-export/device speed claim.

Version 1.4.1 packages the same rendering implementation with license notices and a version increment. Its APK/asset integrity, signature and source build are checked separately. Comparing the APKs found byte-identical `classes.dex` and `assets/studio.html`, with the new license files present; the recorded native rendering results above are from v1.4.0, not a new device acceptance run of v1.4.1.

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
