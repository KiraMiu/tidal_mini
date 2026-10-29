from pathlib import Path
import subprocess,xml.etree.ElementTree as E,re,sys,time
ROOT=Path(__file__).parent
ADB='/Users/cxmacstudio01/Library/Android/sdk/platform-tools/adb'
def adb(*a):return subprocess.check_output([ADB,*a])
def tree():
 adb('shell','uiautomator','dump','/data/local/tmp/tide-v03.xml')
 return adb('exec-out','cat','/data/local/tmp/tide-v03.xml')
if sys.argv[1]=='capture':
 raw=tree();(ROOT/(sys.argv[2]+'.xml')).write_bytes(raw)
 (ROOT/(sys.argv[2]+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 for n in E.fromstring(raw).iter('node'):
  if n.get('text'):print(n.get('text'))
if sys.argv[1] in ['tap','hold']:
 for n in E.fromstring(tree()).iter('node'):
  if sys.argv[2] in [n.get('text'),n.get('content-desc')]:
   a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));x=str((a+c)//2);y=str((b+d)//2)
   if sys.argv[1]=='tap':adb('shell','input','tap',x,y)
   else:adb('shell','input','swipe',x,y,x,y,'1800')
   print(sys.argv[1],sys.argv[2]);break
 else:raise RuntimeError('missing '+sys.argv[2])
