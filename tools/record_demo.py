"""Record rapid switches in the real app and extract frames for visual review."""
import pathlib
import re
import subprocess
import time


def adb(*args, **kwargs):
    return subprocess.run(['adb', *args], check=True, **kwargs)


for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
    adb('shell', 'settings', 'put', 'global', key, '1')
adb('shell', 'am', 'force-stop', 'com.immunoblockers')
adb('shell', 'am', 'start', '-n', 'com.immunoblockers/.MainActivity')
time.sleep(3)
size = subprocess.check_output(['adb', 'shell', 'wm', 'size'], text=True)
w, h = map(int, re.findall(r'(\d+)x(\d+)', size)[-1])
density = subprocess.check_output(['adb', 'shell', 'wm', 'density'], text=True)
scale = int(re.findall(r'\d+', density)[-1]) / 160
folder = pathlib.Path('test-screenshots')
folder.mkdir(exist_ok=True)
recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--size', '540x960', '--bit-rate', '2500000', '--time-limit', '12', '/sdcard/Immunoblockers-navigation.mp4'])
try:
    time.sleep(1)
    for tab in [1, 2, 3, 4, 0, 4, 1, 3, 0, 2, 4, 0]:
        x = round(12 * scale + (w - 24 * scale) * (.1 + .2 * tab))
        adb('shell', 'input', 'tap', str(x), str(round(h - 70 * scale)))
        time.sleep(.12)
    recorder.wait(timeout=25)
finally:
    if recorder.poll() is None:
        recorder.terminate()
        recorder.wait(timeout=5)
adb('pull', '/sdcard/Immunoblockers-navigation.mp4', str(folder / 'navigation.mp4'))
subprocess.run(['ffmpeg', '-y', '-i', str(folder / 'navigation.mp4'), '-vf', 'fps=12,scale=180:-1,tile=6x4', '-frames:v', '6', str(folder / 'navigation-frames-%02d.jpg')], check=True)
