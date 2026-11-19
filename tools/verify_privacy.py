#!/usr/bin/env python3
"""Guard app-owned diagnostic/telemetry boundaries; not an OS packet-capture claim."""
import argparse,os,re,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1];java=root/'app/src/main/java/dev/motion2video/app'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apks',action='store_true');a=p.parse_args()
for file in java.glob('*.java'):
 text=file.read_text()
 if file.name=='WebViewSecurity.java':
  assert text.count('android.util.Log.d(')==1 and 'if(!debug(context))return;' in text
  assert 'client.first+" cspDirective="+client.directive' in text
  text=text.replace('android.util.Log.d("Motion2VideoWebView"', 'approvedDebugEvent("Motion2VideoWebView"')
 if re.search(r'\bLog\.[vdiewtf]\s*\(|printStackTrace\s*\(|System\.(out|err)\b',text):raise ValueError('Unexpected app diagnostic sink: '+file.name)
 if file.name!='PublicErrors.java' and re.search(r'\.get(?:Localized)?Message\s*\(',text):raise ValueError('Raw exception output bypasses privacy policy: '+file.name)
 if re.search(r'java\.net\.|okhttp|retrofit|crashlytics|firebase|io\.sentry|opentelemetry',text,re.I):raise ValueError('New network/telemetry dependency needs privacy review: '+file.name)
gradle=(root/'app/build.gradle.kts').read_text()
if re.search(r'firebase|crashlytics|sentry|analytics|opentelemetry',gradle,re.I):raise ValueError('Telemetry build dependency added')
security=(java/'WebViewSecurity.java').read_text()
assert 'view.setWebChromeClient(new PrivateWebChromeClient(debug(context)))' in security
for name in ('MainActivity.java','StudioActivity.java','OffscreenStage.java'):
 text=(java/name).read_text();assert 'WebViewSecurity.configure(' in text
 assert 'setWebChromeClient(' not in text,'WebView privacy client overwritten'
android='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();app=manifest.find('application')
assert any(x.get(android+'name')=='android.webkit.WebView.MetricsOptOut' and x.get(android+'value')=='true' for x in app.findall('meta-data'))
assert app.get(android+'allowBackup')=='false'
assert not any(x.get(android+'name') in ('android.permission.CAMERA','android.permission.RECORD_AUDIO','com.google.android.gms.permission.AD_ID') for x in manifest.findall('uses-permission'))
assert 'rel="preconnect"' not in (root/'ui/Motion2Video-Studio.html').read_text()
print('PASS: source diagnostic sinks, error boundary, no owned telemetry client, all WebView console clients and privacy metadata')
if a.apks:
 sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME']);aapt=sdk/'build-tools/35.0.0/aapt2'
 for variant,name in [('debug','app-debug.apk'),('release','app-release-unsigned.apk')]:
  apk=root/'app/build/outputs/apk'/variant/name
  text=subprocess.check_output([str(aapt),'dump','xmltree','--file','AndroidManifest.xml',str(apk)],text=True)
  blocks=re.findall(r'E: meta-data[^\n]*\n.*?(?=\n\s*E:|\Z)',text,re.S)
  block=next(x for x in blocks if '"android.webkit.WebView.MetricsOptOut"' in x)
  assert re.search(r'android:value\([^)]*\)=(?:true|\(type 0x12\)0xffffffff)',block)
  print('PASS:',variant,'compiled APK requests WebView metrics opt-out')
