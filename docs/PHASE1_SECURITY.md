# Phase 1: WebView and native bridge boundary

This phase changes source on `security/phase-1`; it is not an update to the already-published v1.4.1 binary. See [AUDIT_REPORT.md](AUDIT_REPORT.md) for verified baseline findings and [DECISIONS.md](DECISIONS.md) for tradeoffs. Later phases are not started.

Imported HTML runs with file/content and file-URL cross-access disabled, DOM storage disabled, Safe Browsing enabled and navigation rejected. Trusted studio storage remains enabled to retain the editor's code. No JavaScriptInterface exists. A private MessagePort goes only to the trusted studio; the animation gets only a frame acknowledgement channel. Undeclared commands, extra keys and coerced scalar types are rejected. Registered-download lookup remains the boundary for opening a file.

## Offline/online behavior

`اجازهٔ منابع آنلاین` (Allow online resources) is OFF by default. Bundled native fonts, GSAP/plugins, Lottie, Three.js and the Tailwind runtime can be served by allowlisted local responses, including recognized CDN aliases. Other requests receive an empty 403 and native network loads are blocked. CSP adds connect/resource/embedding restrictions before user scripts. Known aliases never imply general network permission.

Enabling the switch requires the native Persian warning's affirmative button. Cancellation/back leaves it off. Consent is session-scoped and captured into each service request; it is not restored from localStorage, imported options or a previous completed job. While a job is active the option is locked. Online content can contact HTTPS servers/send content and use secure WebSockets. It cannot navigate into the studio, private files or arbitrary content providers. Safe Browsing/platform traffic is a separate OS behavior.

Standalone browser preview uses a sandboxed frame, CSP and bundled inline GSAP. External fonts/images need embedding or online permission; the Android app's packaged fonts remain available offline. The trusted studio's CDN UI tags are not imported animation network permission.

## Reproduce automated security checks

```sh
npm ci --ignore-scripts
npx playwright-core install chromium
npm run test:ui
npm run test:clock
python3 tools/test_yuv.py
python3 build.py
```

Host prerequisites include JDK 17, Node, Chromium and OpenSSL (ephemeral TLS test keys stay in ignored build/ and are removed by the test).

`test:ui` now includes `tests/security.cjs`: it compiles and executes the **production Java CSP injector** and exercises its HTML in Chromium sandboxed frames. Offline fetch, XHR, beacon, external image/script/style, WebSocket, worker/frame and navigation probes must not reach the network boundary. A separate online pass must reach permitted HTTPS resources. WebSockets/beacons are checked with real CSP violation events, not Playwright's WebSocket shim or the beacon method's return value (a queued beacon may return true even when CSP blocks it).

The studio UI test also proves that an imported frame's forged native-port transfer and undeclared native messages do not establish a privileged connection; native opt-in waits for confirmation. These are browser/host tests, not a substitute for Android engine tests.

## Native fixture (requires device/emulator)

Build the app and instrumentation APK using the same development keystore, install them on an expendable test target, then run:

```sh
python3 tests/build-instrumentation.py
adb shell am instrument -w dev.motion2video.test/.WebSecurityTest
```

This fixture is designed to check actual settings, offline resource responses and the closed native JSON schema. The API30 emulator booted here, but APK installation timed out after 180 seconds before instrumentation; this native fixture has not been executed successfully. Repeat existing render/background/cancellation tests to validate vendor WebView behavior, including `setBlockNetworkLoads` with intercepted local HTTPS resources. For API29+ process loss, run `adb shell am instrument -w dev.motion2video.test/.RendererGoneTest`. It terminates a test service WebView renderer and checks the Persian error, stopped job and deleted partial output; this intrusive fixture has compiled but has not been run successfully in this environment. No physical device is connected.

CSP intentionally cannot bound arbitrary JavaScript CPU/memory. Frame waits already have timeouts, but overall memory/watchdog hardening is Phase 3. FileProvider/release minification are Phase 2; supply-chain/release migration is Phase 4. WebGL/1080p60/all-codec acceptance remains unverified.
