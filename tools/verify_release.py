#!/usr/bin/env python3
"""Verify local builds or immutable GitHub Release assets, never raw Git files."""
import argparse,re,tempfile,urllib.request,zipfile
from pathlib import Path
from checksums import digest,entries,verify

def apk(path):
 with zipfile.ZipFile(path) as z:
  if z.testzip() is not None or not {'AndroidManifest.xml','classes.dex','assets/studio.html'}.issubset(z.namelist()):raise ValueError('Invalid APK archive')

def download(url,path,limit):
 with urllib.request.urlopen(url,timeout=60) as response,path.open('wb') as out:
  size=0
  for chunk in iter(lambda:response.read(1024*1024),b''):
   size+=len(chunk)
   if size>limit:raise ValueError('Download exceeds verification limit')
   out.write(chunk)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--directory',type=Path,default=Path('dist'));p.add_argument('--release');p.add_argument('--remote',action='store_true',help='Legacy alias: verify published v1.4.1 through GitHub Releases');p.add_argument('--repo',default='imthedark-dark/motion2video-pro');p.add_argument('--apk');a=p.parse_args()
 if a.remote and not a.release:a.release='v1.4.1'
 if not a.release:
  for path in verify(a.directory):apk(path)
  print('PASS: local APK digests and ZIP integrity');return
 if not re.fullmatch(r'v\d+\.\d+\.\d+',a.release) or not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',a.repo):p.error('Invalid repository/tag')
 name=a.apk or f'Motion2Video-{a.release[1:]}-Dark.apk'
 if not re.fullmatch(r'Motion2Video-[A-Za-z0-9._-]+\.apk',name):p.error('Invalid APK name')
 base=f'https://github.com/{a.repo}/releases/download/{a.release}/'
 with tempfile.TemporaryDirectory(prefix='motion-release-') as tmp:
  folder=Path(tmp);download(base+'SHA256SUMS.txt',folder/'SHA256SUMS.txt',65536);expected=entries(folder/'SHA256SUMS.txt')
  if name not in expected:raise ValueError('APK missing from release checksum manifest')
  download(base+name,folder/name,256*1024*1024)
  if digest(folder/name)!=expected[name]:raise ValueError('Release APK checksum mismatch')
  apk(folder/name)
 print('PASS: GitHub Release APK SHA-256 and ZIP integrity (not a signature/device acceptance check)')
if __name__=='__main__':main()
