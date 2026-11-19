# Sensitive files

Signing keys, tokens, environment files, local SDK configuration, private download URLs and user runtime data must stay outside Git. `build/`, `dist/`, local settings and key extensions are ignored. The default debug key password is a standard development value, not a production credential.

Before publishing, run `python3 tools/audit_source.py`. Never commit a release signing key; APK signing certificates are public metadata and do not contain its private key.

## Phase 1 source boundary

The `security/phase-1` review adds offline imported resources by default, explicit Persian online consent, early CSP, separate trusted/untrusted WebViews, strict native MessagePort schemas and renderer-loss cleanup. No `addJavascriptInterface` is exposed. Debug access follows the APK debuggable flag. See [verified findings](docs/AUDIT_REPORT.md), [decisions](docs/DECISIONS.md) and [tests/remaining risks](docs/PHASE1_SECURITY.md). Published v1.4.1 does not yet contain these source changes.

Imported JavaScript is still executable and may consume CPU/memory. CSP is defense in depth, not an OS security guarantee. Online opt-in allows external requests and possible disclosure of user content; Safe Browsing/platform services can have separate network behavior. Physical-device, Android renderer-kill and native interception acceptance have not been executed for this phase. Signing/supply-chain and the full vulnerability-reporting policy rewrite remain Phase 4.
