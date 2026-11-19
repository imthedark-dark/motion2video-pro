# Contributing to Motion2Video Pro

[English README](README.md) · [راهنمای فارسی](README.fa.md)

The original project code and documentation are MIT licensed. Contributions to those files are accepted under the same license. Submit only work you have permission to contribute, identify copied code and its upstream license, and retain third-party notices. No copyright transfer is required. Bundled GSAP and fonts keep their own licenses; see [the dependency review](docs/LICENSING.md).

## Start with an issue or pull request

Describe the problem, the expected result and a minimal example. For rendering bugs include Android version, device model, WebView version, codec, dimensions, FPS and bitrate. Use a small animation you can share publicly. Remove tokens, private links, imported personal HTML and identifiers from logs. The [reproducibility guide](docs/VALIDATION.md) lists the supported test commands and current evidence.

For a change, create a branch, keep the change focused and explain the user-visible behavior. Include relevant validation and remaining limitations. A large feature or new dependency should first explain its purpose, platform requirements and license. Do not silently update bundled libraries or claim support for an untested codec/device.

## Build and check

Use a full JDK 17 (including jlink/jmod), Python 3, Android SDK Platform 35 and Build Tools 35.0.0. The Python entry point uses the checked Gradle wrapper and resolves AndroidX; initial downloads need network access:

```sh
python3 tools/audit_source.py
python3 tools/test_yuv.py
python3 tools/verify_release.py
python3 build.py
npm ci
npx playwright-core install chromium
npm run test:ui
npm run test:clock
```

Set `ANDROID_SDK_ROOT` to your SDK and optionally `CHROMIUM_PATH` to a system Chromium executable. Run the checks relevant to the change; documentation-only changes do not require a device render. CI builds with a fresh debug key; its APK cannot update the published APK. Never commit a keystore, credentials, SDK files, generated runtime logs or private data.

For Phase 2 release verification, also run `python3 build.py --release`, `python3 tools/verify_phase2.py` and `python3 tools/test_platform.py`. The release APK is unsigned until production signing is configured in Phase 4. See [Phase 2 native/QA instructions](docs/PHASE2_ANDROID.md).

## Interface changes

`ui/Motion2Video-Studio.html` is the canonical single-file interface. Preserve Persian RTL, the Dark signature, responsive layouts and the project's visual/motion rules. The Android builder copies it to `assets/studio.html`. If Tailwind utility classes change, run `node tools/compile_studio_css.cjs` and commit both generated CSS/loader assets. Refresh `DEPENDENCIES.lock.json` for changed bundled assets; preserve upstream notices.

## Media and background changes

Preserve requested frame count, timestamps, dimensions, bitrate and color math. Test background completion, restoring progress/results and cancellation if service lifetime changes. Keep the 72 differential YUV cases passing when modifying the converter. Record device tests in [the compatibility matrix](docs/VALIDATION.md); label emulators separately from physical phones.

## راهنمای کوتاه فارسی

برای گزارش اشکال، نسخهٔ اندروید و WebView، مدل گوشی، تنظیمات خروجی و یک نمونهٔ کوچک و قابل انتشار بفرست. اطلاعات خصوصی و کلیدها را وارد مخزن نکن. تغییرات کد اصلی با مجوز MIT پذیرفته می‌شوند؛ مجوز کتابخانه‌ها باید حفظ شود. آزمون انجام‌نشده را موفق ثبت نکن.

Phase4: run `python3 tools/verify_dependencies.py` and `python3 tools/test_supply_chain.py` when bundled metadata or release tooling changes. `DEPENDENCIES.lock.json` schema2 contains versions, license paths, SHA-256 and sizes; update it only after reviewing actual changed upstream bytes. Never commit APKs, keys or `release-signing.properties`. Follow [RELEASE.md](docs/RELEASE.md); a green host build is not native acceptance or permission to publish a QA key.
