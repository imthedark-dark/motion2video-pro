#!/usr/bin/env python3
"""Inspect compiled APK manifests/resources and actual R8 output, not just Gradle flags."""
from pathlib import Path
import os,re,subprocess,zipfile
root=Path(__file__).resolve().parents[1];sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME']);aapt=sdk/'build-tools/35.0.0/aapt2'
def dump(apk,name='AndroidManifest.xml'):
 return subprocess.check_output([str(aapt),'dump','xmltree','--file',name,str(apk)],text=True).replace('http://schemas.android.com/apk/res/android:','android:')
def resource_xml(apk,name):
 table=subprocess.check_output([str(aapt),'dump','resources',str(apk)],text=True)
 match=re.search(r'\bxml/'+re.escape(name)+r'\n\s+\(\) \(file\) (\S+) type=XML',table)
 assert match,'Missing XML resource: '+name
 return dump(apk,match.group(1))
def attr_bool(text,name,default=False):
 m=re.search(r'android:'+name+r'\([^)]*\)=(true|false|(?:\(type 0x12\))?0x[0-9a-f]+)',text)
 if not m:return default
 value=m.group(1)
 return value=='true' or (value!='false' and int(value.split(')')[-1],16)!=0)
for variant in ['debug','release']:
 apk=root/'app/build/outputs/apk'/variant/('app-debug.apk' if variant=='debug' else 'app-release-unsigned.apk')
 text=dump(apk);assert attr_bool(text,'debuggable')==(variant=='debug'),variant+': incorrect debuggable flag'
 assert not attr_bool(text,'allowBackup',True),variant+': backup enabled'
 assert not attr_bool(text,'usesCleartextTraffic',True),variant+': cleartext enabled'
 assert 'networkSecurityConfig' in text
 blocks=re.split(r'(?m)(?=^\s*E: (?:activity|service|provider|receiver) \()',text)
 components=[b for b in blocks if re.match(r'\s*E: (?:activity|service|provider|receiver) \(',b)]
 assert len(components)>=4,'Component parsing failed'
 for block in components:
  name=re.search(r'android:name\([^)]*\)="([^"]+)"',block).group(1)
  assert 'android:exported' in block,variant+': missing exported on '+name
  assert attr_bool(block,'exported')==name.endswith('.StudioActivity'),variant+': unwanted export '+name
  if name.endswith('.ExportFileProvider'):assert attr_bool(block,'grantUriPermissions'),'Missing URI grants'
 for permission in ['WRITE_EXTERNAL_STORAGE','POST_NOTIFICATIONS','FOREGROUND_SERVICE_DATA_SYNC','FOREGROUND_SERVICE_MEDIA_PROCESSING']:assert permission in text
 assert re.search(r'maxSdkVersion[^\n]*(?:0x1c|28)',text),'Storage permission not SDK bounded'
 service_type=re.search(r'foregroundServiceType\([^)]*\)=(0x[0-9a-f]+)',text);assert service_type and int(service_type.group(1),16)==0x2001,'Incorrect FGS types'
 paths=resource_xml(apk,'file_paths');assert 'Download/Motion2Video/' in paths and 'name="file"' in paths
 assert 'root-path' not in paths and 'cache-path' not in paths and 'external-files-path' not in paths
 network=resource_xml(apk,'network_security_config');assert 'cleartextTrafficPermitted=false' in network and 'src="system"' in network and 'domain-config' not in network and 'src="user"' not in network
 with zipfile.ZipFile(apk) as z:
  assert z.read('assets/studio.html')==(root/'ui/Motion2Video-Studio.html').read_bytes()
  assert not [n for n in z.namelist() if n.endswith(('.jks','.keystore','.pem','.key'))]
 print('PASS:',variant,'compiled manifest boundaries, FileProvider roots, network XML and canonical UI.')
mapping=root/'app/build/outputs/mapping/release/mapping.txt';assert mapping.is_file(),'R8 mapping absent'
entries=re.findall(r'(?m)^(dev\.motion2video\.app\.\S+) -> (\S+):$',mapping.read_text());assert any(a!=b for a,b in entries),'No actual application obfuscation found'
print('PASS: R8 produced obfuscated app classes; release resources and manifest survived shrinking.')
