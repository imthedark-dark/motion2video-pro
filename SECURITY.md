# Sensitive files

Signing keys, tokens, environment files, local SDK configuration, private download URLs and user runtime data must stay outside Git. `build/`, `dist/`, local settings and key extensions are ignored. The default debug key password is a standard development value, not a production credential.

Before publishing, run `python3 tools/audit_source.py`. Never commit a release signing key; APK signing certificates are public metadata and do not contain its private key.

## Phase 1 source boundary

The `security/phase-1` review adds offline imported resources by default, explicit Persian online consent, early CSP, separate trusted/untrusted WebViews, strict native MessagePort schemas and renderer-loss cleanup. No `addJavascriptInterface` is exposed. Debug access follows the APK debuggable flag. See [verified findings](docs/AUDIT_REPORT.md), [decisions](docs/DECISIONS.md) and [tests/remaining risks](docs/PHASE1_SECURITY.md). Published v1.4.1 does not yet contain these source changes.

Imported JavaScript is still executable and may consume CPU/memory. CSP is defense in depth, not an OS security guarantee. Online opt-in allows external requests and possible disclosure of user content; Safe Browsing/platform services can have separate network behavior. Phase 1 native installation timed out; subsequent Phase 2 emulator checks exercised native interception and real renderer loss. Physical-device acceptance remains unverified. Signing/supply-chain and the full vulnerability-reporting policy rewrite remain Phase 4.

## Phase 2 source boundary

The `security/phase-2` branch preserves Phase 1 isolation and adds a non-exported, read-only AndroidX FileProvider with only the Motion2Video Downloads root. Registered legacy content URIs remain compatible; MediaStore is retained for Android 10+. Only the launcher is exported, including after library manifest merging. Storage is bounded to API26–28, notifications to API33+, and denied permissions do not abort encoding. Backup remains disabled and explicit network-security XML forbids cleartext and trusts system CAs only.

OS foreground-service deadlines cancel work, release the wake lock and stop the service without waiting on the main thread. Workers retain their request and cannot publish after their service stops. A non-debuggable R8/resource-shrunk release and native lifecycle/file checks passed on an Android 11 emulator; no physical-phone or Android 15 real quota acceptance is claimed. Full production signing/reporting-policy changes remain Phase 4; published v1.4.1 has not been replaced. [Details and limitations](docs/PHASE2_ANDROID.md).
