#!/usr/bin/env python3
"""Run converter regression/benchmark checks with the JDK, without Android."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];out=root/'build/yuv-test';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),str(root/'app/src/main/java/dev/motion2video/app/Yuv420Converter.java'),str(root/'tests/java/Yuv420ConverterTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'Yuv420ConverterTest'],check=True)
