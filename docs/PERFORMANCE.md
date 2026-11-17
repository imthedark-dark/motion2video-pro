# Background rendering and performance — 1.4.0

## Rendering lifetime

The foreground service owns a private virtual display and Presentation WebView. It does not use the Activity window, screen capture, or an overlay. Notification progress, cancellation and a partial CPU wake lock belong to the service. Android 15+ uses the mediaProcessing service type; older supported versions use dataSync where a type is required.

Closing/removing the Activity leaves the job running. The studio reconnects to the service state when reopened. Completed output is saved with DownloadRepository; if public saving fails, the completed private output can be saved again from the UI. Android 8/9 storage permission is requested before starting. Android 13+ notification permission controls notification visibility, not the encoding format.

Force stop or process termination ends a render. No automatic checkpoint/resume of arbitrary HTML is claimed. The next app launch marks an interrupted job and removes its partial private output. OEM battery management and Android foreground-service time limits still apply.

## Faster path without reducing output settings

- Native studio utilities are precompiled from the bundled Tailwind 3.4.17 build, avoiding runtime PostCSS compilation when opening/reopening the Android UI. The standalone browser HTML retains its CDN tag.
- Two reusable Bitmaps overlap WebView capture and a single serialized encoder.
- Frame-ready MessagePort acknowledgements and a WebView visual-state callback replace polling and extra Activity-vsync waits.
- The encoder drains available packets without a forced 10ms wait after every input frame; EOS still waits for completion.
- Cached YUV plane metadata and bulk row writes replace per-pixel Image.Plane accessor calls. Shared/interleaved UV planes retain their own pixel/row strides.
- Frame timestamps remain index × 1,000,000 / requested FPS. Resolution, bitrate, frame count and BT.709 limited-range conversion are preserved. No frames are dropped to improve a speed figure.

## Measured scope

The differential JVM test verified 72 combinations of sizes, strides, offsets and overlapping UV buffers against the previous converter, byte for byte. One warmed desktop JVM run over thirty 1280×720 frames measured 152.7ms in the reference converter and 93.3ms in the optimized converter: 1.64× for color conversion only. Run `tests/java/Yuv420ConverterTest.java` with the production converter to repeat locally. The ratio is not a device-wide or whole-export guarantee.

The first Android 11 software-emulator service test completed with no StudioActivity, continued after Home and saved matching bytes through MediaStore.Downloads. The decoded result contained thirty H.264/YUV420P frames at 640×360 and 30fps, one second, BT.709 limited. The motion square moved between the first and last decoded frame; moov preceded mdat.

A second UI-driven export continued after `finishAndRemoveTask()`. Recovery testing restored the playable video, Downloads path and chosen dimensions/FPS. Immediate cancellation and the notification cancel action both deleted partial exports, left Downloads history unchanged and unlocked the next export.

Physical-device performance, Android 13+ notification prompts, Android 15 service timeout behavior, 1080p60, other codecs and the experimental GPU path have not been acceptance-tested. The output is still silent.

## Updating native UI styles

After changing Tailwind utility classes in `ui/Motion2Video-Studio.html`, run `node tools/compile_studio_css.cjs` with the Playwright Chromium installed (or set `CHROMIUM_PATH`). Commit both generated `studio-tailwind.css` and `studio-tailwind-loader.js`. Custom CSS remains in the canonical HTML.
