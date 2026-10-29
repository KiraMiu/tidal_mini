#!/usr/bin/env python3
"""Build the Java Android app using installed SDK tools, without Maven/Gradle downloads.

Each build has its own directory. No source or previous artifact is deleted.
The generated key is a local debug key, never the original app's release key.
"""
import datetime
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
TOOLS = SDK / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '35.0.0')
ANDROID = SDK / 'platforms/android-34/android.jar'
BUILD = ROOT / 'build/offline' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
MAIN = ROOT / 'app/src/main'

def run(*args):
    print('+', str(args[0]), flush=True)
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

def main():
    for path in [ANDROID, TOOLS/'aapt2', TOOLS/'d8', TOOLS/'zipalign', TOOLS/'apksigner']:
        if not path.exists():
            raise SystemExit(f'Missing SDK component: {path}')
    for name in ['java', 'javac', 'keytool']:
        if not shutil.which(name):
            raise SystemExit(f'Missing Java tool: {name}; use JDK 17')
    BUILD.mkdir(parents=True)
    for name in ['classes','dex','generated']:
        (BUILD/name).mkdir()
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest = ET.parse(MAIN/'AndroidManifest.xml')
    manifest.getroot().set('package', 'io.moreless.tide.rebuild')
    manifest.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable','true')
    manifest.write(BUILD/'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    # Compile app resources as well as assets (launcher icon).
    run(TOOLS/'aapt2','compile','--dir',MAIN/'res','-o',BUILD/'compiled-res.zip')
    run(TOOLS/'aapt2','link','-I',ANDROID,'--manifest',BUILD/'AndroidManifest.xml',
        BUILD/'compiled-res.zip','-A',MAIN/'assets','-0','m4a','-0','mp3','--java',BUILD/'generated','-o',BUILD/'resources.apk')
    sources=sorted((MAIN/'java').rglob('*.java')) + sorted((BUILD/'generated').rglob('*.java'))
    run('javac','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(TOOLS/'core-lambda-stubs.jar')+os.pathsep+str(ANDROID),
        '-d',BUILD/'classes',*sources)
    run(TOOLS/'d8','--lib',ANDROID,'--min-api','24','--output',BUILD/'dex',*sorted((BUILD/'classes').rglob('*.class')))
    shutil.copyfile(BUILD/'resources.apk',BUILD/'unaligned.apk')
    with zipfile.ZipFile(BUILD/'unaligned.apk','a') as archive:
        for dex in (BUILD/'dex').glob('*.dex'):
            archive.write(dex,dex.name)
    run(TOOLS/'zipalign','-p','4',BUILD/'unaligned.apk',BUILD/'aligned.apk')
    key=ROOT/'build/local-debug.keystore'
    if not key.exists():
        run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
            '-alias','androiddebugkey','-dname','CN=Tide Rebuild Debug','-keyalg','RSA','-keysize','2048','-validity','3650')
    output=BUILD/'tide-rebuild-debug.apk'
    run(TOOLS/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android',
        '--ks-key-alias','androiddebugkey','--out',output,BUILD/'aligned.apk')
    run(TOOLS/'apksigner','verify','--verbose',output)
    run(TOOLS/'zipalign','-c','4',output)
    (ROOT/'build/latest-apk.txt').write_text(str(output)+'\n')
    print(f'APK: {output}')

if __name__ == '__main__':
    main()
