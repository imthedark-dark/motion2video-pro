# License scope and dependency review

Reviewed: 2026-11-19. Project attribution: **Dark**.

## What MIT covers

The maintainer has selected the [MIT License](../LICENSE) for original Motion2Video Java/Python/JavaScript code, HTML/CSS, project documentation and original project artwork. MIT is an OSI-approved open-source license. It permits reuse, modification and distribution while requiring preservation of its copyright and permission notice. No application to GitHub or OSI is required to apply the standard license to code the maintainer has the right to license.

This grant does not claim ownership of third-party files, fonts, imported user animations, operating-system components or external tools. Existing third-party notices and licenses take precedence for their respective content. Imported HTML/assets remain the importer's responsibility. There is no external certification or endorsement of the complete APK.

## Dependency inventory

| Component / bundled version | License / evidence | Scope and conditions |
| --- | --- | --- |
| Original app, renderer, UI and docs | [MIT](../LICENSE), copyright 2026 Dark | Preserve copyright and permission notice; no warranty |
| Tailwind Play CDN 3.4.17 and generated utility CSS | [MIT text](../app/src/main/assets/licenses/tailwindcss-MIT.txt); embedded bundled notices | Keep MIT and embedded transitive notices |
| lottie-web 5.13.0 | [MIT text](../app/src/main/assets/licenses/lottie-web-MIT.txt), upstream airbnb/lottie-web v5.13.0 LICENSE.md | Keep upstream copyright and permission notice |
| Three.js 0.160.1 / r160 | [MIT text](../app/src/main/assets/licenses/three-MIT.txt), upstream mrdoob/three.js r160 LICENSE | Keep upstream copyright and permission notice |
| Vazirmatn, Amiri, Noto Naskh Arabic, Scheherazade New, Anton, Inter | SIL OFL 1.1; individual `*-OFL.txt` beside the [font files](../app/src/main/assets/fonts) | Preserve notices and OFL conditions, including reserved font names where stated; do not sell font files by themselves |
| GSAP UI core 3.12.5; motion core and MorphSVG, DrawSVG, SplitText, MotionPath, Flip 3.13.0 | Original headers reference [GSAP Standard License](https://gsap.com/standard-license/); [retrieved terms](../app/src/main/assets/licenses/GSAP-STANDARD-LICENSE.txt) | Custom license, **not MIT and not an OSI open-source license**; preserve proprietary notices and comply with use restrictions |
| Android SDK, JDK, Chromium, Playwright and optional FFmpeg | External build/test/runtime tools; their respective upstream licenses | Not embedded native media codecs supplied by this project; review redistribution obligations if packaging these tools separately |

The GSAP page retrieved on 2026-11-19 states an effective date of 2025-04-30 and last modification of 2025-05-30. The saved text is a reference snapshot of its normative terms, not a replacement grant or proof of historical terms for every GSAP version.

## Compatibility boundary

MIT permits the project to use dependencies under separate licenses; it does not relicense them. The complete distribution contains custom-licensed GSAP and therefore must not be described as an entirely MIT/OSI-licensed dependency stack. GSAP's current terms restrict use in certain visual, no-code animation builders competing with Webflow. The current application imports and renders user-written HTML; it does not implement such a no-code editor. This is the project's documented scope, not a written clearance from Webflow. Before adding a visual animation builder, review the then-applicable terms and obtain written permission if required, or replace GSAP with a dependency whose license fits that use.

The review found no basis for granting MIT rights over GSAP or other third-party work. Upstream rights and the restrictions above remain in effect. No commercial/codec patent license, app-store approval or certification is implied by this copyright license.

## Distribution and provenance

Full lottie-web/Three.js notices and the GSAP terms snapshot were added to source after v1.4.0. The immutable v1.4.0 APK/tag is not changed by this documentation update; it retains the original bundled dependency headers and font/Tailwind license files. The new notice files are included in v1.4.1 and subsequent builds. Original script headers have been preserved. GSAP files are identified by version in their headers; bundled asset SHA-256 values are in `DEPENDENCIES.lock.json`.

Before publishing new dependencies: record the exact version, upstream source and license, copy required notices, update asset hashes and run relevant regressions. An available update is not automatically installed.

## Phase 2 native dependency addition

AndroidX Core **1.13.1** supplies the standard FileProvider. Its resolved runtime graph includes other AndroidX components, Kotlin standard-library components and Guava's listenablefuture; these are Apache-2.0 projects with copyrights retained by their upstream authors (Android Open Source Project/AndroidX, JetBrains, Google and contributors). The Apache-2.0 text is in `app/src/main/assets/licenses/androidx-Apache-2.0.txt`. This does not alter GSAP's custom-license boundary. Gradle 8.9 wrapper, generated from the checksum-verified distribution, retains its upstream Apache-2.0 headers and is build tooling rather than APK runtime code. The new artifacts are source-branch/QA changes, not replacements for published v1.4.1. Detailed automated dependency verification remains Phase 4.
