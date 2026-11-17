from pathlib import Path
import subprocess as sp,zipfile,os
p=Path(__file__).resolve().parents[1];sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'Android'/'Sdk');t=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar';base=p/'tests/instrumentation';b=base/'build';b.mkdir(exist_ok=True)
def run(*a):sp.run([str(x) for x in a],check=True)
for n in ['classes','dex']:(b/n).mkdir(exist_ok=True)
run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','17','-target','17','-classpath',jar,'-d',b/'classes',*list((base/'src').rglob('*.java')))
run(t/'d8','--lib',jar,'--min-api','26','--output',b/'dex',*list((b/'classes').rglob('*.class')))
run(t/'aapt2','link','-I',jar,'--manifest',base/'AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','35','-o',b/'test.apk')
with zipfile.ZipFile(b/'test.apk','a') as z:z.write(b/'dex/classes.dex','classes.dex')
run(t/'zipalign','-f','4',b/'test.apk',b/'aligned.apk')
run(t/'apksigner','sign','--ks',Path(os.environ.get('M2V_DEBUG_KEYSTORE',str(p/'build/debug.jks'))),'--ks-pass','pass:android','--out',b/'signed.apk',b/'aligned.apk')
