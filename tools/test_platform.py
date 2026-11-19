#!/usr/bin/env python3
"""Host checks for permission bounds and cancellation-aware output copying."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];out=root/'build/platform-test';out.mkdir(parents=True,exist_ok=True)
java=root/'app/src/main/java/dev/motion2video/app'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),str(java/'PermissionPolicy.java'),str(java/'CancellableCopy.java'),str(root/'tests/java/PlatformProtectionTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'PlatformProtectionTest'],check=True)
