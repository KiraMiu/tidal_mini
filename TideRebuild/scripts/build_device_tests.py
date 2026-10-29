#!/usr/bin/env python3
"""Build a separate, locally signed instrumentation APK; no test entry in main APK."""
import pathlib,subprocess,os,zipfile,shutil,datetime
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path.home()/'Library/Android/sdk';bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-34/android.jar'
app=pathlib.Path((root/'build/latest-apk.txt').read_text().strip())
out=root/'build/device-tests'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');out.mkdir(parents=True)
for d in ['classes','dex']: (out/d).mkdir()
(out/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.moreless.tide.rebuild.tests"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34"/><instrumentation android:name="io.moreless.tide.rebuild.AlarmChecks" android:targetPackage="io.moreless.tide.rebuild"/><application android:label="潮汐内部验证"/></manifest>')
def run(*args):subprocess.run([str(a) for a in args],check=True)
run(bt/'aapt2','link','-I',android,'--manifest',out/'AndroidManifest.xml','-o',out/'test.apk')
run('javac','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(bt/'core-lambda-stubs.jar')+os.pathsep+str(android),'-classpath',app.parent/'classes','-d',out/'classes',*sorted((root/'tests/android').glob('*.java')))
run(bt/'d8','--lib',android,'--classpath',app,'--min-api','24','--output',out/'dex',*sorted((out/'classes').rglob('*.class')))
with zipfile.ZipFile(out/'test.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run(bt/'zipalign','-p','4',out/'test.apk',out/'aligned.apk')
run(bt/'apksigner','sign','--ks',root/'build/local-debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'tests.apk',out/'aligned.apk')
(root/'build/latest-test-apk.txt').write_text(str(out/'tests.apk')+'\n');print(out/'tests.apk')
