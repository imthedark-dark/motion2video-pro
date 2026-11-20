# Release verification

APKs are distributed only as [GitHub Release assets](https://github.com/imthedark-dark/motion2video-pro/releases) with SHA256SUMS.txt. This branch no longer tracks APK binaries. Historical tags/history and existing assets are unchanged. Follow [RELEASE.md](RELEASE.md) for controlled signing, clean staging, source/asset/scanner checks and native acceptance.

## Historical v1.4.1 evidence

- [Direct legacy APK](https://github.com/imthedark-dark/motion2video-pro/releases/download/v1.4.1/Motion2Video-1.4.1-Dark.apk)
- [Release SHA256SUMS.txt](https://github.com/imthedark-dark/motion2video-pro/releases/download/v1.4.1/SHA256SUMS.txt)
- SHA-256: `f7f55ace3ad21431036f9021b50ff932571c44d4889d25da8185d295687f27d3`
- Public signing-certificate SHA-256: `e35d0fd36c70de294ce2419b0397bdcaf32fadda34fef7a94b119f26261759d9`

This was a development-signed1.4.1 package, 1,965,306 bytes, predating Phases1–4. The earlier [successful upload run](https://github.com/imthedark-dark/motion2video-pro/actions/runs/44442997677) attached the existing bytes and verified them. It is historical evidence, not a production security certification or proof of the new source's native acceptance.

```sh
python3 tools/verify_release.py --release v1.4.1
python3 tools/verify_release.py --directory dist
```

Remote verification uses GitHub Releases, never raw Git fallback. It checks the selected APK against the release manifest and ZIP integrity. Local verification requires the exact staged APK/checksum inventory. Verify the APK signer separately with `apksigner verify --verbose --print-certs` and a fingerprint obtained through a trusted channel.

The manual release-assets workflow defaults to read-only verification. Its separate guarded publish-draft mode can make an already-staged draft public only after signature/version/checksum checks, green tagged-source build/OWASP jobs and maintainer native-acceptance confirmation. It cannot overwrite an already-published release or obtain binaries from Git. No production key or new hardened public APK is created by these checks.
