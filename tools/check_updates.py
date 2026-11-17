#!/usr/bin/env python3
"""Read-only dependency update check; does not execute/download replacement code."""
import urllib.request,json,datetime,pathlib
current={'gsap':'3.13.0','lottie-web':'5.13.0','three':'0.160.1'}
report={'checked_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'packages':[],'policy':'Check -> pin -> regression tests -> sign a new APK. No automatic code replacement.'}
for package,version in current.items():
 item={'package':package,'bundled':version,'source':'https://registry.npmjs.org/'+package+'/latest'}
 try:
  with urllib.request.urlopen(item['source'],timeout=20) as response:latest=json.load(response)['version']
  item.update(latest=latest,update_available=latest!=version)
 except Exception as e:item['error']=str(e)
 report['packages'].append(item)
report['other_sources']={'fonts':'https://github.com/google/fonts','ffmpeg':'https://ffmpeg.org/download.html','gstreamer':'https://gstreamer.freedesktop.org/download/','android_webview':'https://play.google.com/store/apps/details?id=com.google.android.webview'}
path=pathlib.Path(__file__).resolve().parents[1]/'tests/update-report.json';path.write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
