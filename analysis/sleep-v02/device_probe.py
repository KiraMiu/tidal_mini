from pathlib import Path
import subprocess,sys,xml.etree.ElementTree as E,re,time,json
ADB='/Users/cxmacstudio01/Library/Android/sdk/platform-tools/adb'
SERIAL='49VGOR99Z985YTIB';PACKAGE='io.moreless.tide.rebuild';ROOT=Path(__file__).parent

def adb(*args,binary=False):
 r=subprocess.run([ADB,'-s',SERIAL,*args],check=True,stdout=subprocess.PIPE)
 return r.stdout if binary else r.stdout.decode('utf-8')
def dump(name='probe'):
 adb('shell','uiautomator','dump','/data/local/tmp/tide-sleep-v02.xml')
 data=adb('exec-out','cat','/data/local/tmp/tide-sleep-v02.xml');(ROOT/(name+'.xml')).write_text(data)
 return E.fromstring(data)
def tap(label):
 tree=dump()
 for n in tree.iter('node'):
  if n.get('text')==label or n.get('content-desc')==label:
   x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));print('tap:',label,flush=True);return
 raise RuntimeError('UI label absent: '+label)
def prefs(name):
 raw=adb('shell','run-as',PACKAGE,'cat','/data/user_de/0/'+PACKAGE+'/shared_prefs/'+name+'.xml')
 root=E.fromstring(raw);return {e.get('name'):e.get('value',e.text) for e in root}
if sys.argv[1]=='tap':tap(sys.argv[2])
if sys.argv[1]=='dump':
 for n in dump(sys.argv[2]).iter('node'):
  if n.get('text'):print(n.get('text'),n.get('bounds'))
if sys.argv[1]=='state':
 for name in ['sleep_alarm','sleep_audio']:print(name,json.dumps(prefs(name),ensure_ascii=False))
if sys.argv[1]=='start-test':
 tap('闹钟与后台设置');tap('静音测试闹钟（30 秒）');tap('开始静音测试');time.sleep(1)
 state=prefs('sleep_alarm');assert state.get('phase')=='armed',state
 (ROOT/'scheduled-state.json').write_text(json.dumps(state,ensure_ascii=False,indent=2));print(json.dumps(state,ensure_ascii=False),flush=True)
if sys.argv[1]=='kill-test-process':
 # Only the debuggable test app's own uid is allowed to signal its own pid.
 pid=adb('shell','pidof',PACKAGE).strip();assert pid.isdecimal(),pid
 adb('shell','run-as',PACKAGE,'kill','-9',pid);print('terminated test pid',pid,flush=True)
if sys.argv[1]=='ring-smoke':
 tap('闹钟与后台设置');tap('静音测试闹钟（30 秒）');tap('开始静音测试');time.sleep(1)
 initial=prefs('sleep_alarm');assert initial.get('phase')=='armed' and initial.get('silentTest')=='true',initial
 pid=adb('shell','pidof',PACKAGE).strip();assert pid.isdecimal(),pid
 adb('shell','input','keyevent','223')
 adb('shell','run-as',PACKAGE,'kill','-9',pid);print('terminated only silent test process',pid,flush=True)
 end=time.monotonic()+40
 while time.monotonic()<end:
  state=prefs('sleep_alarm')
  if state.get('phase')=='ringing':break
  time.sleep(.5)
 assert state.get('phase')=='ringing',state
 time.sleep(.7)
 (ROOT/'ring-smoke.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
 (ROOT/'ring-smoke-services.txt').write_text(adb('shell','dumpsys','activity','services',PACKAGE))
 tap('再睡 5 分钟');time.sleep(.4)
 snoozed=prefs('sleep_alarm');assert snoozed['phase']=='snoozed',snoozed
 assert 295000<int(snoozed['trigger'])-int(time.time()*1000)<=300000,snoozed
 (ROOT/'ring-smoke-result.json').write_text(json.dumps({'scheduled':initial,'ringing':state,'snoozed':snoozed},ensure_ascii=False,indent=2))
 print('PASS process death + locked silent alarm -> full-screen UI -> snooze5min; cancelling test',flush=True)
 adb('shell','am','start','-n',PACKAGE+'/.SleepActivity');tap('结束睡眠');tap('结束睡眠');time.sleep(.3)
 assert prefs('sleep_alarm')['phase']=='off'
 print('PASS cancelled snoozed alarm',flush=True)
