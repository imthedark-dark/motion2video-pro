#!/usr/bin/env python3
"""Exercise corruption, inventory, unsafe-name and version guards with real files."""
import copy,json,shutil,tempfile,subprocess,sys
from pathlib import Path
from checksums import verify,digest,entries
from verify_dependencies import verify as verify_assets
root=Path(__file__).resolve().parents[1];checks=0

def rejected(call):
 global checks
 try:call()
 except (ValueError,KeyError):checks+=1;return
 raise AssertionError('Invalid supply-chain input accepted')

with tempfile.TemporaryDirectory(prefix='motion-supply-') as tmp:
 folder=Path(tmp);(folder/'a.apk').write_bytes(b'original');manifest=folder/'SHA256SUMS.txt'
 def reset():manifest.write_text(digest(folder/'a.apk')+'  a.apk\n')
 reset();verify(folder);checks+=1
 (folder/'a.apk').write_bytes(b'corrupted');rejected(lambda:verify(folder));reset()
 (folder/'extra.apk').write_bytes(b'extra');rejected(lambda:verify(folder));(folder/'extra.apk').unlink()
 manifest.write_text('0'*64+'  ../a.apk\n');rejected(lambda:entries(manifest))
 reset();manifest.write_text(manifest.read_text()*2);rejected(lambda:entries(manifest))
 manifest.write_text('');rejected(lambda:entries(manifest))
 reset();(folder/'link.apk').symlink_to(folder/'a.apk');rejected(lambda:verify(folder));(folder/'link.apk').unlink()
 assets=folder/'app/src/main/assets';shutil.copytree(root/'app/src/main/assets',assets);(folder/'ui').mkdir();shutil.copy(root/'ui/Motion2Video-Studio.html',folder/'ui')
 original=json.loads((root/'DEPENDENCIES.lock.json').read_text());lock=folder/'DEPENDENCIES.lock.json'
 def reset_lock():lock.write_text(json.dumps(original))
 reset_lock();verify_assets(folder);checks+=1
 p=assets/'gsap.min.js';data=p.read_bytes();p.write_bytes(data+b'changed');rejected(lambda:verify_assets(folder));p.write_bytes(data)
 (assets/'extra.js').write_text('extra');rejected(lambda:verify_assets(folder));(assets/'extra.js').unlink()
 p.rename(assets/'missing.js');rejected(lambda:verify_assets(folder));(assets/'missing.js').rename(p)
 p.rename(assets/'saved');p.symlink_to(assets/'saved');rejected(lambda:verify_assets(folder));p.unlink();(assets/'saved').rename(p)
 wrong=copy.deepcopy(original);wrong['components']['font-amiri']['version']='999.0';lock.write_text(json.dumps(wrong));rejected(lambda:verify_assets(folder));reset_lock()
 (folder/'ui/Motion2Video-Studio.html').write_text('stale');rejected(lambda:verify_assets(folder))
 audit_root=folder/'audit';(audit_root/'tools').mkdir(parents=True);shutil.copy(root/'tools/audit_source.py',audit_root/'tools')
 for name in ['release-signing.properties','forbidden.apk']:
  file=audit_root/name;file.write_bytes(b'test fixture, no credentials')
  result=subprocess.run([sys.executable,str(audit_root/'tools/audit_source.py')],cwd=audit_root,capture_output=True,text=True)
  assert result.returncode!=0 and name in result.stdout;checks+=1;file.unlink()
 result=subprocess.run([sys.executable,str(root/'build.py'),'--sign'],capture_output=True,text=True)
 assert result.returncode==2 and '--sign requires --release' in result.stderr;checks+=1
 print(f'PASS: {checks} actual checksum/asset corruption, inventory, symlink, traversal, duplicate and version guards')
