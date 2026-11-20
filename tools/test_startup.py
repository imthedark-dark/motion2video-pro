#!/usr/bin/env python3
"""Force-stop and time a debug APK on the selected test device. Preserves app data."""
import argparse
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb', help='ADB binary or device-specific wrapper')
parser.add_argument('--max-delay-ms', type=int, default=500)
args = parser.parse_args()
package = 'dev.motion2video.app'


def adb(*command):
    return subprocess.check_output([args.adb, *command], text=True, timeout=120).strip()


adb('shell', 'am', 'force-stop', package)
# Restrict logs by both launch time and PID, including when a PID is recycled.
since = adb('shell', "date '+%m-%d %H:%M:%S.000'")
launch = adb('shell', 'am', 'start', '-W', '-n', package + '/.StudioActivity')
print(launch, flush=True)
if "Status: ok" not in launch:
    raise SystemExit("FAIL: Android did not confirm launch")
pid = adb('shell', 'pidof', '-s', package)
deadline = time.monotonic() + 240
pattern = re.compile(r'^([A-Z_]+) elapsedMs=(\d+) main=(true|false) cpuMs=(\d+)(?: maxQueueDelayMs=(\d+))?$')
while time.monotonic() < deadline:
    lines = adb('logcat', '-b', 'main', '-d', '-T', since, '--pid', pid,
                '-v', 'raw', '-s', 'M2VStartup:D', '*:S').splitlines()
    samples = [m for line in lines if (m := pattern.fullmatch(line))]
    if samples and samples[-1][1] == 'READY':
        break
    time.sleep(1)
else:
    raise SystemExit('FAIL: debug bridge-ready milestone missing')

for sample in samples:
    print(sample[0])
expected = 'ACTIVITY FIRST_DRAW PROVIDER_BEGIN PROVIDER_END INPUTS_READY STUDIO_BEGIN STUDIO_CONSTRUCTED STAGE_BEGIN STAGE_READY LOAD_QUEUED BRIDGE READY'.split()
if [m[1] for m in samples] != expected:
    raise SystemExit('FAIL: incomplete or out-of-order startup')
for m in samples:
    worker = m[1] in ('PROVIDER_BEGIN', 'PROVIDER_END', 'INPUTS_READY')
    if (m[3] == 'true') == worker:
        raise SystemExit('FAIL: wrong thread for ' + m[1])
delay = int(samples[-1][5])
if delay > args.max_delay_ms:
    raise SystemExit(f'FAIL: main queue late by {delay} ms (budget {args.max_delay_ms} ms)')
print('PASS: first draw precedes WebView setup, studio ready, delay within budget')
