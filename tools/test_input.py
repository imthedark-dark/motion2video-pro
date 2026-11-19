#!/usr/bin/env python3
"""Exercise the actual production policies without Android mocks."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];out=root/'build/input-test';out.mkdir(parents=True,exist_ok=True)
java=root/'app/src/main/java/dev/motion2video/app'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(out),str(java/'ImportPolicy.java'),str(java/'RenderBudget.java'),str(root/'tests/java/InputProtectionTest.java')],check=True)
subprocess.run(['java','-Xmx512m','-cp',str(out),'InputProtectionTest'],check=True)
