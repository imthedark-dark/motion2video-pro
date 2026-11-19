#!/usr/bin/env python3
"""Test production error filtering and desktop diagnostic controls; no real user data."""
from pathlib import Path
import json,os,subprocess,tempfile,sys
root=Path(__file__).resolve().parents[1];out=root/'build/privacy-test';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(out),str(root/'app/src/main/java/dev/motion2video/app/PublicErrors.java'),str(root/'tests/java/PublicErrorsTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'dev.motion2video.app.PublicErrorsTest'],check=True)
with tempfile.TemporaryDirectory(prefix='motion-privacy-') as tmp:
 p=Path(tmp);fake=p/'ffmpeg';record=p/'args.json';marker='PRIVATE_FIXTURE_PATH_AND_HTML'
 fake.write_text('#!'+sys.executable+'\nimport os,sys,json\nopen(os.environ["M2V_TEST_ARGS"],"w").write(json.dumps(sys.argv[1:]))\nprint("'+marker+'")\nprint("'+marker+'",file=sys.stderr)\nsys.exit(int(os.environ.get("M2V_TEST_EXIT","0")))\n');fake.chmod(0o700)
 env=dict(os.environ,PATH=str(p)+os.pathsep+os.environ['PATH'],M2V_TEST_ARGS=str(record))
 args=[sys.executable,str(root/'desktop/master_export.py'),str(p/'private-input.mp4'),str(p/'private-output.mp4'),'--audio',str(p/'private-audio.m4a'),'--fps','30']
 expected=['-hide_banner','-y','-i',str(p/'private-input.mp4'),'-i',str(p/'private-audio.m4a'),'-map','0:v:0','-c:v','libx264','-crf','17','-preset','medium','-pix_fmt','yuv420p','-r','30','-fps_mode','cfr','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-color_range','tv','-map','1:a:0','-c:a','aac','-ar','48000','-ac','2','-b:a','256k','-shortest','-movflags','+faststart',str(p/'private-output.mp4')]
 for fail in (False,True):
  result=subprocess.run(args,env=dict(env,M2V_TEST_EXIT='1' if fail else '0'),capture_output=True,text=True)
  assert (result.returncode!=0)==fail and marker not in result.stdout+result.stderr and str(p) not in result.stdout+result.stderr
  assert json.loads(record.read_text())==expected,'Encoding/audio/container arguments changed'
 result=subprocess.run(args+['--debug'],env=env,capture_output=True,text=True);assert result.returncode==0 and marker in result.stderr
 print('PASS: desktop success/failure suppress diagnostics by default; explicit --debug exposes fixture diagnostics; encoding arguments unchanged')
