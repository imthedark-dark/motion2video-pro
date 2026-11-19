# Phase 2 — Android boundaries and release shrinking

Branch: `security/phase-2`, based on Phase 1 commit `624e48b`. Published v1.4.1 APKs/tags are unchanged. Phase 3 is not started.

## Components, files and permissions

Only StudioActivity is exported as the launcher. MainActivity, RenderService and both merged providers are internal. AndroidX's merged ProfileInstallReceiver is explicitly made non-exported; automatic in-process profile installation remains available, while external adb benchmark broadcasts are not an app feature. Inspect merged APKs, not only the source manifest.

AndroidX Core 1.13.1 FileProvider replaces the custom provider. A read-only subclass permits only generated MP4/WebM names and rejects writes, delete/update/insert, query strings and invalid paths. Its authority and XML root name retain old `content://dev.motion2video.app.downloads/file/<name>` records. The only root is `Download/Motion2Video/`; there is no storage-root, cache-root or private-job-file mapping. Android 10+ continues using MediaStore content URIs. No external Intent uses file://; in-app legacy VideoView playback from its own private file path does not grant another app access. A new share button is Phase 6.

WRITE_EXTERNAL_STORAGE remains capped at API28 and is requested only on API26–28. POST_NOTIFICATIONS is requested on API33+. Denial does not cancel video encoding; Persian notices explain how to return to the app or retry a completed private export. UI notices do not unlock a running render. Backup stays disabled. The network-security XML forbids cleartext and trusts only system CAs; Phase 1 online consent still applies to imported content.

## Foreground-service time limits

API35+ starts with mediaProcessing; API29–34 uses dataSync; older supported versions use the two-argument foreground call. Both type permissions/types remain declared.

The timeout callback sets cancellation, publishes a Persian terminal error, cancels pending captures, releases its wake lock and requests foreground/service stop without joining a worker. The worker captures its immutable request, so shutdown cannot null it during saving/finally. Publication is rejected once the owning service stops or another instance takes over. Downloads copying checks cancellation between 64 KiB chunks and removes its own failed pending row/file. Abandoned private output and faststart/job files are cleaned on the next state recovery. This does not claim checkpoint/resume after process kill or a universal bound on an OS/filesystem stall.

## Debug/release build paths

Use a **full JDK 17** (including jlink/jmod), Python 3, SDK Platform 35 and Build Tools 35.0.0. The old manual SDK-only builder is replaced by a thin wrapper around Gradle because AndroidX merging and AGP resource shrinking must use one consistent build graph. The checked-in Gradle 8.9 wrapper validates its distribution SHA-256. Initial Gradle/Maven downloads require network access; this is separate from app runtime networking.

```sh
python3 build.py
python3 build.py --release
python3 tools/verify_phase2.py
```

Default output remains the locally signed contributor debug APK in dist/. Its keystore lives at ignored build/debug.jks or an existing development key specified by M2V_DEBUG_KEYSTORE. Gradle's standard debug defaults handle that key. The release output is `Motion2Video-1.4.1-Dark-release-unsigned.apk`: non-debuggable, R8-minified/obfuscated and resource-shrunk. Production signing is Phase 4; never publish the unsigned or development-signed QA APK as a production release. On managed machines the helper places Android/Gradle state under ignored build/ unless those locations are already configured. No proxy credentials or local trust-store configuration are in source.

Keep rules preserve required runtime annotations and MessagePort callback entry methods; manifest components are retained by AGP's generated rules. No blanket keep of renderer classes/fields is used. Instrumentation that reflects private fields must use the debug APK; release tests use public Android/View/WebView interfaces instead.

## Verification

```sh
npm run test:ui
npm run test:clock
python3 tools/test_yuv.py
python3 tools/test_platform.py
python3 tools/verify_phase2.py
```

The platform test covers Android permission boundaries, an exact-byte copy, cancellation before writing and cancellation at the next chunk boundary. The APK inspection resolves renamed XML resource paths from the compiled resource table, verifies all exported flags, FGS bits, read-grant/provider roots, backup/cleartext flags, release debugging and actual R8 mappings. The UI test includes permission denial without releasing the render lock.

### Device/emulator fixtures

Use a disposable test installation and matching development signatures; keep all keys/logs out of Git. `ReleaseSmokeTest` must target a locally QA-signed copy of the **minified non-debuggable** release, never the debug build. It uses SDK/UI surfaces rather than obfuscated private fields, renders a one-second 640×360/30fps H.264 MP4, waits for saved playback, reads the result through FileProvider, counts thirty samples and checks write rejection. `ServiceTimeoutTest` targets the debug APK and invokes the real app timeout handler during an active job. On API30 this is a manual callback test, not Android 15's actual quota scheduler.

```sh
python3 tests/build-instrumentation.py
# Install the appropriately signed app/test APKs first.
adb shell am instrument -w dev.motion2video.test/.ReleaseSmokeTest
# Replace the target app with the matching debug APK before this reflection-based fixture.
adb shell am instrument -w dev.motion2video.test/.ServiceTimeoutTest
adb shell am instrument -w dev.motion2video.test/.WebSecurityTest
```

Runtime evidence and limitations are recorded in AUDIT_REPORT.md. Physical devices, Android 8/9 permission UX, Android 13+ notification denial and Android 15 real six-hour quota enforcement require separate acceptance runs. Source manifest validation alone is not a device test.
