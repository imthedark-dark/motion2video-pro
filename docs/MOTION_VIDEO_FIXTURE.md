# Exact motion-video.html investigation

The test uses the supplied file byte for byte. SHA-256 is `8819f55d148a91fe7f290441057c44ff756ca29e802bd110d12a7ccd6c795e7a`. The test runs each revision's production Java asset interceptor against minimal Android API stand-ins and loads the prepared document from the app's `https://motion.local/` origin in Chromium. The historical tag used duplicate native interceptors with the same results for the URLs in this fixture. The legacy Google responses are simulated with local font bytes. This reproduces the resource policy, page readiness script and the clock-port protocol. Android's independent network flag, compositor, WebView lifecycle and process exits require Android testing.

## Findings in investigation order

1. **Fonts.** The stylesheet asks for Orbitron and Vazirmatn in one CSS2 URL. Commit `624e48b` (Phase 1) first combined an all-families-known alias check with an offline 403 for unmatched URLs. Orbitron was absent from that list, so the mixed request failed as a whole. The tag's interceptor also did not recognize Orbitron, but let WebView request Google. In the hardened app `style-src` allowed the Google CSS alias; the 403 came from the interceptor. `font-src` limited native offline font binaries to allowed local resources, so direct gstatic URLs were not eligible. `connect-src` covers Fetch/XHR and was irrelevant to this CSS import. Chromium dispatched a 403/aborted stylesheet, displayed the fixture using fallback, completed `document.fonts.ready` and acknowledged both sampled frames. No font-induced hang or renderer exit was reproduced.
2. **Canvas.** The source uses exactly 120 Canvas 2D particles and a loop with 7,140 unordered distance checks per animation frame, drawing lines only under 120 pixels. There is no WebGL and the backing canvas uses `innerWidth × innerHeight`, without multiplying by devicePixelRatio. At the tested 393×221 viewport and DPR 3 one 4-byte-per-pixel backing buffer is 347,412 bytes, about 0.33 MiB. A 1920×1080 backing buffer would be about 7.9 MiB. These sizes exclude WebView compositing, GPU textures and concurrent surfaces. The `blur(40px)` on a 600×600 glow and cursor `mix-blend-mode` add compositor work. Both compared Android revisions already force the preview hardware layer and select the offscreen layer according to capture mode. Phase 1 added renderer-exit handling and centralized security settings; Phase 3 added watchdogs. The Chromium test did not reproduce a blank or stuck canvas.
3. **Clock.** `motion-clock.js` is byte-identical between the tag and Phase 6. Its seek pauses and positions CSS animations through Web Animations or the negative-delay fallback and acknowledges the frame after two animation frames. It does not override rAF, `performance.now()` or `setTimeout()`; the fixture provides no `renderFrame` hook. Its custom scene controller therefore advances in real time, independent of requested render timestamps. Both clock paths acknowledged seeks in all tested revisions. Exact scene timing for frame-by-frame export would require a source hook and is separate from the preview problem.
4. **WebViews.** Studio and imported preview are separate native WebViews in both revisions. Offscreen export adds a service-owned WebView for the private display. The Studio pauses the preview while rendering and leaves the instance allocated; a WebView pause does not guarantee its JavaScript stops. The earlier compatibility commit on this fix branch skipped a redundant initial preview load, while retaining the isolated views. The reported failure also occurred before export, which does not require the third WebView.
5. **Cursor and input.** The pointer handler changes cursor coordinates only on a mouse event. The easing loop for the follower runs every frame even without input. The test observed no cursor coordinate writes from the mouse handler until simulated movement, then verified the expected coordinate and zero JavaScript errors. The scene uses key and inline button listeners, but neither frame seek calls a listener.

## Historical results

Measured elapsed time from host navigation to the app's page-ready flag. The two frame acknowledgements are the modern Web Animations and fallback seeks, respectively. The legacy outbound counts are eligible requests handled entirely by the local test shim.

| Revision | Page-ready | Font requests and console errors | Blank frame | Frame acknowledgements |
| --- | ---: | --- | --- | --- |
| v1.4.1 | 425 ms | Google CSS + 2 Google font requests eligible, zero errors | No | 41, 42 |
| security/phase-1 | 394 ms | Google CSS 403, one resource console error | No | 41, 42 |
| security/phase-2 | 378 ms | Google CSS 403, one resource console error | No | 41, 42 |
| security/phase-3 | 367 ms | Google CSS 403, one resource console error | No | 41, 42 |
| security/phase-4 | 378 ms | Google CSS 403, one resource console error | No | 41, 42 |
| security/phase-5 | 384 ms | Google CSS 403, one resource console error | No | 41, 42 |
| feature/phase-6 | 341 ms | Google CSS 403, one resource console error | No | 41, 42 |
| fix branch before the font change | 315 ms | Google CSS 403, one resource console error | No | 41, 42 |
| fix branch with the font change | 361 ms | Both actual bundled fonts loaded, zero errors/blocks/network requests | No | 41, 42 |

Phases 1–6 sent no requests outside the intercepted origin and had no CSP violations for the exact fixture. The failed stylesheet is reported as HTTP 403 and Chromium's `net::ERR_ABORTED` for the same request. All cases drew nonzero-alpha Canvas pixels and a visible title, with advancing progress. Timings are single host observations. Run `node tests/motion-video-fixture.cjs --history` to repeat the matrix when the phase refs are present. The normal UI test command runs the current branch only.

## Xiaomi follow-up

Install the debug APK and import the unchanged fixture offline. Confirm the notice and both fonts in normal preview. If it stops, collect the WebView version from **درباره**, time from import to the error, and `adb logcat -d -s Motion2VideoWebView:D`. Compare normal and software preview. The debug event distinguishes a watchdog timeout, an Android-reported renderer crash and an exit without a crash. It includes a fixed blocked-resource category and CSP directive where available, never HTML, request URLs or paths. The exporter should also be checked on the phone, since the fixture's JavaScript timeline is independent of the frame seek clock.
