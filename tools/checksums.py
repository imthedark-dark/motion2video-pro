#!/usr/bin/env python3
"""Generate/verify SHA256SUMS.txt for local APKs; never follows symlinks."""
import argparse,hashlib,re
from pathlib import Path
NAME=re.compile(r'[A-Za-z0-9][A-Za-z0-9._-]*\.apk\Z')
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()
def entries(path):
 result={}
 if path.is_symlink() or path.stat().st_size>65536:raise ValueError('Invalid checksum manifest')
 for line in path.read_text(encoding='ascii').splitlines():
  match=re.fullmatch(r'([a-f0-9]{64})  ([A-Za-z0-9][A-Za-z0-9._-]*\.apk)',line)
  if not match or match[2] in result:raise ValueError('Invalid/duplicate checksum entry')
  result[match[2]]=match[1]
 if not result:raise ValueError('Empty checksum manifest')
 return result
def apks(directory):
 paths=sorted(directory.glob('*.apk'))
 if not paths or any(p.is_symlink() or not p.is_file() or not NAME.fullmatch(p.name) for p in paths):raise ValueError('Expected safe regular APK files')
 return paths
def verify(directory):
 expected=entries(directory/'SHA256SUMS.txt');paths=apks(directory)
 if set(expected)!={p.name for p in paths}:raise ValueError('APK/manifest inventory mismatch')
 for p in paths:
  if digest(p)!=expected[p.name]:raise ValueError('SHA-256 mismatch: '+p.name)
 return paths
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('mode',choices=['generate','verify']);parser.add_argument('directory',type=Path);a=parser.parse_args()
 if a.mode=='generate':
  paths=apks(a.directory);manifest=a.directory/'SHA256SUMS.txt'
  if manifest.is_symlink():raise ValueError('Refusing symlink manifest')
  manifest.write_text(''.join(f'{digest(p)}  {p.name}\n' for p in paths),encoding='ascii')
 else:paths=verify(a.directory)
 print(f'PASS: {a.mode} checksums for {len(paths)} APK(s)')
if __name__=='__main__':main()
