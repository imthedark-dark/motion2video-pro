# Release signing and supply chain

This phase adds release tooling; it does not publish a hardened APK. Existing v1.4.1 is a legacy development-signed release. Never replace its tag/assets. Phase 3 native/physical acceptance is still pending and must be completed before publishing new production bytes.

## Contributor builds

Use full JDK17, Python3, Android SDK35/build-tools35.0.0 and the checksum-pinned Gradle8.9 wrapper:

```sh
python3 build.py
python3 build.py --release
```

Debug remains independently development-signed (`build/debug.jks`, ignored; optional `M2V_DEBUG_KEYSTORE`). Release without `--sign` stays non-debuggable, R8-minified/resource-shrunk and **unsigned**. Neither is a production distributable. Filenames use the APK's actual Gradle output metadata/versionName. `dist/SHA256SUMS.txt` is generated for the local APK inventory.

## Create and back up a production key

The maintainer must control the release identity. Creating a new key does not permit upgrades over APKs signed with another key. Obtain the legitimate existing release key if continuity is required; do not substitute a test key or assume the old development key is suitable for production. Record the public signing-certificate SHA-256 separately through a trusted channel.

On a trusted machine, choose a key path **outside the checkout** and generate interactively (passwords are prompted, not put in shell history):

```sh
keytool -genkeypair -storetype JKS -keyalg RSA -keysize 3072 \
  -validity 10000 -alias motion2video-release \
  -keystore /secure/offline/motion2video-release.jks
```

Choose the certificate subject interactively. Back up the key in encrypted offline storage and store its passwords separately in a password manager. Test restoration with a disposable signed build; keep at least two controlled backups. Losing the key can prevent upgrades; unauthorized access can let someone impersonate releases. An Android signing key is not an APK file, source-code license or password-reset credential. Never upload it with release assets or bug reports.

## Supply signing inputs

Explicit opt-in is required:

```sh
python3 build.py --release --sign
```

Provide all four settings through the process environment (inject with a password manager/secret manager; do not paste passwords in command arguments):

| Environment variable | Local properties key |
| --- | --- |
| `M2V_RELEASE_KEYSTORE` | `storeFile` |
| `M2V_RELEASE_STORE_PASSWORD` | `storePassword` |
| `M2V_RELEASE_KEY_ALIAS` | `keyAlias` |
| `M2V_RELEASE_KEY_PASSWORD` | `keyPassword` |

Alternatively put these properties in root `release-signing.properties`, a local ignored file with owner-only permissions. Use Java properties escaping for backslashes or use forward slashes in paths. Relative keystore paths resolve against the project root. Environment values take precedence. Missing settings fail closed; build.py explicitly forces signing OFF for debug/unsigned builds, even if a global Gradle property enables it. Signing files/secrets are not read for those builds. Direct Gradle equivalent: `./gradlew --no-daemon -Pm2vReleaseSigning=true :app:assembleRelease`. Do not use verbose/debug Gradle logging with real secrets.

Signed output is `dist/Motion2Video-<version>-Dark-release.apk`. This filename alone does not prove a production key was used. Verify the signer against the independently recorded fingerprint:

```sh
apksigner verify --verbose --print-certs dist/Motion2Video-<version>-Dark-release.apk
```

Tests for this phase use only a newly generated seven-day **throwaway QA** certificate outside Git. Those APKs must never be published or used as upgrade identities. No production signing secret is supplied to CI.

## Verify before release

1. Choose a new versionName/versionCode; never reuse an immutable tag or overwrite published bytes.
2. Run the required UI, clock, YUV and platform/input checks, both builds, compiled-manifest/R8 checks, source audit and `python3 tools/test_supply_chain.py`.
3. Run `python3 tools/verify_dependencies.py`. Every bundled asset, version/license record, font version and embedded offline GSAP copy must match the reviewed schema-2 inventory. No updater automatically executes/replaces a dependency.
4. Run `npm ci --ignore-scripts` and `npm audit --audit-level=high` (includes dev tools). Resolve findings; a network error is not a clean audit.
5. Resolve the actual native runtime graph and run the isolated scanner:

```sh
./gradlew --no-daemon :app:copySecurityDependencies
./gradlew --no-daemon -p tools/dependency-check dependencyCheckAnalyze
```

OWASP Dependency-Check Gradle13.0.0 scans those AAR/JAR artifacts and bundled scripts. Its build is isolated from AGP so scanner libraries cannot change the app classpath. CVSS7+ findings and data/scan errors fail CI. First NVD synchronization can be slow; an optional `NVD_API_KEY` secret helps rate limiting. Pull-request scans receive no NVD secret. Without a key the scanner uses the official NIST JSON 2.0 feeds. Trusted successful runs cache the advisory database. The workflow also runs each Monday at 04:23 UTC on the default branch and supports manual dispatch. Feed failures remain failures. Document any reviewed false positive with evidence and an expiry in tools/dependency-check/suppressions.xml. Reports are in `tools/dependency-check/build/reports/dependency-check/`. Do not suppress scanner errors or claim coverage when feeds are unavailable. Reports do not establish exploitability or comprehensive safety; review false positives with evidence, never blanket-suppress a library.

