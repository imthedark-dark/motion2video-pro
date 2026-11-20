#!/usr/bin/env python3
"""Run native font policy tests using only an injected transport."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/motion2video/app"
with tempfile.TemporaryDirectory() as out:
    names = ["FontDiscovery", "FontDownload", "FontCache", "FontConsent"]
    tests = ["FontDownloadTest", "FontCacheTest"]
    subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-encoding", "UTF-8", "-d", out,
                    *(str(src / (n + ".java")) for n in names),
                    *(str(root / "tests/java" / (n + ".java")) for n in tests)], check=True)
    for test in tests:
        subprocess.run(["java", "-cp", out, "dev.motion2video.app." + test], check=True)
