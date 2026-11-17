# Sensitive files

Signing keys, tokens, environment files, local SDK configuration, private download URLs and user runtime data must stay outside Git. `build/`, `dist/`, local settings and key extensions are ignored. The default debug key password is a standard development value, not a production credential.

Before publishing, run `python3 tools/audit_source.py`. Never commit a release signing key; APK signing certificates are public metadata and do not contain its private key.
