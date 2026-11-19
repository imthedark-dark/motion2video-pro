# Security policy

## Supported versions

| Version | Status |
| --- | --- |
| Latest security-review source (`security/phase-5`, incorporating Phases 1–4) | Reports accepted; host/build checks recorded, native acceptance pending |
| Published v1.4.1 | Legacy development-signed release; investigate reports, but security fixes currently exist only in review source |
| v1.4.0 and older | Unsupported; do not assume they contain the hardening in current source |

No hardened production APK has been published. Published v1.4.1 does **not** contain Phases 1–5. Native Phase 3 acceptance and physical-device checks remain outstanding. Update this table when a tested release ships.

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
- Enabling online resources lets imported code contact external servers and potentially disclose content. Safe Browsing and OS services may have their own network behavior. Phase5 privacy controls and scoped evidence are documented in PRIVACY.md; device-wide network/logging acceptance is still pending.
- MediaCodec/WebView/OS vulnerabilities and codec availability depend on the device and vendor updates. Codec support is not universal.
- Hashes prove byte consistency against a reviewed manifest, not author identity or absence of bugs. Validate the APK signer separately through a trusted fingerprint. Dependency scanners can miss vulnerabilities or report false positives; unavailable advisory feeds must not be reported as clean scans.
- Original code is MIT; GSAP is custom-licensed. The whole bundled stack is not wholly OSI-licensed. See [license review](docs/LICENSING.md).

## Sensitive material

Never commit signing keys, passwords, tokens, private URLs, local signing properties, logs or user runtime data. `build/debug.jks`, release keys and `release-signing.properties` are ignored. The standard debug password is a public development default, not a production credential. Run `python3 tools/audit_source.py` before committing; its checks do not replace human review. Build/verification CI has read-only permissions and pinned actions; only the explicit draft-publication job has contents:write/actions:read. No CI job has a production signing key. Do not publish contributor/throwaway QA APKs as production releases.

## Phase 5 privacy and logging controls

All native WebViews consume console messages without forwarding message/source text. Unknown exception strings are not copied into new public errors or notifications; exact reviewed messages and fixed type fallbacks are used, with bounded/cycle-safe cause traversal. Legacy stored error/warning display is filtered. Intentional output metadata remains available to the trusted UI. No native verbose logger, analytics or crash-reporting SDK was added. WebView metrics opt-out is declared; Safe Browsing remains enabled. Optional desktop diagnostics require `--debug`.

See [PRIVACY.md](PRIVACY.md) for Persian/English storage, network and deletion details. Tests distinguish default blocking from explicitly allowed remote content. Native packet/logcat capture and OS/provider telemetry behavior remain unverified; this is not an air-gap guarantee. A future crash-reporting integration must be explicitly opt-in and OFF by default, with a new privacy review. Current private-reporting setup and Phase4 feed blockers are unchanged.

Phase 6 sharing accepts only bounded content URIs found in app export history. The final URI is regenerated by FileProvider from a canonical generated filename under the existing Download/Motion2Video root; no caller-selected paths or write grants. Native chooser/file access across OEM storage implementations requires device validation.

Audio imports use content-provider selection, strict extension/MIME/size/duration/channel/rate bounds, opaque UUID cache IDs and a closed option schema. Parsers have a single bounded worker and a caller timeout; a stuck platform/native parser may occupy that worker until Android terminates it. DSP/codec loops poll cancellation and memory/deadline limits; scratch is removed on failure or interrupted-job recovery. No imported path/URI reaches MediaExtractor from the JS bridge.

Queue commands are closed-schema UUID actions, never caller-selected file paths. Admission is transactional (≤8 unfinished jobs, ≤60MiB HTML), requests are immutable and source settings are revalidated on execution. Active-job interruption marks failure; waiting work pauses on process death/quota. Resume intersects captured online consent with current-session native consent. Old partial staging may require clearing app cache after storage exhaustion; no automatic native-hang recovery or Android FGS quota bypass is claimed.

## Phase 9 native font download / دانلود بومی فونت

Imported HTML may cause an eligible family to appear in the Persian consent dialog. The trusted bridge has no operations to approve a font, start a download or read device font information. The native dialog or a remembered grant for the same family during an explicit user action authorizes requests. Bare CSS family names and dynamically constructed requests cannot grant permission. The worker has an exact two-host HTTPS allowlist, disallows redirects and credentials, validates and bounds the response, and never executes or serves Google CSS directly. The offline WebView receives verified local bytes under document-scoped synthetic paths. Deletion cannot remove assets during a running offscreen render. The release contains no new diagnostic output, and existing debug diagnostics retain fixed categories.

فایل HTML می‌تواند نام یک فونت واجد شرایط را برای نمایش در پنجرهٔ رضایت پیشنهاد کند. پل ارتباطی راهی برای شروع دانلود، اعطای اجازه یا دیدن فونت‌های نصب‌شده ندارد. دانلود فقط از پنجرهٔ بومی یا اجازهٔ قبلی همان خانواده همراه اقدام کاربر آغاز می‌شود. تنها `fonts.googleapis.com` و `fonts.gstatic.com` با HTTPS مجازند. فایل محدود و بررسی می‌شود، در حافظهٔ خصوصی می‌ماند و با دکمهٔ **فونت‌ها** قابل حذف است. گوگل IP و خانوادهٔ درخواستی را می‌بیند. اینترنت پیش‌فرض WebView بسته می‌ماند. [حریم خصوصی](PRIVACY.md) · [جزئیات و محدودیت‌ها](docs/FONTS.md)
