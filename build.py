#!/usr/bin/env python3
"""Build through the checked Gradle wrapper (AndroidX merge and release R8/shrinker).

python3 build.py           -> contributor debug APK
python3 build.py --release -> minified, non-debuggable, unsigned release APK
Production release signing is intentionally deferred to Phase 4.
"""
import argparse,hashlib,os,pathlib,shutil,subprocess
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--release',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parent;variant='release' if a.release else 'debug'
env=os.environ.copy();env.setdefault('GRADLE_USER_HOME',str(root/'build/gradle-home'))
env.setdefault('ANDROID_USER_HOME',str(root/'build/android-user'));pathlib.Path(env['ANDROID_USER_HOME']).mkdir(parents=True,exist_ok=True)
if env.get('ANDROID_SDK_ROOT'):env.setdefault('ANDROID_HOME',env['ANDROID_SDK_ROOT'])
launcher=[str(root/'gradlew.bat')] if os.name=='nt' else ['sh',str(root/'gradlew')]
subprocess.run(launcher+['--no-daemon',':app:assemble'+variant.capitalize()],cwd=root,env=env,check=True)
source=root/'app/build/outputs/apk'/variant/('app-release-unsigned.apk' if a.release else 'app-debug.apk')
dist=root/'dist';dist.mkdir(exist_ok=True)
name='Motion2Video-1.4.1-Dark'+('-release-unsigned' if a.release else '')+'.apk';apk=dist/name;shutil.copyfile(source,apk)
(dist/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(dist.glob('*.apk'))))
print(f'APK ready: {apk} ({apk.stat().st_size:,} bytes)')
