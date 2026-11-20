# Phase 3 — input and render resource limits

Only HTML/HTM documents are imported; no ZIP, folder extraction or symlink traversal exists. Both Android activities use ContentResolver document URIs, provider display name/MIME/size checks, a streaming 20 MiB byte limit (including when the provider lies about size), and strict UTF-8 decoding. UTF-8 BOM is accepted; malformed bytes, NUL and unpaired surrogates are rejected. Convert legacy encodings to UTF-8 before import. Extension is mandatory. HTML, plain text, unspecified and generic octet-stream MIME are accepted because document providers use these for HTML; explicit unrelated MIME types are rejected. Names are never used as output paths. Imports run off the Android UI thread; only one native import worker is admitted at a time.

The browser picker mirrors these checks, with TextDecoder fatal mode. Pasted content and native command/request boundaries also enforce the UTF-8 size bound. JSON envelope limits account for six-character escapes; the HTML payload retains its independent 20 MiB limit. Activity saved-state Bundles no longer carry HTML; recreation uses the existing private editor preferences. Service Intents continue to carry only the request UUID. This limit is an admission ceiling, not a guarantee a 20 MiB script will fit a small phone's memory.

Existing output choices remain: 1–300 seconds; 30/60 FPS; 640×360, 1280×720, 1920×1080 or 1080×1920; 12/16/20 Mbps; existing listed codecs. Native command schemas, private render requests and browser controls reject invalid types, non-finite numbers, fractions for integer fields, overflow/coercion, and values outside the supported choices. Device codec support is still checked before rendering. No timestamps, frame count, dimensions, bitrate defaults or YUV conversion math were changed.

## Watchdog

The native preview has a generation-scoped responsiveness probe: a tiny JavaScript ping every 45 seconds, with a 45-second response deadline. It suspends while the studio render job owns the stage and is cleared on replacement/destruction. An unresponsive preview is detached/destroyed (renderer termination attempted on API29+) and a native Persian dialog asks the user to reopen and fix heavy/infinite code. This does not change animation time or seek any frames.

- Existing operation/frame wait: 45 seconds; readiness limit: 120 seconds. Persian timeout messages explain the failure.
- Render-engine wall time (preparation through encoding/finalization): two hours, measured with elapsed realtime, independent of video timestamps. Subsequent Downloads copying uses the service cancellation checks.
- Before allocating frames: reserve 24 bytes per pixel plus 16 MiB for the two-buffer pipeline and temporary resources. Compare current Java heap use plus this reserve against the lower of 256 MiB and 85% of the VM heap maximum.
- While rendering: poll Java heap and Android's system `lowMemory` flag once a second. Cancel if the heap ceiling is exceeded, system memory is low, or no pipeline progress occurs for 120 seconds. No automatic quality reduction or dropped frames.
- User cancel wakes pending readiness JavaScript and capture futures. On API29+, a cancellation probes renderer responsiveness for up to two seconds before teardown and terminates an unresponsive renderer; a responsive renderer closes normally. Deadline aborts tear down the stage; API 29+ attempts termination of its WebView renderer. Such processes may be shared by app WebViews; all app WebViews have renderer-gone handlers. API 26–28 supports teardown, but does not expose this renderer-termination API.
- Failure deletes partial output, closes the stage and encoder, and recycles bitmaps only after the encoder has stopped using them and no PixelCopy still owns them. An unresponsive platform codec can delay cleanup; no unsafe recycling of buffers still in use.
- The non-exported legacy Activity has the shared import/memory preflight, per-frame heap/overall-time checks and its existing 15-second frame/30-second readiness waits. The foreground-service path is the shipped studio render path.

## Limits of containment

A Java heap budget is not a total process/GPU/WebView renderer memory quota. Android can kill a process before the next poll. A blocked native codec, system API or main-thread drawing operation cannot be forcibly interrupted safely. The OS sandbox and Phase 1 offline policy remain necessary. Arbitrary imported JavaScript is not guaranteed safe. Browser-only exports depend on browser APIs and have no Android service watchdog.

## Reproduce

```sh
npm run test:ui
npm run test:clock
python3 tools/test_yuv.py
python3 tools/test_platform.py
python3 tools/test_input.py
python3 build.py
python3 build.py --release
python3 tools/verify_phase2.py
```

`dev.motion2video.test/.InputProtectionTest` tests actual native closed setting schemas, an infinite script deadline, cancellation and partial-file cleanup on a debug build. Build it with `python3 tests/build-instrumentation.py` and run `am instrument -w dev.motion2video.test/.InputProtectionTest` on an Android device/emulator. This fixture deliberately hangs imported JavaScript; run on a test device.

### Verification outcome (2026-11-19)

Debug and minified unsigned release builds and all required host suites passed. The production import/budget policies passed 23 JVM checks. The production bridge and request classes additionally passed 159 schema/unchanged-setting checks using a supplied org.json 20240303 JVM runtime and Android35 API jar for compile-time framework types:

```sh
python3 tools/test_requests.py --android-jar "$ANDROID_SDK_ROOT/platforms/android-35/android.jar" --json-jar /path/to/trusted/json-20240303.jar
```

This optional harness does not bundle or download a JSON dependency and does not invoke Android framework/codec APIs; it does not replace native acceptance.

The new instrumentation APK compiled and installed, but the API30 software emulator's system process repeatedly failed (`Lost network stack`); instrumentation invocations failed before test startup because ActivityManager was unavailable. Phase 3 infinite-script/cancel, preview responsiveness, memory-pressure, Activity recreation and final release export runtime checks therefore remain **unverified**. Previous Phase 2 native successes are historical evidence only. Physical phones and API26–28 teardown behavior also remain unverified. Native fixtures must pass on a working test device before production release.

### Pending native acceptance cases

On a working test device, run the instrumentation fixture above and the existing minified ReleaseSmokeTest. Also check automatic preview with an infinite loop (native error/teardown within two probe intervals), replacing a preview before its deadline (no stale teardown), foreground/background render transitions (preview probe suspends), cancel during GPU capture, memory pressure on small-heap devices, and Activity recreation with an import above 2 MiB (no large saved-state Binder transaction). Preserve output frame count/dimensions and check partial files are removed. These cases have not been marked passed here.
