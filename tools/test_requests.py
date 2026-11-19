#!/usr/bin/env python3
"""Compile actual request/bridge sources with a supplied JVM JSON runtime.
Not an Android instrumentation substitute; no framework/codec APIs are called.
"""
import argparse,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--android-jar',required=True,type=Path);p.add_argument('--json-jar',required=True,type=Path);a=p.parse_args()
for jar in (a.android_jar,a.json_jar):
 if not jar.is_file():p.error(f'Jar not found: {jar}')
root=Path(__file__).resolve().parents[1];out=root/'build/request-test';out.mkdir(parents=True,exist_ok=True)
java=root/'app/src/main/java/dev/motion2video/app';classpath=str(a.json_jar.resolve())+':'+str(a.android_jar.resolve())
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-cp',classpath,'-d',str(out),*[str(java/n) for n in ('AudioPolicy.java','AudioOptions.java','CapabilityPolicy.java','ImportPolicy.java','CodecSupport.java','RenderRange.java','RenderRequest.java','BridgeValidation.java')],str(root/'tests/java/RequestValidationTest.java')],check=True)
subprocess.run(['java','-cp',str(out)+':'+classpath,'dev.motion2video.app.RequestValidationTest'],check=True)
