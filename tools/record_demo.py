"""Record the real Android tab animation after functional tests; optional emulator artifact."""
import os, re, subprocess, time

def adb(*args,**kwargs):return subprocess.run(['adb',*args],check=True,**kwargs)
try:
    for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
        adb('shell','settings','put','global',key,'1')
    adb('shell','am','start','-n','com.immunoblockers/.MainActivity')
    time.sleep(2)
    size=subprocess.check_output(['adb','shell','wm','size'],text=True)
    w,h=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    recorder=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','2000000','--time-limit','12','/sdcard/Immunoblockers-navigation.mp4'])
    time.sleep(1)
    for tab in [2,3,1,0,3,0]:
        adb('shell','input','tap',str(round(w*(.125+.25*tab))),str(h-250))
        time.sleep(1)
    recorder.wait(timeout=25)
    os.makedirs('test-screenshots',exist_ok=True)
    adb('pull','/sdcard/Immunoblockers-navigation.mp4','test-screenshots/')
except Exception as e:
    print('Optional preview recording unavailable:',type(e).__name__)
