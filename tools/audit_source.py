#!/usr/bin/env python3
"""Reject private key files, local settings and common credential formats.
Reports paths and rule names only; never prints matched credential values.
"""
from pathlib import Path
import re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
RULES={
 'private key':re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'),
 'GitHub credential':re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
 'AWS access key':re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
 'Google API key':re.compile(r'\bAIza[0-9A-Za-z_-]{35}\b'),
 'Slack credential':re.compile(r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b'),
 'signed download link':re.compile(r'https?://[^\s\"<>]+[?&](?:sig|X-Amz-Signature|X-Goog-Signature)=',re.IGNORECASE),
 'credential in URL':re.compile(r'https?://[^\s/:]+:[^\s/@]+@'),
}
try:
 output=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT)
 files=sorted(set(output.decode().split('\0'))-{''})
except (subprocess.CalledProcessError,FileNotFoundError):
 files=[str(p.relative_to(ROOT)) for p in ROOT.rglob('*') if p.is_file() and not any(x in p.relative_to(ROOT).parts for x in ['.git','node_modules','build','dist','__pycache__'])]
issues=[]
for name in files:
 p=ROOT/name
 if not p.is_file():continue
 if p.is_symlink():issues.append((name,'symlink must be reviewed'));continue
 if p.suffix.lower() in {'.jks','.keystore','.key','.pem','.p12','.pfx','.pk8','.p8','.apk','.aab'} or p.name in {'local.properties','.npmrc','.netrc','.pypirc'} or (p.name.startswith('release-signing') and p.suffix=='.properties') or p.name=='.env' or (p.name.startswith('.env.') and p.name!='.env.example'):
  issues.append((name,'private/local file'));continue
 raw=p.read_bytes()
 if b'\0' in raw:continue
 try:text=raw.decode('utf-8')
 except UnicodeDecodeError:continue
 for label,pattern in RULES.items():
  if pattern.search(text):issues.append((name,label))
for name,label in issues:print(f'BLOCK: {name}: {label}')
print(f'Audited {len(files)} files; {len(issues)} issue(s).')
sys.exit(bool(issues))
