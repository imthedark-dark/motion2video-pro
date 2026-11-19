#!/usr/bin/env python3
"""Fail on changed, missing, extra or symlinked bundled assets and stale metadata."""
import argparse,base64,hashlib,json,re,struct
from pathlib import Path

def font_versions(data):
 tables=struct.unpack_from('>H',data,4)[0]
 for i in range(tables):
  tag,_,offset,_=struct.unpack_from('>4sIII',data,12+16*i)
  if tag!=b'name':continue
  _,count,strings=struct.unpack_from('>HHH',data,offset);versions=[]
  for n in range(count):
   platform,_,_,name,length,start=struct.unpack_from('>6H',data,offset+6+12*n)
   if name==5:
    text=data[offset+strings+start:offset+strings+start+length].decode('utf-16-be' if platform in (0,3) else 'mac_roman')
    match=re.search(r'\d+(?:\.\d+)+',text)
    if match:versions.append(match[0])
  return set(versions)
 raise ValueError('Missing font version table')

def verify(root):
 root=Path(root);assets=root/'app/src/main/assets';lock=json.loads((root/'DEPENDENCIES.lock.json').read_text())
 if lock.get('schema_version')!=2:raise ValueError('Expected dependency manifest schema 2')
 components=lock['components'];records=lock['assets']
 if any(p.is_symlink() for p in assets.rglob('*')):raise ValueError('Symlink in bundled assets')
 actual={str(p.relative_to(assets)) for p in assets.rglob('*') if p.is_file()}
 if actual!=set(records):raise ValueError('Asset inventory differs from manifest')
 for name,c in components.items():
  if any(not isinstance(c.get(k),str) or not c[k] for k in ('name','version','license','source')):raise ValueError('Incomplete component: '+name)
  if c['version'] in ('latest','unknown','*'):raise ValueError('Unpinned component')
  if not c.get('license_files') or any(p not in records for p in c['license_files']):raise ValueError('Missing component license')
 for name,record in records.items():
  if Path(name).is_absolute() or '..' in Path(name).parts or record['component'] not in components:raise ValueError('Unsafe asset metadata')
  data=(assets/name).read_bytes()
  if not re.fullmatch('[a-f0-9]{64}',record['sha256']) or hashlib.sha256(data).hexdigest()!=record['sha256'] or len(data)!=record['bytes']:raise ValueError('Asset integrity mismatch: '+name)
  c=components[record['component']]
  if name.endswith('.ttf') and font_versions(data)!={c['version']}:raise ValueError('Font version mismatch: '+name)
  if record.get('version_marker') and (c['version'] not in record['version_marker'] or record['version_marker'] not in data.decode('utf-8')):raise ValueError('Library version marker mismatch: '+name)
 if (assets/'studio.html').read_bytes()!=(root/'ui/Motion2Video-Studio.html').read_bytes():raise ValueError('Canonical UI and bundled copy differ; sync before locking')
 ui=(assets/'studio.html').read_text();match=re.search(r'const OFFLINE_GSAP="([^"]+)"',ui)
 if not match or base64.b64decode(match[1],validate=True)!=(assets/'studio-gsap-3.12.5.min.js').read_bytes():raise ValueError('Embedded offline GSAP differs')
 print(f'PASS: {len(records)} bundled asset hashes, {len(components)} version/license records, font versions and embedded GSAP')

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);a=p.parse_args();verify(a.root)
