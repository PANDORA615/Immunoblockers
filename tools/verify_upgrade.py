"""Exercise the published signing identity and existing Room data on a disposable rooted emulator."""
from pathlib import Path
import re
import sqlite3
import struct
import zlib
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args, **kwargs):
    return subprocess.run(['adb', *args], check=True, **kwargs)


def screen():
    adb('shell','uiautomator','dump','/sdcard/window.xml',stdout=subprocess.DEVNULL)
    return ET.fromstring(subprocess.check_output(['adb','shell','cat','/sdcard/window.xml']))


def click(text=None, field=False):
    node=next(n for n in screen().iter('node') if (n.get('class')=='android.widget.EditText' if field else n.get('text')==text))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.4)


folder=Path('upgrade-evidence');folder.mkdir(exist_ok=True)
for attempt in range(5):
    # adbd can close the first connection while switching to root immediately after boot.
    subprocess.run(['adb','root'],check=False)
    adb('wait-for-device')
    if subprocess.check_output(['adb','shell','id','-u'],text=True).strip()=='0':break
    time.sleep(1)
else:raise RuntimeError('Disposable emulator did not provide root access')
adb('install','distribution/Immunoblockers-0.4.0-universal.apk')
adb('shell','am','start','-W','-n','com.immunoblockers/.MainActivity')
time.sleep(2)
click('Мой профиль');click(field=True)
adb('shell','input','text','UpgradeProfile')
adb('shell','input','keyevent','4')
click('Сохранить ник');click('Назад')
assert any(n.get('text')=='UpgradeProfile' for n in screen().iter('node'))
adb('shell','am','force-stop','com.immunoblockers')
# Set the old 0.4.0 widget-photo preference in the real DataStore protobuf.
# The new wallpaper key must remain absent to exercise the compatibility fallback.
def string_field(number,value):
    data=value.encode() if isinstance(value,str) else value
    assert len(data)<128
    return bytes([number*8+2,len(data)])+data
photo_path='/data/user/0/com.immunoblockers/files/images/upgrade-wallpaper.png'
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+bytes([35,115,195])*32)*32))+chunk(b'IEND',b'')
photo=folder/'wallpaper.png';photo.write_bytes(png)
adb('shell','mkdir','-p','/data/user/0/com.immunoblockers/files/images')
adb('push',str(photo),photo_path)
prefs_path='/data/user/0/com.immunoblockers/files/datastore/settings.preferences_pb'
prefs=folder/'settings.pb';adb('pull',prefs_path,str(prefs))
entry=string_field(1,'widgetImage')+string_field(2,string_field(5,photo_path))
prefs.write_bytes(prefs.read_bytes()+string_field(1,entry))
adb('push',str(prefs),prefs_path)
remote='/data/user/0/com.immunoblockers/databases'
adb('pull',remote,str(folder/'databases'))
db=folder/'databases/immunoblockers.db'
with sqlite3.connect(db) as connection:
    connection.execute("INSERT INTO wellbeing_entries(id,recordedAt,mood,text,groupId) VALUES('upgrade-test',123456,NULL,'Upgrade journal survives',NULL)")
    connection.execute("INSERT INTO medications VALUES('upgrade-med','Upgrade medicine','1','мг','preserve','2026-10-01',NULL,'1,2,3,4,5,6,7','09:00',0,1,1,123456,123456)")
    connection.execute("INSERT INTO occurrences VALUES('upgrade-dose','upgrade-med',1,'2026-10-01','09:00',123456,'Upgrade medicine','1','мг','taken',123456,NULL,NULL)")
    connection.commit()
    connection.execute('PRAGMA wal_checkpoint(TRUNCATE)')
adb('push',str(db),remote+'/immunoblockers.db')
adb('shell','rm','-f',remote+'/immunoblockers.db-wal',remote+'/immunoblockers.db-shm')
adb('install','-r','distribution/Immunoblockers-0.4.1-universal.apk')
adb('shell','am','start','-W','-n','com.immunoblockers/.MainActivity')
time.sleep(2)
root=screen()
assert any(n.get('text')=='UpgradeProfile' for n in root.iter('node')),'Profile disappeared after update'
ET.ElementTree(root).write(folder/'updated-home.xml',encoding='utf-8')
with (folder/'updated-home.png').open('wb') as output:adb('exec-out','screencap','-p',stdout=output)
buttons=[n for n in root.iter('node') if 'Добавить лекарство' in (n.get('text','')+' '+n.get('content-desc',''))]
assert len(buttons)==1,f'Expected one add button, found {len(buttons)}: '+ET.tostring(root,encoding='unicode')
click('Настройки')
for attempt in range(5):
    if any(n.get('text')=='Обои выбраны' for n in screen().iter('node')):break
    adb('shell','input','swipe','540','1350','540','600','450')
else:raise AssertionError('Legacy widget photo was not adopted as app wallpaper')
with (folder/'wallpaper-migration.png').open('wb') as output:adb('exec-out','screencap','-p',stdout=output)
click('Сегодня')
adb('shell','am','force-stop','com.immunoblockers')
adb('pull',remote,str(folder/'after-databases'))
with sqlite3.connect(folder/'after-databases/immunoblockers.db') as connection:
    assert connection.execute("SELECT text,mood,recordedAt FROM wellbeing_entries WHERE id='upgrade-test'").fetchone()==('Upgrade journal survives',None,123456)
    assert connection.execute("SELECT name,archived FROM medications WHERE id='upgrade-med'").fetchone()==('Upgrade medicine',1)
    assert connection.execute("SELECT state,markedAt FROM occurrences WHERE id='upgrade-dose'").fetchone()==('taken',123456)
    assert connection.execute('PRAGMA user_version').fetchone()==(2,)
adb('shell','am','start','-W','-n','com.immunoblockers/.MainActivity')
time.sleep(1)
with (folder/'updated-home.png').open('wb') as output:adb('exec-out','screencap','-p',stdout=output)
(folder/'result.txt').write_text('PASS: real published 0.4.0 -> signed 0.4.1; profile, legacy wallpaper, medication, taken dose, journal, Room v2 preserved; one add button.\n')
print((folder/'result.txt').read_text())
# Only screenshots and the result are uploaded; private app data stays on the disposable runner.
