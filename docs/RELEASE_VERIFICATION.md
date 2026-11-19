# APK distribution and checksum verification

Current packaged version: **1.4.1**. It adds license notices and documentation to the unchanged v1.4 rendering implementation. Previous immutable APKs/tags are retained.

- [Release page](https://github.com/imthedark-dark/motion2video-pro/releases/tag/v1.4.1)
- [Direct APK for ADM](https://raw.githubusercontent.com/imthedark-dark/motion2video-pro/v1.4.1/downloads/Motion2Video-1.4.1-Dark.apk)
- [Tagged SHA256SUMS.txt](https://raw.githubusercontent.com/imthedark-dark/motion2video-pro/v1.4.1/downloads/SHA256SUMS.txt)

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

The release notes provide working direct APK/checksum links. The `uploads.github.com` asset upload operation returned **HTTP 401** in this publishing environment, so native GitHub Release asset attachments are **not** marked complete. No credential was included in source to work around this restriction. A maintainer with functioning asset-upload authentication can attach the exact files from `downloads/` through GitHub's release editor; do not rebuild or replace the signed bytes for that purpose.

Keeping the download route explicit also matters for statistics: raw downloads are not represented by GitHub's release-asset download counter.
