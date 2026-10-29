#!/usr/bin/env python3
"""Recover only public demo URLs explicitly present in the original APK metadata."""
import json, pathlib, subprocess, hashlib, urllib.request, datetime
root=pathlib.Path(__file__).resolve().parents[1]
original=root.parent/'recovered/assets'
assets=root/'app/src/main/assets'
known={s['name']['zh-Hans'] for s in json.loads((original/'presets/scenes.json').read_text())}
extra=[]; provenance=[]
for source in ['presets/breath/breath_recommend_scenes.json','presets/guide_focus_scenes.json']:
 for scene in json.loads((original/source).read_text()):
  name=scene['name']['zh-Hans']; res=scene['resource']; url=res.get('demo_sound_url')
  if name in known or not url:continue
  known.add(name)
  filename='original_demo_'+url.rsplit('/',1)[1]+'.mp3'; path=assets/'sounds'/filename
  try:
   if not path.exists():
    with urllib.request.urlopen(url,timeout=25) as r:
     if not r.headers.get('Content-Type','').startswith('audio/'):raise ValueError('not audio')
     data=r.read(20*1024*1024)
    path.write_bytes(data)
   probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration:stream=codec_name,codec_type','-of','json',str(path)]))
   if not any(s['codec_type']=='audio' for s in probe['streams']):raise ValueError('no audio stream')
   scene['resource']['bundle']=filename; scene['rebuild_demo']=True
   extra.append(scene)
   provenance.append({'path':'sounds/'+filename,'name':name,'source_metadata':source,'url':url,'kind':'original_public_demo','duration_seconds':float(probe['format']['duration']),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
   print(name,probe['format']['duration'],flush=True)
  except Exception as e:print('UNAVAILABLE',name,str(e),flush=True)
# A later full recovery must never be downgraded back to a public demo.
full_manifest=root/'FULL_SOUND_PROVENANCE.json'
if full_manifest.exists():
 for full in json.loads(full_manifest.read_text()):
  full_file=assets/full['path']
  if hashlib.sha256(full_file.read_bytes()).hexdigest()!=full['sha256']:raise ValueError('Full sound hash mismatch')
  for scene in extra:
   if scene['name']['zh-Hans']==full['name']:
    scene['resource']['bundle']=full_file.name;scene.pop('rebuild_demo',None)
(assets/'presets/recovered_scenes.json').write_text(json.dumps(extra,ensure_ascii=False,indent=2)+'\n')
(root/'RECOVERED_SOUND_PROVENANCE.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
