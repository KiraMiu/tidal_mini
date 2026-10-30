from pathlib import Path
import subprocess,time,re,json,xml.etree.ElementTree as ET
ADB='/Users/cxmacstudio01/Library/Android/sdk/platform-tools/adb';SERIAL='49VGOR99Z985YTIB';PKG='io.moreless.tide.rebuild';OUT=Path(__file__).resolve().parent

def adb(*args):return subprocess.run([ADB,'-s',SERIAL,*args],check=True,capture_output=True).stdout
def shell(*args):return adb('shell',*args).decode()
def prefs(name):return {n.get('name'):n.get('value',n.text or '') for n in ET.fromstring(shell('run-as',PKG,'cat',f'/data/user_de/0/{PKG}/shared_prefs/{name}.xml'))}
def ui():
    shell('uiautomator','dump','/data/local/tmp/tidal-v044-ui.xml')
    raw=shell('cat','/data/local/tmp/tidal-v044-ui.xml');tree=ET.fromstring(raw)
    assert any(n.get('package')==PKG for n in tree.iter('node'))
    return tree,raw

def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def tap(n):
    x1,y1,x2,y2=bounds(n);shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
def click(label):
    tree,_=ui();tap(next(n for n in tree.iter('node') if n.get('content-desc')==label or n.get('text')==label));time.sleep(.2)
def open_volume():
    click('设置');tree,_=ui();tap(next(n for n in tree.iter('node') if n.get('text','').startswith('背景音乐音量 · ')));time.sleep(.2)
def shot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'));_,raw=ui();(OUT/(name+'.xml')).write_text(raw)
def move(ratio):
    tree,_=ui();slider=next(n for n in tree.iter('node') if n.get('class')=='android.widget.SeekBar')
    x1,y1,x2,y2=bounds(slider);pad=round(16*int(re.findall(r'\d+',shell('wm','density'))[-1])/160)
    x=round(x1+pad+(x2-x1-2*pad)*ratio);y=(y1+y2)//2
    shell('input','swipe',str((x1+x2)//2),str(y),str(x),str(y),'350');time.sleep(.2)
    gain=float(prefs('sleep_audio')['volumeGain']);assert abs(gain-ratio)<.002,(gain,ratio)
    label=f'{gain*100:.2f}'.rstrip('0').rstrip('.')+'%';tree,_=ui();assert any(n.get('text')==label for n in tree.iter('node')),(gain,label)
    return gain,label

def native(expected):
    data=shell('dumpsys','media.player');block=data[data.index('packageName: '+PKG):].split('\n\n',1)[0]
    pairs=[list(map(float,m)) for m in re.findall(r'left - right volume\(([\d.]+), ([\d.]+)\)',block)]
    assert len(pairs)==2 and all(abs(v-expected)<.00001 for pair in pairs for v in pair),pairs
    return block,pairs

def volumes():
    data=shell('dumpsys','audio');out={}
    for name in ['MUSIC','ALARM']:
        m=re.search(r'^- STREAM_'+name+r':\n(.*?)(?=^- STREAM_|\Z)',data,re.M|re.S);assert m
        out[name]=[line.strip() for line in m.group(1).splitlines() if any(k in line for k in ['Muted:','Muted Internally:','Current:','Devices:'])]
    return out

before={'audio':prefs('sleep_audio'),'alarm':prefs('sleep_alarm'),'systemVolumes':volumes()}
assert before['audio'].get('wanted')=='false' and before['alarm'].get('phase')=='off'
(OUT/'before.json').write_text(json.dumps(before,ensure_ascii=False,indent=2))
shell('am','start','-n',PKG+'/.SleepActivity');time.sleep(.3);open_volume()
tree,_=ui();assert any(n.get('text')=='100%' for n in tree.iter('node'))
assert 'volumeGain' not in before['audio'] and before['audio']['volumeStep']=='10'
shot('continuous-default')
samples=[]
for ratio in [.123,.375,.687]:
    gain,label=move(ratio);assert abs(gain*10-round(gain*10))>.01
    samples.append({'target':ratio,'savedGain':gain,'label':label});print(samples[-1],flush=True)
move(0);shell('input','keyevent','4');click('播放声音');time.sleep(.5)
assert prefs('sleep_audio')['status']=='playing';block,pairs=native(0);(OUT/'playing-zero.txt').write_text(block)
click('暂停声音');open_volume();gain,label=move(.375);block,pairs=native(gain)
assert 'paused(1)' in block;(OUT/'paused-fractional.txt').write_text(block);shot('continuous-fractional')
shell('am','force-stop',PKG);shell('am','start','-n',PKG+'/.SleepActivity');time.sleep(.3);open_volume()
assert float(prefs('sleep_audio')['volumeGain'])==gain
tree,_=ui();assert any(n.get('text')==label for n in tree.iter('node'));shot('continuous-reopened')
move(1);shell('input','keyevent','4');shell('am','force-stop',PKG);shell('am','start','-n',PKG+'/.SleepActivity')
after={'audio':prefs('sleep_audio'),'alarm':prefs('sleep_alarm'),'systemVolumes':volumes()}
assert before['alarm']==after['alarm'] and before['systemVolumes']==after['systemVolumes']
assert after['audio']['volumeGain']=='1.0' and after['audio']['wanted']=='false'
assert 'ServiceRecord{' not in shell('dumpsys','activity','services',PKG)
(OUT/'after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2))
(OUT/'summary.json').write_text(json.dumps({'samples':samples,'fractionalNativeGain':pairs,'reopenedLabel':label,'legacy100PercentPreserved':True,'finalGain':1.0,'systemVolumesAndAlarmUnchanged':True,'playbackStopped':True},ensure_ascii=False,indent=2))
print('PASS fractional drag/display/storage/native stereo gain/reopen; restored 100%, no active service; alarm and system volumes unchanged',flush=True)
