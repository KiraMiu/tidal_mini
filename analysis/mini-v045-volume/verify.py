from pathlib import Path
import subprocess,xml.etree.ElementTree as ET,re,time,json
ADB='/Users/cxmacstudio01/Library/Android/sdk/platform-tools/adb';PKG='io.moreless.tide.rebuild';OUT=Path(__file__).resolve().parent

def adb(*args):return subprocess.run([ADB,'-s','49VGOR99Z985YTIB',*args],check=True,capture_output=True).stdout
def shell(*args):return adb('shell',*args).decode()
def prefs(name):return {n.get('name'):n.get('value',n.text or '') for n in ET.fromstring(shell('run-as',PKG,'cat',f'/data/user_de/0/{PKG}/shared_prefs/{name}.xml'))}
def ui():
    shell('uiautomator','dump','/data/local/tmp/tidal-v045-ui.xml')
    return ET.fromstring(shell('cat','/data/local/tmp/tidal-v045-ui.xml'))
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def tap(n):
    a,b,c,d=bounds(n);shell('input','tap',str((a+c)//2),str((b+d)//2))
def click(label):
    tap(next(n for n in ui().iter('node') if label in [n.get('text'),n.get('content-desc')]));time.sleep(.2)
def open_volume(label):click('设置');click('背景音乐音量 · '+label)
def set_ratio(ratio):
    n=next(n for n in ui().iter('node') if n.get('class')=='android.widget.SeekBar');a,b,c,d=bounds(n)
    pad=round(16*int(re.findall(r'\d+',shell('wm','density'))[-1])/160)
    shell('input','tap',str(round(a+pad+(c-a-2*pad)*ratio)),str((b+d)//2));time.sleep(.2)

before=prefs('sleep_alarm');audio=prefs('sleep_audio')
assert before['phase']=='off' and audio['wanted']=='false'
gain=float(audio['volumeGain']);label=str(int(gain*100+.5))+'%'
print(adb('install','--no-streaming','-r',str(OUT.parents[1]/'交付/潮汐Mini-0.4.5-debug.apk')).decode(),flush=True)
shell('am','start','-n',PKG+'/.SleepActivity');time.sleep(.3);open_volume(label)
assert float(prefs('sleep_audio')['volumeGain'])==gain
assert any(n.get('text')==label for n in ui().iter('node'))
(OUT/'integer-percent.png').write_bytes(adb('exec-out','screencap','-p'))
shell('input','keyevent','4');open_volume(label)
assert any(n.get('text')==label for n in ui().iter('node'))
shell('input','keyevent','4')
assert prefs('sleep_alarm')==before and prefs('sleep_audio')==audio
result={'savedGainPreserved':gain,'dialogLabel':label,'settingsLabel':label,'alarmUnchanged':True,'audioPreferencesUnchanged':True,'playbackNotStarted':True}
(OUT/'summary.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
