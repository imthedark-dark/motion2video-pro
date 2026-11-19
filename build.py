#!/usr/bin/env python3
"""Build through the checked Gradle wrapper (AndroidX merge and release R8/shrinker).

python3 build.py           -> contributor debug APK
python3 build.py --release -> minified, non-debuggable, unsigned release APK
Add --sign to --release to read private release signing settings (never publish a QA key).
"""
import argparse,json,re,os,pathlib,shutil,subprocess
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--release',action='store_true');p.add_argument('--sign',action='store_true');a=p.parse_args()
if a.sign and not a.release:p.error('--sign requires --release')
root=pathlib.Path(__file__).resolve().parent;variant='release' if a.release else 'debug'
env=os.environ.copy();env.setdefault('GRADLE_USER_HOME',str(root/'build/gradle-home'))
env.setdefault('ANDROID_USER_HOME',str(root/'build/android-user'));pathlib.Path(env['ANDROID_USER_HOME']).mkdir(parents=True,exist_ok=True)
if env.get('ANDROID_SDK_ROOT'):env.setdefault('ANDROID_HOME',env['ANDROID_SDK_ROOT'])
launcher=[str(root/'gradlew.bat')] if os.name=='nt' else ['sh',str(root/'gradlew')]
subprocess.run(launcher+['--no-daemon','-Pm2vReleaseSigning='+('true' if a.sign else 'false'),':app:assemble'+variant.capitalize()],cwd=root,env=env,check=True)
source=root/'app/build/outputs/apk'/variant/(('app-release.apk' if a.sign else 'app-release-unsigned.apk') if a.release else 'app-debug.apk')
dist=root/'dist';dist.mkdir(exist_ok=True)
metadata=json.loads((source.parent/'output-metadata.json').read_text())
element=metadata['elements'][0];version=element['versionName']
if len(metadata['elements'])!=1 or element['outputFile']!=source.name or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?',version):raise ValueError('Unexpected APK output metadata')
name='Motion2Video-'+version+'-Dark'+(('-release' if a.sign else '-release-unsigned') if a.release else '')+'.apk';apk=dist/name;shutil.copyfile(source,apk)
subprocess.run([os.sys.executable,str(root/'tools/checksums.py'),'generate',str(dist)],check=True)
print(f'APK ready: {apk} ({apk.stat().st_size:,} bytes)')
