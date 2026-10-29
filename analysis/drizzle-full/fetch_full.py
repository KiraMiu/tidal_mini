from pathlib import Path
import re,json,urllib.request,hashlib,base64,subprocess
root=Path(__file__).parent
html=(root/'official-drizzle-share.html').read_text()
urls=[json.loads('"'+v+'"') for v in re.findall(r'share_page_play_url:"([^"]+)"',html)]
url=next(u for u in urls if '/sounds/lnSNpbE0TI_HLFPpdeSkoeIvGjvh?' in u)
expected=12339888;target=root/'drizzle-original-full.m4a'
with target.open('xb') as out:
 for start in range(0,expected,1024*1024):
  end=min(start+1024*1024,expected)-1
  request=urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}'})
  with urllib.request.urlopen(request,timeout=30) as response:
   data=response.read(); cr=response.headers.get('Content-Range')
   assert response.status==206 and cr==f'bytes {start}-{end}/{expected}',(response.status,cr)
   assert len(data)==end-start+1,len(data)
   out.write(data)
  print('bytes',end+1,'/',expected,flush=True)
data=target.read_bytes();blocks=[hashlib.sha1(data[i:i+4194304]).digest() for i in range(0,len(data),4194304)]
etag=base64.urlsafe_b64encode(b'\x96'+hashlib.sha1(b''.join(blocks)).digest()).decode()
assert etag=='lnSNpbE0TI_HLFPpdeSkoeIvGjvh',etag
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration,size,format_name:stream=codec_name,sample_rate,channels','-of','json',str(target)]))
result={'source_page':'https://tide.fm/share/scenes/5c481d18cdcaa900064e4cf2','resource_hash':etag,'expected_bytes':expected,'actual_bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'probe':probe,'note':'Original resource served by the official public share-page playback URL; signature not stored in provenance.'}
(root/'full-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False,indent=2))
