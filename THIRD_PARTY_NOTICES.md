# Third-party notices

Project attribution: **Dark**. Original project work is licensed under [MIT](LICENSE). This does not replace third-party copyrights or licenses. See [license scope and compatibility](docs/LICENSING.md).

- Fonts: Vazirmatn, Amiri, Noto Naskh Arabic, Scheherazade New, Anton and Inter. SIL OFL license files are bundled beside fonts in `app/src/main/assets/fonts`.
- The standalone UI also embeds GSAP 3.12.5 as base64 for offline imported previews; decoding retains the original upstream header and license. This is not relicensed under MIT.
- GSAP UI core 3.12.5 and motion core/plugins 3.13.0 retain original headers linking the [GSAP Standard License](https://gsap.com/standard-license/). GSAP uses a custom license, not MIT/OSI. A retrieved normative-terms snapshot is in `app/src/main/assets/licenses/GSAP-STANDARD-LICENSE.txt`; see the scope review before adding a no-code visual builder.
- Tailwind Play CDN 3.4.17: MIT notice is in `app/src/main/assets/licenses/tailwindcss-MIT.txt`; bundled transitive notices remain in the script. The native utility stylesheet is generated from this build.
- lottie-web 5.13.0: copyright 2015 Bodymovin, MIT; full notice in `app/src/main/assets/licenses/lottie-web-MIT.txt`.
- Three.js 0.160.1 / r160: copyright 2010–2023 three.js authors, MIT; full notice in `app/src/main/assets/licenses/three-MIT.txt`.
- Android SDK, JDK, Chromium, FFmpeg and Playwright are external build/test/runtime tools, not part of the APK's native media engine.

New notice files added after v1.4.0 are included in the v1.4.1 APK and subsequent builds. Publishing this source update does not retroactively replace the immutable APK/tag.

Phase 2 adds AndroidX Core 1.13.1 (including FileProvider) and its AndroidX/Kotlin/Guava runtime dependencies under Apache-2.0. The full license is bundled as `app/src/main/assets/licenses/androidx-Apache-2.0.txt`. Copyrights remain with the Android Open Source Project/AndroidX contributors, JetBrains and the respective upstream authors. Gradle 8.9 wrapper is Apache-2.0 build tooling, not part of the APK; generated wrapper notices are retained. The Gradle distribution is checked by SHA-256. Native dependencies are resolved through the pinned Gradle declaration; extended supply-chain locking/review remains Phase 4.

Phase 4 records exact versions, notice paths and asset SHA-256/sizes in `DEPENDENCIES.lock.json` (schema2); `tools/verify_dependencies.py` enforces the inventory, font name-table versions and embedded UI GSAP copy. No bundled library/font bytes were upgraded. GSAP's custom restrictions and unresolved historical-grant/redistribution questions remain flags for maintainer review; this inventory is not legal advice or written clearance. OWASP Dependency-Check13.0.0 is isolated external audit tooling, not APK runtime code.
