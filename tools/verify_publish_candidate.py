#!/usr/bin/env python3
"""Verify exact staged release bytes/identity; never uploads or publishes."""
import argparse,os,re,subprocess
from pathlib import Path
from checksums import verify
from verify_release import apk

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--directory',type=Path,required=True);p.add_argument('--tag',required=True);p.add_argument('--signer',required=True);a=p.parse_args()
 if not re.fullmatch(r'v\d+\.\d+\.\d+',a.tag) or not re.fullmatch(r'[a-fA-F0-9]{64}',a.signer):p.error('Expected semantic tag and trusted certificate SHA-256')
 name=f'Motion2Video-{a.tag[1:]}-Dark-release.apk'
 if {x.name for x in a.directory.iterdir()}!={name,'SHA256SUMS.txt'}:raise ValueError('Stage only the intended signed APK and checksum manifest')
 paths=verify(a.directory)
 if len(paths)!=1 or paths[0].name!=name:raise ValueError('Unexpected release APK')
 path=paths[0];apk(path)
 sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
 if not sdk:p.error('Set ANDROID_SDK_ROOT for signature/manifest checks')
 tools=Path(sdk)/'build-tools/35.0.0'
 cert=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(path)],text=True)
 fingerprints=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$',cert,re.M)
 if [x.lower() for x in fingerprints]!=[a.signer.lower()]:raise ValueError('APK signer does not match independently trusted fingerprint')
 badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(path)],text=True)
 package=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
 if not package or package[1]!='dev.motion2video.app' or package[3]!=a.tag[1:] or int(package[2])<1 or 'application-debuggable' in badging:raise ValueError('Release package/version/debuggable flag is invalid')
 print('PASS: staged bytes, version, non-debuggable manifest and expected APK signer (verification only)')
if __name__=='__main__':main()
