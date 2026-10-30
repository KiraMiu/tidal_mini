from pathlib import Path
import subprocess,sys,time,re,json,xml.etree.ElementTree as ET
ADB='/Users/cxmacstudio01/Library/Android/sdk/platform-tools/adb'
SERIAL='49VGOR99Z985YTIB'
PKG='io.moreless.tide.rebuild'
OUT=Path(__file__).resolve().parent

def adb(*args,binary=False):
    result=subprocess.run([ADB,'-s',SERIAL,*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    return result.stdout if binary else result.stdout.decode()

def shell(*args):return adb('shell',*args)
def prefs(name):
    root=ET.fromstring(shell('run-as',PKG,'cat',f'/data/user_de/0/{PKG}/shared_prefs/{name}.xml'))
    return {node.attrib['name']:node.attrib.get('value',node.text or '') for node in root}
def ui():
    shell('uiautomator','dump','/data/local/tmp/tidal-volume-ui.xml')
    raw=shell('cat','/data/local/tmp/tidal-volume-ui.xml')
    tree=ET.fromstring(raw)
    assert any(n.get('package')==PKG for n in tree.iter('node')),'test app not visible'
    return tree,raw

def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))
def tap_node(node):
    x1,y1,x2,y2=bounds(node);shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
def find(tree,label):
    for node in tree.iter('node'):
        if node.get('content-desc')==label or node.get('text')==label:return node
    raise AssertionError(f'missing UI: {label}')
def click(label):
    tree,_=ui();tap_node(find(tree,label));time.sleep(.25)
def back():shell('input','keyevent','4');time.sleep(.25)
def slider():
    tree,raw=ui();return next(n for n in tree.iter('node') if n.get('class')=='android.widget.SeekBar'),raw

