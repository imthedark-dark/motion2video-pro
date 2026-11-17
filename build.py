#!/usr/bin/env python3
"""Dependency-free debug APK builder using the official Android SDK and a JDK.

Run: ANDROID_SDK_ROOT=/path/to/sdk python3 build.py
Android Studio/Gradle is also supported by the included project files.
"""
import os, pathlib, subprocess, zipfile, shutil, hashlib, xml.etree.ElementTree as ET

P=pathlib.Path(__file__).resolve().parent
SDK=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or pathlib.Path.home()/'Android'/'Sdk')
TOOLS=SDK/'build-tools/35.0.0'; ANDROID=SDK/'platforms/android-35/android.jar'
B=P/'build';DIST=P/'dist';B.mkdir(exist_ok=True);DIST.mkdir(exist_ok=True)
# The standalone file is the canonical UI source for both browser and Android.
shutil.copyfile(P/'ui/Motion2Video-Studio.html',P/'app/src/main/assets/studio.html')
def run(*args):subprocess.run([str(x) for x in args],check=True,cwd=P)
for p in [TOOLS/'aapt2',TOOLS/'d8',TOOLS/'apksigner',TOOLS/'zipalign',ANDROID]:
    if not p.exists():raise SystemExit(f'Missing SDK component: {p}. Install platform 35 and build-tools 35.0.0.')
for n in ['classes','dex','gen']:
    d=B/n
    if d.exists():shutil.rmtree(d)
    d.mkdir()
run(TOOLS/'aapt2','compile','--dir',P/'app/src/main/res','-o',B/'resources.zip')
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest=ET.parse(P/'app/src/main/AndroidManifest.xml')
manifest.getroot().set('package','dev.motion2video.app')
manifest.write(B/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(TOOLS/'aapt2','link','-I',ANDROID,'--manifest',B/'AndroidManifest.xml',
    '--java',B/'gen','--min-sdk-version','26','--target-sdk-version','35',
    '--version-code','5','--version-name','1.3.1','-A',P/'app/src/main/assets','-o',B/'base.apk',B/'resources.zip')
sources=list((P/'app/src/main/java').rglob('*.java'))+list((B/'gen').rglob('*.java'))
run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','17','-target','17','-classpath',ANDROID,'-d',B/'classes',*sources)
run(TOOLS/'d8','--lib',ANDROID,'--min-api','26','--output',B/'dex',*list((B/'classes').rglob('*.class')))
with zipfile.ZipFile(B/'base.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for f in (B/'dex').glob('*.dex'):z.write(f,f.name)
run(TOOLS/'zipalign','-f','4',B/'base.apk',B/'aligned.apk')
key=B/'debug.jks'
if not key.exists():
    run('java','sun.security.tools.keytool.Main','-genkeypair','-keystore',key,'-storepass','android',
        '-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048',
        '-validity','10000','-dname','CN=Dark Motion2Video Debug,O=Local Development,C=US')
apk=DIST/'Motion2Video-1.3.1-Dark.apk'
run(TOOLS/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,B/'aligned.apk')
run(TOOLS/'apksigner','verify','--verbose',apk)
run(TOOLS/'zipalign','-c','4',apk)
(DIST/'SHA256SUMS.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
print(f'APK ready: {apk} ({apk.stat().st_size:,} bytes)')
