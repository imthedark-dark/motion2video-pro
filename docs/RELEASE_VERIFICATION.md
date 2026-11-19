# APK distribution and checksum verification

Current packaged version: **1.4.1**. It adds license notices and documentation to the unchanged v1.4 rendering implementation. Previous immutable APKs/tags are retained.

- [Release page](https://github.com/imthedark-dark/motion2video-pro/releases/tag/v1.4.1)
- [Direct APK for ADM](https://github.com/imthedark-dark/motion2video-pro/releases/download/v1.4.1/Motion2Video-1.4.1-Dark.apk)
- [Tagged SHA256SUMS.txt](https://github.com/imthedark-dark/motion2video-pro/releases/download/v1.4.1/SHA256SUMS.txt)

`downloads/` contains the signed distributable, not a private signing key. The published APK keeps the signing certificate used for previous versions; independently generated CI debug artifacts have a different local key.

Verified APK SHA-256:

```text
f7f55ace3ad21431036f9021b50ff932571c44d4889d25da8185d295687f27d3
```

## Verify bytes and signature

From a source checkout:

```sh
python3 tools/verify_release.py
python3 tools/verify_release.py --remote
```

The first checks all committed APKs against `downloads/SHA256SUMS.txt`, ZIP integrity and required APK entries. The second independently downloads each immutable public tagged APK and compares its bytes/size to the same digest. Run after publishing a new tag. CI runs the local check on every build; remote checking requires network access and an already-published tag.

For a manual download, place the APK and checksum file in one directory:

```sh
sha256sum --check SHA256SUMS.txt --ignore-missing
apksigner verify --print-certs Motion2Video-1.4.1-Dark.apk
```

Expected upgrade signing-certificate SHA-256:

```text
e35d0fd36c70de294ce2419b0397bdcaf32fadda34fef7a94b119f26261759d9
```

This certificate digest is public metadata, not the private key. A matching digest establishes file/signature consistency, not freedom from vulnerabilities or an external security certification.

## Release attachment status

**Complete for v1.4.1.** The native APK and SHA256SUMS.txt assets are attached to the GitHub Release. The [release-assets workflow](../.github/workflows/release-assets.yml) uploaded the existing signed bytes with GitHub's built-in token and downloaded them again for verification. [Successful publishing run](https://github.com/imthedark-dark/motion2video-pro/actions/runs/44442997677).

An independent public HTTP download returned 200 for both assets and matched the committed bytes. The APK is 1,965,306 bytes. No rebuild, replacement private key or external credential was needed. The earlier local `uploads.github.com` HTTP 401 restriction was resolved by publishing inside GitHub Actions.

Maintainers can run the workflow manually for an existing semantic release tag. It verifies the committed checksum, refuses to overwrite mismatching existing assets and checks downloaded uploaded files. The raw tagged download remains a fallback; its downloads are not included in Release-asset counters.
