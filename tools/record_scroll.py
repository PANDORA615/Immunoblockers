"""Capture the reported long empty home and its scroll boundary on API 35."""
from pathlib import Path
import subprocess
import time
import re


def adb(*args, **kwargs):
    return subprocess.run(['adb', *args], check=True, **kwargs)


adb('shell', 'pm', 'revoke', 'com.immunoblockers', 'android.permission.POST_NOTIFICATIONS')
adb('shell', 'appops', 'set', 'com.immunoblockers', 'SCHEDULE_EXACT_ALARM', 'deny')
adb('shell', 'am', 'force-stop', 'com.immunoblockers')
adb('shell', 'am', 'start', '-n', 'com.immunoblockers/.MainActivity')
time.sleep(2)
folder = Path('test-screenshots')
folder.mkdir(exist_ok=True)
with (folder/'empty-home.png').open('wb') as output:
    adb('exec-out', 'screencap', '-p', stdout=output)
size = subprocess.check_output(['adb', 'shell', 'wm', 'size'], text=True)
w, h = map(int, re.findall(r'(\d+)x(\d+)', size)[-1])
recorder = subprocess.Popen(['adb','shell','screenrecord','--size','540x960','--time-limit','8','/sdcard/scroll.mp4'])
time.sleep(1)
for start, end in [(.68,.43),(.68,.38),(.42,.68),(.42,.72)]:
    adb('shell','input','swipe',str(w//2),str(round(h*start)),str(w//2),str(round(h*end)),'700')
    time.sleep(.3)
recorder.wait(timeout=15)
adb('pull','/sdcard/scroll.mp4',str(folder/'scroll.mp4'))
subprocess.run(['ffmpeg','-y','-i',str(folder/'scroll.mp4'),'-vf','fps=6,scale=180:-1,tile=6x4','-frames:v','2',str(folder/'scroll-frames-%02d.jpg')],check=True)
adb('shell','pm','grant','com.immunoblockers','android.permission.POST_NOTIFICATIONS')
adb('shell','appops','set','com.immunoblockers','SCHEDULE_EXACT_ALARM','allow')
