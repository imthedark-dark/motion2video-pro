# Security policy

## Supported versions

| Version | Status |
| --- | --- |
| Latest security-review source (`security/phase-4`, incorporating Phases 1–3) | Reports accepted; host/build checks recorded, native acceptance pending |
| Published v1.4.1 | Legacy development-signed release; investigate reports, but security fixes currently exist only in review source |
| v1.4.0 and older | Unsupported; do not assume they contain the hardening in current source |

No hardened production APK has been published. Published v1.4.1 does **not** contain Phases 1–4. Native Phase 3 acceptance and physical-device checks remain outstanding. Update this table when a tested release ships.

## Report a vulnerability privately

After the owner enables reporting, use [GitHub private vulnerability reporting](https://github.com/imthedark-dark/motion2video-pro/security/advisories/new): open the repository's **Security → Advisories → Report a vulnerability**. Include affected commit/APK version, Android/WebView versions, reproducible steps, impact and a minimal synthetic HTML fixture. Remove personal data, signing material and tokens. Do not publish exploit details or user HTML in public issues, discussions or pull requests.

**Setup status checked 2026-11-19:** private vulnerability reporting is currently disabled. Enabling it through the connected integration returned HTTP 403. A repository owner must enable **Settings → Code security → Private vulnerability reporting** before external reporters can use this route. There is no verified alternative private email address in this repository; do not send sensitive details publicly while the private route is unavailable. Maintainers can use draft GitHub Security Advisories for private triage and coordinated disclosure.

## Response expectations

This is a volunteer-maintained project, not a guaranteed response service. Target acknowledgement: seven days; initial assessment: fourteen days; status update at least every thirty days while a report is active. Complex platform/dependency issues can take longer. Coordinate public disclosure after a fix or an agreed timeline; never request production keys, real private animations or credentials to reproduce a bug.

## Threat model and boundaries

Imported HTML/CSS/SVG/JavaScript is active, untrusted code. The trusted Persian studio and imported content use separate WebViews. Imported code receives no `addJavascriptInterface` bridge. Native messages use a closed schema; filesystem opening is restricted to registered `content://` downloads. Imported resources are offline by default; explicit native Persian consent enables online HTTPS resources. Local packaged aliases, early CSP, restricted WebView settings and disabled cleartext are defense in depth.

Imports have type/size/UTF-8 limits; render requests restrict dimensions, duration, FPS, bitrate and codec. Watchdogs check readiness, frames, Java heap and low system memory; preview responsiveness and renderer-loss handlers attempt safe teardown. Download access is narrow and read-only; non-launcher components are unexported. Release signing is opt-in, separate from debug, with secrets outside Git. See [audit evidence](docs/AUDIT_REPORT.md), [decisions](docs/DECISIONS.md), [input limits](docs/PHASE3_INPUT.md) and [release process](docs/RELEASE.md).

## Known limitations

- Arbitrary JavaScript can consume CPU/memory. Java heap checks are not a renderer/GPU/OS memory quota; process death and blocked platform APIs can defeat timely cleanup. API26–28 cannot use the newer renderer-termination API.
- Native infinite-script/cancel/preview, memory-pressure and final minified-release runtime acceptance remain unverified in Phase 3 because the emulator's system services failed. No physical-phone acceptance is claimed.
- Enabling online resources lets imported code contact external servers and potentially disclose content. Safe Browsing and OS services may have their own network behavior. A complete privacy/logging audit is Phase 5 work.
- MediaCodec/WebView/OS vulnerabilities and codec availability depend on the device and vendor updates. Codec support is not universal.
- Hashes prove byte consistency against a reviewed manifest, not author identity or absence of bugs. Validate the APK signer separately through a trusted fingerprint. Dependency scanners can miss vulnerabilities or report false positives; unavailable advisory feeds must not be reported as clean scans.
- Original code is MIT; GSAP is custom-licensed. The whole bundled stack is not wholly OSI-licensed. See [license review](docs/LICENSING.md).

## Sensitive material

Never commit signing keys, passwords, tokens, private URLs, local signing properties, logs or user runtime data. `build/debug.jks`, release keys and `release-signing.properties` are ignored. The standard debug password is a public development default, not a production credential. Run `python3 tools/audit_source.py` before committing; its checks do not replace human review. Build/verification CI has read-only permissions and pinned actions; only the explicit draft-publication job has contents:write/actions:read. No CI job has a production signing key. Do not publish contributor/throwaway QA APKs as production releases.
