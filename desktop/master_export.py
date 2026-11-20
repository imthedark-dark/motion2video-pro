#!/usr/bin/env python3
"""Optional desktop FFmpeg export. Not a bundled Android encoder."""
import argparse,subprocess,pathlib
p=argparse.ArgumentParser();p.add_argument('input');p.add_argument('output');p.add_argument('--preset',choices=['avc','hevc','av1','vp9','vp8','prores422','prores4444','flac','pcm'],default='avc');p.add_argument('--audio');p.add_argument('--debug',action='store_true',help='Local FFmpeg diagnostics; may expose input paths/metadata');p.add_argument('--fps',type=int,choices=[30,60],default=60);a=p.parse_args()
presets={'avc':('libx264',['-crf','17','-preset','medium','-pix_fmt','yuv420p'],'.mp4'),'hevc':('libx265',['-crf','18','-tag:v','hvc1','-pix_fmt','yuv420p'],'.mp4'),'av1':('libsvtav1',['-crf','18','-preset','8','-pix_fmt','yuv420p'],'.mp4'),'vp9':('libvpx-vp9',['-crf','18','-b:v','0','-pix_fmt','yuv420p'],'.webm'),'vp8':('libvpx',['-crf','10','-b:v','12M','-pix_fmt','yuv420p'],'.webm'),'prores422':('prores_ks',['-profile:v','2','-pix_fmt','yuv422p10le'],'.mov'),'prores4444':('prores_ks',['-profile:v','4','-pix_fmt','yuva444p10le'],'.mov')}
cmd=['ffmpeg','-hide_banner','-y','-i',a.input]
if a.audio:cmd+=['-i',a.audio]
if a.preset in ['flac','pcm']:
 ext='.flac' if a.preset=='flac' else '.wav'
 if pathlib.Path(a.output).suffix.lower()!=ext:p.error('Audio output must end in '+ext)
 cmd+=['-vn','-c:a','flac' if a.preset=='flac' else 'pcm_s24le','-ar','48000','-ac','2']
else:
 encoder,options,ext=presets[a.preset]
 if pathlib.Path(a.output).suffix.lower()!=ext:p.error('Container must be '+ext)
 cmd+=['-map','0:v:0','-c:v',encoder,*options,'-r',str(a.fps),'-fps_mode','cfr','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-color_range','tv']
 if a.audio:cmd+=['-map','1:a:0','-c:a','libopus' if ext=='.webm' else 'pcm_s24le' if ext=='.mov' else 'aac','-ar','48000','-ac','2']+(['-b:a','256k'] if ext in ['.mp4','.webm'] else [])+['-shortest']
 else:cmd+=['-an']
 if ext=='.mp4':cmd+=['-movflags','+faststart']
cmd+=[a.output]
try:
 result=subprocess.run(cmd,stdout=None if a.debug else subprocess.DEVNULL,stderr=None if a.debug else subprocess.DEVNULL)
except OSError:
 p.exit(1,'FFmpeg could not start. Check the local installation.\n')
if result.returncode:
 p.exit(1,'Export failed. Check the input, codec and free space; --debug enables local diagnostics.\n')
