#!/usr/bin/env python3
"""Build with Android SDK build-tools 36, platform 35, JDK 17, libxposed API 102.
No network downloads and no system APK classes are included in the output.
"""
import hashlib, json, os, pathlib, re, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET
P = pathlib.Path(__file__).resolve().parent
def required_env(primary, fallback):
    value = os.environ.get(primary) or os.environ.get(fallback)
    if not value: raise SystemExit(f'Set {primary} or {fallback} before building.')
    return pathlib.Path(value)
SDK = required_env('FUSION_ANDROID_SDK', 'ANDROID_HOME')
JDK = required_env('FUSION_JAVA_HOME', 'JAVA_HOME')
TOOLS = SDK / 'build-tools' / '36.0.0'
ANDROID = SDK / 'platforms' / 'android-35' / 'android.jar'
API = pathlib.Path(os.environ.get('FUSION_XPOSED_JAR', str(P / 'libs/libxposed-api-102.jar')))
BUILD = P / 'build'
OUT = pathlib.Path(os.environ.get('FUSION_OUTPUT', str(P / 'dist')))
KEY = pathlib.Path(os.environ.get('FUSION_KEYSTORE', str(P / '.local/fusion-dev.jks')))
PASSWORD = os.environ.get('FUSION_KEYSTORE_PASSWORD', 'fusion-local-dev')
VERSION = ET.parse(P/'AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
if not VERSION or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', VERSION):
    raise SystemExit('AndroidManifest.xml must declare a semantic versionName.')
if not API.is_file(): raise SystemExit('Missing libxposed API 102 JAR. Run python3 tools/fetch_xposed_api.py or set FUSION_XPOSED_JAR.')
env = os.environ.copy(); env['JAVA_HOME'] = str(JDK); env['PATH'] = str(JDK/'bin') + os.pathsep + env.get('PATH','')
env['FUSION_SIGN_PASS'] = PASSWORD
def run(*args):
    command = [str(a) for a in args]
    if sys.platform == 'win32' and not pathlib.Path(command[0]).is_file():
        for suffix in ('.exe', '.bat'):
            if pathlib.Path(command[0] + suffix).is_file():
                command[0] += suffix
                break
    subprocess.run(command, cwd=P, env=env, check=True)
if BUILD.exists(): shutil.rmtree(BUILD)
for d in [BUILD/'generated/dev/fusion/widget', BUILD/'classes', BUILD/'test-classes', BUILD/'dex', OUT, KEY.parent]: d.mkdir(parents=True, exist_ok=True)
if not KEY.exists():
    run(JDK/'bin/keytool', '-genkeypair', '-keystore', KEY, '-storepass:env', 'FUSION_SIGN_PASS', '-keypass:env', 'FUSION_SIGN_PASS', '-alias', 'fusion', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650', '-dname', 'CN=Fusion Widget Local Development', '-noprompt')
run(JDK/'bin/keytool', '-exportcert', '-keystore', KEY, '-storepass:env', 'FUSION_SIGN_PASS', '-alias', 'fusion', '-file', BUILD/'certificate.der')
digest = hashlib.sha256((BUILD/'certificate.der').read_bytes()).hexdigest()
(BUILD/'generated/dev/fusion/widget/BuildIdentity.java').write_text('package dev.fusion.widget; public final class BuildIdentity { public static final String CERT_SHA256 = "'+digest+'"; public static final String VERSION_NAME = '+json.dumps(VERSION)+'; }\n')
run(TOOLS/'aapt2', 'compile', '--dir', P/'res', '-o', BUILD/'resources.zip')
assets = ['-A', P/'assets'] if (P/'assets').is_dir() else []
run(TOOLS/'aapt2', 'link', '-I', ANDROID, '--manifest', P/'AndroidManifest.xml', '--java', BUILD/'generated', *assets, '--auto-add-overlay', '-o', BUILD/'unsigned.apk', BUILD/'resources.zip')
sources = sorted((P/'src').rglob('*.java')) + sorted((BUILD/'generated').rglob('*.java'))
run(JDK/'bin/javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-classpath', str(ANDROID)+os.pathsep+str(API), '-d', BUILD/'classes', *sources)
# Test real caller-certificate pinning and state interpretation on the host JVM.
run(JDK/'bin/javac', '-encoding', 'UTF-8', '-classpath', BUILD/'classes', '-d', BUILD/'test-classes', P/'tests/PolicyTest.java', P/'tests/PresentationTest.java')
run(JDK/'bin/java', '-cp', str(BUILD/'classes')+os.pathsep+str(BUILD/'test-classes'), 'dev.fusion.widget.PolicyTest', BUILD/'certificate.der')
run(JDK/'bin/java', '-cp', str(BUILD/'classes')+os.pathsep+str(BUILD/'test-classes'), 'dev.fusion.widget.PresentationTest')
run(JDK/'bin/jar', 'cf', BUILD/'app-classes.jar', '-C', BUILD/'classes', '.')
run(TOOLS/'d8', '--min-api', '31', '--lib', ANDROID, '--classpath', API, '--output', BUILD/'dex', BUILD/'app-classes.jar')
with zipfile.ZipFile(BUILD/'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    for dex in (BUILD/'dex').glob('*.dex'): apk.write(dex, dex.name)
    for resource in (P/'resources').rglob('*'):
        if resource.is_file(): apk.write(resource, resource.relative_to(P/'resources').as_posix())
run(TOOLS/'zipalign', '-f', '4', BUILD/'unsigned.apk', BUILD/'aligned.apk')
result = OUT/f'HyperFusionWidget-{VERSION}.apk'
# Keep the generated development password out of command arguments.
env['FUSION_SIGN_PASS'] = PASSWORD
run(TOOLS/'apksigner', 'sign', '--ks', KEY, '--ks-key-alias', 'fusion', '--ks-pass', 'env:FUSION_SIGN_PASS', '--key-pass', 'env:FUSION_SIGN_PASS', '--v4-signing-enabled', 'false', '--out', result, BUILD/'aligned.apk')
run(TOOLS/'apksigner', 'verify', '--verbose', '--print-certs', result)
(result.with_suffix('.apk.sha256')).write_text(hashlib.sha256(result.read_bytes()).hexdigest()+'  '+result.name+'\n')
print('Built:', result)