6. Complete native acceptance on working devices, including minified signed build, renderer/cancel/memory/preview tests, upgrade identity, Downloads and permission flows. Current native gaps are in [VALIDATION.md](VALIDATION.md).
7. Sign with the controlled production identity, verify signature/non-debuggable flags and independently verify the final exact bytes. Review [licensing concerns](LICENSING.md), including GSAP's custom terms. This document is operational guidance, not legal advice.

## Distribute via GitHub Releases

APKs are no longer tracked in `downloads/` on this branch; historical Git commits/tags are not rewritten. GitHub Releases carry APK assets plus a checksum manifest. Source archives contain no current distributable APK. CI debug artifacts remain contributor test artifacts, not production releases.

Stage only the intended signed APK in a clean private directory, then:

```sh
python3 tools/checksums.py generate /path/to/release-staging
python3 tools/verify_release.py --directory /path/to/release-staging
```

After maintainer review and successful native/signature checks, attach that exact APK and SHA256SUMS.txt to a **new** GitHub Release. Do not upload unsigned, development-signed or throwaway QA files, extra local APKs or keys. The workflow `release-assets.yml` defaults to read-only verification. Its separate explicit `publish-draft` mode preserves publishing automation without tracked binaries: it requires a new pre-staged draft, green build/OWASP jobs for the exact tagged commit, the trusted signer fingerprint and maintainer confirmation of native acceptance. Only that job has contents:write/actions:read. It verifies checksums, APK signature, package/version and non-debuggable flags, then makes the same draft public without rebuilding/uploading/overwriting bytes. It refuses already-published releases or extra assets (including keys).

To use the guarded publication mode after all checks pass: create/push a **new** tag for the reviewed source, stage only the signed APK and SHA256SUMS.txt in a new **draft** GitHub Release, and run the build/audit workflow on that exact tagged commit. Dispatch `release-assets.yml` with mode `publish-draft`, that tag, the successful audit run ID, the independently trusted signer SHA-256 and the native-acceptance checkbox. It requires both build and dependency-check jobs to have succeeded on the same commit. Current blocked advisory feeds/native gaps therefore cannot be represented as completed acceptance. No publication mode was invoked during this phase.

The guard does not establish reproducible-build provenance by itself. The maintainer must ensure the staged exact bytes were built from the tag, own the controlled signing identity, and have actually tested them. It does not create keys or infer native-device success from a checkbox. `tools/verify_publish_candidate.py` can run the same byte/signature/manifest checks locally without publishing.

Verify downloaded public bytes:

```sh
python3 tools/verify_release.py --release v1.4.1
# For a future new signed-release filename, additionally pass --apk <exact asset name>.
```

The legacy manifest lists older APKs too; remote verification selects only the requested asset, not absent assets from other releases. Local checksum verification requires the exact staged APK inventory, rejects unsafe/duplicate names and symlinks, and checks ZIP integrity. SHA-256 from the same download channel proves consistency, not independent authenticity; the trusted signer fingerprint is a separate check.

## Action and dependency updates

Dependabot covers npm, Gradle (app and isolated scanner) and GitHub Actions weekly. Every external action is pinned to a full upstream commit SHA; comments identify its major version. Workflow permissions default to read-only; only the guarded explicit publication job has contents:write/actions:read. Checkout credentials are not persisted. Review Dependabot PRs, upstream provenance/licensing and scan/regression results before merging. Refresh reviewed asset metadata/hashes explicitly when bundled files change; never just regenerate hashes to silence an integrity failure.

## Phase 6 release gate

Feature builds remain unreleased debug/unsigned artifacts. Before owner-controlled signing/publication, complete the physical-device capability, audio A/V/priming, queue lifecycle/quota and FileProvider share checks in VALIDATION.md, including a minified release UI pass. Host DSP/FIFO and compile-only instrumentation are not substitutes. Preserve the existing signer and user-owned production keystore; this work neither creates production keys nor changes published Release assets.

### Fresh-checkout contributor builds

An explicit `M2V_DEBUG_KEYSTORE` must name an existing development keystore. Otherwise an existing ignored `build/debug.jks` is reused. On a fresh checkout, Android Gradle Plugin uses its standard automatically generated debug keystore. Contributor debug builds require no production secrets. Production signing remains an explicit, separate configuration.
