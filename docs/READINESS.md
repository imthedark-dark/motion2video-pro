# Public readiness checklist

Updated: 2026-11-19. This records completed work and real limits; it is not an external approval or certification.

| Requested item | Result | Evidence / remaining work |
| --- | --- | --- |
| Choose a license, review code ownership and third-party compatibility | Original project work licensed MIT; third-party scope reviewed | [LICENSE](../LICENSE), [license inventory](LICENSING.md). No ownership grant over upstream files. GSAP remains custom-licensed; complete dependency stack is not wholly OSI/MIT. No written clearance from Webflow is claimed. |
| Add LICENSE and CONTRIBUTING.md | Complete | [LICENSE](../LICENSE), [CONTRIBUTING.md](../CONTRIBUTING.md); notices included in v1.4.1 packaging |
| Verify APK and SHA-256 in Release | Complete for v1.4.1: native APK/checksum attached and verified | [Verification](RELEASE_VERIFICATION.md). GitHub Actions attached the signed files; independent public downloads returned HTTP 200 and matching hashes. |
| Gather real download, usage, Stars and Forks statistics | Public Stars/Forks and asset counters recorded; unsupported metrics explicitly unknown | [Metrics](METRICS.md). Raw-download and active-user counts are not available. No private traffic published. |
| Record reproducible tests and supported phones | Reproduction guide and honest matrix complete; no physical-phone acceptance claim | [Validation](VALIDATION.md), test sources and decoded sample. Android 11 emulator tested; physical phones remain untested. |
| Prepare a precise English impact explanation and development plan | Complete | [Purpose, evidence and roadmap](IMPACT_AND_ROADMAP.md) distinguishes demonstrated behavior from unmeasured adoption and planned features. |

A standard MIT license is applied by the rights holder; there is no separate GitHub/OSI license application to obtain for original code. This does not obtain rights over dependencies or provide app-store approval.

GSAP retaining its own license is the documented licensing boundary, not an unfinished application for MIT rights. The current code-import/rendering scope is recorded in [LICENSING.md](LICENSING.md). A future no-code visual editor needs a fresh license review. No physical Android device is connected to the publishing environment; that acceptance test remains genuinely unperformed. A [Persian device checklist](DEVICE_TEST.fa.md) and issue form are provided for collecting real reports.
