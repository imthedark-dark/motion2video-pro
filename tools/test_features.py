#!/usr/bin/env python3
"""Pure production feature-policy checks; native codecs require device acceptance."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];out=root/'build/feature-test';out.mkdir(parents=True,exist_ok=True);java=root/'app/src/main/java/dev/motion2video/app'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(out),str(java/'CapabilityPolicy.java'),str(root/'tests/java/CapabilityPolicyTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'CapabilityPolicyTest'],check=True)
