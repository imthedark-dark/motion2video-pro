"""Run OWASP with its original exit status and a credential-safe CI summary."""
import os
from pathlib import Path
import re
import subprocess


def main():
    # Upstream diagnostics may echo part of a rejected key. Keep raw output
    # out of Actions logs and summaries; reports remain available as artifacts.
    with subprocess.Popen(
        ["./gradlew", "--no-daemon", "-p", "tools/dependency-check", "dependencyCheckAnalyze"],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace",
    ) as scan:
        reasons = set()
        for line in scan.stdout:
            if "One or more dependencies were identified with vulnerabilities" in line:
                reasons.add("CVSS threshold reached")
            if "Invalid API Key" in line:
                reasons.add("NVD rejected the API key")
            if "Error updating the NVD Data" in line:
                reasons.add("NVD data update failed")
            match = re.search(r"NVD Returned Status Code: (\d{3})\b", line)
            if match:
                reasons.add("NVD HTTP " + match[1])
            http_error = re.search(r"(?:HTTP response code:|status code[: =]|HTTP/\d(?:\.\d)?)\s*([45]\d\d)\b", line, re.I)
            if http_error:
                reasons.add("HTTP " + http_error[1])
            if "Unable to download" in line or "DownloadFailedException" in line:
                reasons.add("Advisory download failed")
            if "timed out" in line.lower():
                reasons.add("Request timed out")
        code = scan.wait()
    summary = "OWASP Dependency-Check passed (CVSS threshold 7.0)."
    if code:
        summary = "OWASP Dependency-Check failed. " + (", ".join(sorted(reasons)) or "Scanner error")
    print(summary)
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as output:
        print(summary, file=output)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
