#!/usr/bin/env python3
"""Verify committed APK digests; optionally compare immutable public raw links.
Read-only: does not modify APKs, tags or release metadata.
"""
from pathlib import Path
import argparse,hashlib,re,urllib.request,zipfile
p=argparse.ArgumentParser();p.add_argument('--remote',action='store_true');p.add_argument('--repo',default='imthedark-dark/motion2video-pro');a=p.parse_args();root=Path(__file__).resolve().parents[1]
for line in (root/'downloads/SHA256SUMS.txt').read_text().splitlines():
 expected,name=line.split();assert re.fullmatch(r'[a-f0-9]{64}',expected), 'Invalid digest';assert re.fullmatch(r'Motion2Video-[0-9.]+-Dark\.apk',name), 'Unsafe APK name'
 path=root/'downloads'/name;actual=hashlib.sha256(path.read_bytes()).hexdigest();assert actual==expected, name+': local SHA-256 mismatch'
 with zipfile.ZipFile(path) as z:assert z.testzip() is None;assert {'AndroidManifest.xml','classes.dex','assets/studio.html'}.issubset(z.namelist())
 if a.remote:
  version=re.fullmatch(r'Motion2Video-([0-9.]+)-Dark\.apk',name).group(1);url=f'https://raw.githubusercontent.com/{a.repo}/v{version}/downloads/{name}'
  with urllib.request.urlopen(url,timeout=60) as r:
   digest=hashlib.sha256();size=0
   while True:
    chunk=r.read(1024*1024)
    if not chunk:break
    digest.update(chunk);size+=len(chunk)
  assert digest.hexdigest()==expected, name+': remote SHA-256 mismatch';assert size==path.stat().st_size
 print('PASS:',name,'local'+(' + public tagged download' if a.remote else ''),'SHA-256 and APK ZIP integrity')
