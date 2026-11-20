# Phase 5 — privacy and diagnostic boundaries

## Findings and change scope

No first-party Android logging/telemetry/network client was found. The runtime dependency graph remains AndroidX/Kotlin/Guava/annotations, not a reporting SDK. WebView resources are the app-controlled network surface: native known aliases are local; unknown imported requests are blocked unless explicitly opted in. The standalone UI uses CDNs. Safe Browsing/platform/provider traffic cannot be equated to a first-party render upload. The policy in [PRIVACY.md](../PRIVACY.md) states these distinctions in Persian and English, with storage/deletion details.

- `PrivateWebChromeClient` consumes modern/legacy console callbacks without reading/forwarding message content. `WebViewSecurity.configure` installs it for all three WebViews; Studio no longer replaces it with a default client. No verbose native logger or crash SDK was added. Remote debugging stays APK-debuggable-only.
- `PublicErrors` admits only exact reviewed, bounded fixed messages. Unknown provider/codec exception messages, HTML, private paths/URLs, oversized text, cyclic causes and broken message accessors fall back to fixed Persian guidance. Permission/file/memory/timeout types have fixed messages. New status/notifications use this policy; old stored error/warning text is filtered before display. Existing private storage and deliberate Downloads paths/URIs remain; no destructive data migration or secure-wipe claim is made.
- Manifest requests `android.webkit.WebView.MetricsOptOut=true`, checked against Android documentation. Safe Browsing is retained; the metadata is a provider opt-out request, not a device-wide network kill switch.
- Removed the studio's two speculative Google Fonts preconnect hints. CDN script/font URLs required for the standalone UI stay; Android still intercepts them locally. No CSS/font/animation/encoder parameters changed.
- Optional desktop FFmpeg arguments are unchanged. Default stdout/stderr are suppressed; failure output is fixed, without a CalledProcessError command dump. `--debug` explicitly restores local FFmpeg diagnostics. FFmpeg protocols/provider behavior remain external, not an Android offline guarantee.

## Reproduce host/build checks

```sh
npm run test:ui
npm run test:clock
python3 tools/test_yuv.py
python3 tools/test_platform.py
python3 tools/test_input.py
python3 tools/test_privacy.py
python3 tools/verify_privacy.py
python3 tools/test_supply_chain.py
python3 build.py
python3 build.py --release
python3 tools/verify_phase2.py
python3 tools/verify_privacy.py --apks
python3 tools/verify_dependencies.py
```

The browser security suite uses the actual production Java CSP and a real local TLS server. Offline probes must produce zero hits; online consent must permit a synthetic POST body. No real user data is used. `test_privacy.py` runs actual production error-filter cases and a fake FFmpeg process to check output suppression plus exact unchanged encoding/audio arguments. Compiled APK checks inspect real merged metadata; source guards reject new diagnostic sinks/raw exception forwarding and known telemetry client dependencies. They are regression guards, not complete taint analysis or runtime packet capture.

## Pending native acceptance

`PrivacyTest` instrumentation checks actual console callback handling and the manifest metrics flag on a working debug device. Compile with `python3 tests/build-instrumentation.py`, then run `am instrument -w dev.motion2video.test/.PrivacyTest`. This fixture is not a full logcat audit and has not been marked passed here.

On working Android/WebView versions, import synthetic console/error canaries, capture local logcat without real user HTML, and confirm no app-forwarded canary. Check default imported traffic with a controlled network monitor, explicit opt-in traffic, system/provider/Safe Browsing behavior, release debugging disabled, cached/draft retention and deletion, public Downloads survival after uninstall and ordinary output playback. Hardware/emulator failures from Phase3 remain unresolved, so full native logging/network acceptance is still a release gate.

The previously recorded Phase4 advisory-feed and private-reporting setup blocks are not addressed by Phase5. No production key or new public APK is introduced. Phase6 requires another confirmation.
