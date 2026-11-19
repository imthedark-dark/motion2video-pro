# Public project metrics

Snapshot: **2026-11-19 UTC**. Source: public GitHub repository/release REST endpoints; machine-readable values and exact timestamp are in [`metrics.json`](metrics.json).

| Metric | Observed value | Interpretation |
| --- | --- | --- |
| Stars | 0 | Public repository snapshot, not a user count |
| Forks | 0 | Public repository snapshot, not deployments |
| Subscribers | 0 | Public subscribers field, not all repository visitors |
| Downloads reported for attached release assets | 0 | Two v1.4.1 assets are attached; the public counter may lag and includes automated checks, not only users |
| Direct `raw.githubusercontent.com` APK downloads | Unknown | Not exposed by the public endpoints used here |
| Active users / successful real-world exports | Unknown | No app usage telemetry or verified usage study |
| Private traffic / views / clones | Not collected | Excluded from publication |

The APK is now attached to Release v1.4.1 and also available by direct tagged raw download. Release-asset counters do not count this route. Stars/Forks should not be presented as adoption, impact or active users. No download estimates or invented growth figures are used.

Refresh the public snapshot explicitly:

```sh
python3 tools/collect_metrics.py
```

The script uses only unauthenticated public endpoints, requests no private traffic and installs no telemetry. Update this dated table when the snapshot changes. Raw-download and active-user values must remain unknown unless a documented, privacy-respecting measurement source is introduced.
