#!/usr/bin/env python3
"""Rebuild the extra catalogue from verified FULL_SOUND_PROVENANCE only; no demo download."""
from pathlib import Path
import json,hashlib
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
extra=[]
for full in json.loads((root/'FULL_SOUND_PROVENANCE.json').read_text()):
    audio=assets/full['path']
    assert hashlib.sha256(audio.read_bytes()).hexdigest()==full['sha256'],full['name']
    # Keep metadata in the project so a source archive can rebuild without the recovered APK.
    existing=json.loads((assets/'presets/recovered_scenes.json').read_text())
    scene=next(s for s in existing if s['name']['zh-Hans']==full['name'])
    scene['resource']['bundle']=audio.name
    scene.pop('rebuild_demo',None)
    extra.append(scene)
(assets/'presets/recovered_scenes.json').write_text(json.dumps(extra,ensure_ascii=False,indent=2)+'\n')
print('Verified complete sounds:',', '.join(s['name']['zh-Hans'] for s in extra))
