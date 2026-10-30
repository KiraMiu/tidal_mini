#!/usr/bin/env python3
"""Core state-machine tests and APK asset provenance validation."""
from pathlib import Path
import hashlib
import json
import subprocess
import datetime

root=Path(__file__).resolve().parents[1]
out=root/'build/tests'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
out.mkdir(parents=True)
sources=sorted((root/'app/src/main/java/io/moreless/tide/rebuild/core').glob('*.java'))
subprocess.run(['javac','-d',str(out),*map(str,sources),*map(str,sorted((root/'tests').glob('*.java')))],check=True)
subprocess.run(['java','-cp',str(out),'CoreTest'],check=True)
subprocess.run(['java','-cp',str(out),'SleepTest'],check=True)
subprocess.run(['java','-cp',str(out),'PlaybackVolumeTest'],check=True)
assets=root/'app/src/main/assets'
for item in json.loads((root/'ASSET_PROVENANCE.json').read_text()):
    actual=hashlib.sha256((assets/item['path']).read_bytes()).hexdigest()
    assert actual==item['sha256'], item['path']
scenes=json.loads((assets/'presets/scenes.json').read_text())
local=[s for s in scenes if (assets/'sounds'/str(s.get('resource',{}).get('bundle'))).is_file()]
assert len(local)==7
modes=json.loads((assets/'presets/breath/config.json').read_text())['breath_modes']
assert {m['mode'] for m in modes}=={'4-0-4-0','4-7-8-0','box'}
print('PASS: 9 asset hashes; 7 playable scenes; 3 current breathing modes')

for item in json.loads((root/'SLEEP_ART_PROVENANCE.json').read_text()):
    assert hashlib.sha256((assets/item['path']).read_bytes()).hexdigest()==item['sha256'],item['path']
print('PASS: original sleep artwork hashes')

import xml.etree.ElementTree as ET
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
permissions={e.get(ns+'name') for e in manifest.findall('uses-permission')}
assert 'android.permission.RECORD_AUDIO' not in permissions
app=manifest.find('application')
activities={e.get(ns+'name'):e for e in app.findall('activity')}
assert activities['.MainActivity'].get(ns+'enabled')=='false'
assert any(e.get(ns+'name')=='android.intent.category.LAUNCHER' for e in activities['.SleepActivity'].iter('category'))
print('PASS: hidden entry points, sleep launcher, no microphone permission')

recovered=json.loads((assets/'presets/recovered_scenes.json').read_text())
assert any(s['name']['zh-Hans']=='绵雨' for s in recovered)
assert len(recovered)==1 and not any(s.get('rebuild_demo') for s in recovered)
assert not list((assets/'sounds').glob('original_demo_*'))
assert len(local)+len(recovered)==8
print('PASS: 8 complete sound choices; no preview entries or preview audio in application assets')

# The essential Drizzle selection must use the complete original, not a preview.
import base64
for full in json.loads((root/'FULL_SOUND_PROVENANCE.json').read_text()):
    data=(assets/full['path']).read_bytes()
    assert len(data)==full['bytes'] and hashlib.sha256(data).hexdigest()==full['sha256']
    blocks=[hashlib.sha1(data[i:i+4194304]).digest() for i in range(0,len(data),4194304)]
    etag=base64.urlsafe_b64encode((b'\x16'+blocks[0]) if len(blocks)==1 else (b'\x96'+hashlib.sha1(b''.join(blocks)).digest())).decode()
    assert etag==full['resource_hash']
    selected=next(scene for scene in recovered if scene['name']['zh-Hans']==full['name'])
    assert selected['resource']['bundle']==Path(full['path']).name and not selected.get('rebuild_demo',False)
print('PASS: complete original Drizzle selected; byte size, SHA-256 and original resource hash verified')

icon=json.loads((root/'APP_ICON_PROVENANCE.json').read_text())
assert hashlib.sha256((root/icon['path']).read_bytes()).hexdigest()==icon['sha256']
for scene in local+recovered:
    bundle=scene['resource']['bundle']
    cover='light_original.jpg' if bundle=='alarm_1.m4a' else 'preset_wakeup_forest.webp' if bundle=='preset_wakeup_forest.m4a' else 'scene_'+scene['id']+'.png'
    assert (assets/'art'/cover).is_file(),scene['name']['zh-Hans']
print('PASS: all 8 sound covers exist; supplied app icon hash verified')