def set_step(step):
    node,_=slider();x1,y1,x2,y2=bounds(node)
    # Application track padding is 16dp, observed density is 450dpi (2.8125px/dp).
    density=int(re.findall(r'\d+',shell('wm','density'))[-1])/160
    pad=round(16*density)
    x=round(x1+pad+(x2-x1-2*pad)*step/10)
    shell('input','tap',str(x),str((y1+y2)//2));time.sleep(.15)
    value=prefs('sleep_audio').get('volumeStep')
    assert value==str(step),(step,value,x)
    tree,raw=ui();find(tree,str(step*10)+'%')
    return {'step':step,'percent':step*10,'stored':value,'tap':[x,(y1+y2)//2]}
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
    _,raw=ui();(OUT/(name+'.xml')).write_text(raw)

action=sys.argv[1]
if action=='open':
    before={'alarm':prefs('sleep_alarm'),'audio':prefs('sleep_audio')}
    assert before['alarm'].get('phase')=='off' and before['audio'].get('wanted')=='false','existing sleep/playback session'
    (OUT/'before.json').write_text(json.dumps(before,ensure_ascii=False,indent=2))
    click('设置');tree,_=ui()
    node=next(n for n in tree.iter('node') if n.get('text','').startswith('背景音乐音量 · '))
    assert '100%' in node.get('text');tap_node(node);time.sleep(.3)
    screenshot('volume-default');node,_=slider()
    print(json.dumps({'default':prefs('sleep_audio').get('volumeStep','unset (100%)'),'slider':node.attrib},ensure_ascii=False))
elif action=='steps':
    results=[set_step(i) for i in range(11)]
    screenshot('volume-100')
    (OUT/'steps.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print(json.dumps(results,ensure_ascii=False))
elif action=='set':
    print(json.dumps(set_step(int(sys.argv[2])),ensure_ascii=False));screenshot('volume-'+sys.argv[2])
elif action=='play':
    assert prefs('sleep_audio').get('volumeStep')=='0','playback must start muted'
    back();click('播放声音');time.sleep(1)
    assert prefs('sleep_audio').get('status')=='playing'
    print(json.dumps(prefs('sleep_audio'),ensure_ascii=False))
elif action=='pause':
    click('暂停声音');assert prefs('sleep_audio').get('status')=='paused'
    click('设置');tree,_=ui();tap_node(next(n for n in tree.iter('node') if n.get('text','').startswith('背景音乐音量 · ')))
    print('paused; volume dialog opened')
elif action=='snapshot':
    screenshot(sys.argv[2]);print(json.dumps(prefs('sleep_audio'),ensure_ascii=False))
elif action=='native':
    data=shell('dumpsys','media.player')
    # media.player reports local track gains; keep the player's section, not other apps' metadata.
    pid=shell('pidof',PKG).strip()
    lines=data.splitlines()
    selected=[]
    for i,line in enumerate(lines):
        if ('pid = '+pid) in line or ('pid='+pid) in line or ('pid: '+pid) in line:
            selected.extend(lines[max(0,i-1):i+90])
    text='\n'.join(selected) if selected else data
    (OUT/('native-'+sys.argv[2]+'.txt')).write_text(text)
    print(text[:18000])
elif action=='system-volume':
    data=shell('dumpsys','audio')
    blocks={}
    for name in ['MUSIC','ALARM']:
        match=re.search(r'^- STREAM_'+name+r':\n(.*?)(?=^- STREAM_|\Z)',data,re.M|re.S)
        assert match,'missing stream '+name
        blocks[name]=[line.strip() for line in match.group(1).splitlines() if any(key in line for key in ['Muted:','Muted Internally:','Min:','Max:','Current:','Devices:'])]
    (OUT/('system-volume-'+sys.argv[2]+'.json')).write_text(json.dumps(blocks,indent=2))
    print(json.dumps(blocks))
elif action=='native-steps':
    assert prefs('sleep_audio').get('status')=='paused','player must remain paused'
    node,_=slider();x1,y1,x2,y2=bounds(node)
    density=int(re.findall(r'\d+',shell('wm','density'))[-1])/160;pad=round(16*density)
    results=[]
    for step in range(11):
        x=round(x1+pad+(x2-x1-2*pad)*step/10)
        shell('input','tap',str(x),str((y1+y2)//2));time.sleep(.15)
        assert prefs('sleep_audio').get('volumeStep')==str(step)
        data=shell('dumpsys','media.player');start=data.index('packageName: '+PKG)
        block=data[start:].split('\n\n',1)[0]
        pairs=[tuple(map(float,m)) for m in re.findall(r'left - right volume\(([\d.]+), ([\d.]+)\)',block)]
        assert len(pairs)==2,(step,block)
        assert all(abs(left-step/10)<.00001 and abs(right-step/10)<.00001 for left,right in pairs),(step,pairs)
        assert 'paused(1)' in block,(step,'player unexpectedly resumed')
        results.append({'step':step,'audioOutput':pairs[0],'audioTrack':pairs[1],'paused':True})
        (OUT/f'paused-native-{step}.txt').write_text(block)
        print(json.dumps(results[-1]),flush=True)
    (OUT/'native-steps.json').write_text(json.dumps(results,indent=2))
    assert prefs('sleep_audio').get('status')=='paused'
elif action=='restart':
    node,_=slider();x1,y1,x2,y2=bounds(node)
    density=int(re.findall(r'\d+',shell('wm','density'))[-1])/160;pad=round(16*density);y=(y1+y2)//2
    def point(ratio):return round(x1+pad+(x2-x1-2*pad)*ratio)
    shell('input','swipe',str(point(1)),str(y),str(point(.37)),str(y),'700');time.sleep(.2)
    assert prefs('sleep_audio').get('volumeStep')=='4','drag did not snap to 40%'
    tree,_=ui();find(tree,'40%')
    shell('input','swipe',str(point(.4)),str(y),str(point(.33)),str(y),'700');time.sleep(.2)
    assert prefs('sleep_audio').get('volumeStep')=='3','drag did not snap to 30%'
    screenshot('volume-drag-30')
    shell('am','force-stop',PKG)
    shell('am','start','-n',PKG+'/.SleepActivity');time.sleep(.5)
    click('设置');tree,_=ui();tap_node(find(tree,'背景音乐音量 · 30%'));time.sleep(.2)
    node,_=slider();tree,_=ui();find(tree,'30%')
    assert prefs('sleep_audio').get('volumeStep')=='3'
    assert prefs('sleep_audio').get('wanted')=='false','restart resumed paused playback'
    screenshot('volume-restarted-30')
    (OUT/'restart.json').write_text(json.dumps({'drag37PercentSnappedTo':40,'drag33PercentSnappedTo':30,'afterForceStopRestartPercent':30,'wanted':False},indent=2))
    print('PASS drag snaps 37% -> 40%, 33% -> 30%; force-stop/reopen keeps 30%, does not resume paused playback')
elif action=='resume-switch':
    assert prefs('sleep_audio').get('volumeStep')=='0'
    back();click('播放声音');time.sleep(.5)
    for name in ['海洋','雨天']:
        tree,_=ui();tap_node(next(n for n in tree.iter('node') if n.get('text','').startswith(('雨天   ·','海洋   ·'))));time.sleep(.2)
        click('选择'+name);time.sleep(.5)
        audio=prefs('sleep_audio');assert audio.get('status')=='playing' and audio.get('volumeStep')=='0'
        data=shell('dumpsys','media.player');block=data[data.index('packageName: '+PKG):].split('\n\n',1)[0]
        pairs=re.findall(r'left - right volume\(([\d.]+), ([\d.]+)\)',block)
        assert len(pairs)==2 and all(float(left)==0 and float(right)==0 for left,right in pairs)
        (OUT/('switch-'+name+'.txt')).write_text(block)
    click('暂停声音');click('播放声音');time.sleep(.5)
    assert prefs('sleep_audio').get('status')=='playing'
    data=shell('dumpsys','media.player');block=data[data.index('packageName: '+PKG):].split('\n\n',1)[0]
    pairs=re.findall(r'left - right volume\(([\d.]+), ([\d.]+)\)',block)
    assert len(pairs)==2 and all(float(left)==0 and float(right)==0 for left,right in pairs)
    (OUT/'resumed-0.txt').write_text(block)
    click('暂停声音');click('设置');tree,_=ui();tap_node(next(n for n in tree.iter('node') if n.get('text','').startswith('背景音乐音量 · ')))
    print('PASS start, switch ocean/rain and resume all keep actual L/R gain 0; final playback paused')
elif action=='finish':
    assert prefs('sleep_audio').get('status')=='paused'
    set_step(10);back()
    shell('am','force-stop',PKG);shell('am','start','-n',PKG+'/.SleepActivity');time.sleep(.3)
    after={'alarm':prefs('sleep_alarm'),'audio':prefs('sleep_audio')}
    before=json.loads((OUT/'before.json').read_text())
    assert after['alarm']==before['alarm'],'alarm preferences changed'
    assert after['audio'].get('volumeStep')=='10' and after['audio'].get('wanted')=='false'
    assert after['audio'].get('file')==before['audio'].get('file')
    active=shell('dumpsys','activity','services',PKG)
    assert 'ServiceRecord{' not in active,'service still running'
    (OUT/'after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2))
    print('PASS restored background coefficient 100%, original rain scene, no active service, no pending alarm; alarm preferences unchanged')
