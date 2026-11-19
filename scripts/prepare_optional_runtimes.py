#!/usr/bin/env python3
"""Build ARM64 optional Node/BD-J payloads. Requires Android NDK r29, ant and JDK 8 (BD-J).
All downloaded archives are pinned; original notices remain inside each module APK.
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/optional-runtime-inputs'
INPUTS = {
    'node-LICENSE': ('https://raw.githubusercontent.com/FongMi/nodejs-mobile/e2e7cf82b9179d19304e19ccb6c3857155e07368/LICENSE', '5888dbb9a1d2b18f2c3e6c5f6af1b39de658372b402a0577b002777f14c62ace'),
    'node.zip': ('https://github.com/FongMi/nodejs-mobile/releases/download/v24.20.0-android.2/nodejs-mobile-android-24.20.0-android.2.zip', '630a4eea9ca984dc5457c5ef7aa0929b3846f77884d6badacda30be275825ef2'),
    'jre.zip': ('https://github.com/FongMi/bdj-runtime/releases/download/zero-jdk8u482-r1/j2re-zero-arm64-v8a.zip', 'f147cc55cba3d2062697638655fa241e113bc691607e83983b2d00a6dcea8826'),
    # 上游 JRE Release 被移除时，从本仓已发布且固定 SHA-256 的模块恢复相同字节。
    'bdj-recovery.apk': ('https://github.com/kukuqi666/TVboxOSC/releases/download/v3.1.0/runtime-bdj-1.0.1-arm64-v8a.apk', '74d6084a6a806ad127081c724bbcff3db5171afa34b65715b053285dcfc56992'),
    'libbluray.tar.xz': ('https://download.videolan.org/pub/videolan/libbluray/1.4.1/libbluray-1.4.1.tar.xz', '76b5dc40097f28dca4ebb009c98ed51321b2927453f75cc72cf74acd09b9f449'),
}

def download(url, part):
    """有界重试网络故障；永久缺失由调用方明确处理。"""
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'TVboxOSC-runtime-build'})
            with urllib.request.urlopen(request, timeout=90) as source, part.open('wb') as output:
                shutil.copyfileobj(source, output)
            return
        except urllib.error.HTTPError as error:
            part.unlink(missing_ok=True)
            if error.code not in {408, 429, 500, 502, 503, 504} or attempt == 2:
                raise
        except (OSError, urllib.error.URLError):
            part.unlink(missing_ok=True)
            if attempt == 2:
                raise
        time.sleep(2 ** attempt)


def fetch(name):
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / name
    url, expected = INPUTS[name]
    if not target.exists():
        part = target.with_suffix('.part')
        try:
            try:
                download(url, part)
            except urllib.error.HTTPError as error:
                if name != 'jre.zip' or error.code not in {404, 410}:
                    raise
                print('上游 JRE 不再提供下载，从已发布模块恢复并校验固定内容。', flush=True)
                recovery = fetch('bdj-recovery.apk')
                with zipfile.ZipFile(recovery) as module:
                    part.write_bytes(module.read('assets/bdj/jre.zip'))
            if hashlib.sha256(part.read_bytes()).hexdigest() != expected:
                raise ValueError(f'Checksum mismatch: {name}')
            part.replace(target)
        finally:
            part.unlink(missing_ok=True)
    if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        raise ValueError(f'Corrupt cached input: {target}')
    return target

def main(name):
    if name not in ('nodejs', 'bdj'): raise ValueError(name)
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '/opt/android-sdk')))
    ndk = Path(os.environ.get('ANDROID_NDK_HOME', str(sdk / 'ndk/29.0.14206865')))
    toolchain = ndk / 'toolchains/llvm/prebuilt' / ('windows-x86_64' if os.name == 'nt' else 'linux-x86_64')
    compiler = toolchain / 'bin' / ('aarch64-linux-android24-clang++.cmd' if os.name == 'nt' else 'aarch64-linux-android24-clang++')
    output = ROOT / f'runtime/{name}/build/payload'
    native = output / 'jniLibs/arm64-v8a'; native.mkdir(parents=True, exist_ok=True)
    assets = output / f'assets/{name}'; assets.mkdir(parents=True, exist_ok=True)
    flags = ['-shared', '-fPIC', '-std=c++17', '-Wl,-z,max-page-size=16384', '-Wl,-soname,libtvbox_' + ('node' if name == 'nodejs' else 'bdj') + '.so']
    if name == 'nodejs':
        archive = fetch('node.zip')
        shutil.copyfile(fetch('node-LICENSE'), assets / 'Node-LICENSE')
        with zipfile.ZipFile(archive) as zipped:
            (assets / 'runtime-manifest.json').write_bytes(zipped.read('runtime-manifest.json'))
            for library in ('libnode.so', 'libc++_shared.so'):
                (native / library).write_bytes(zipped.read('bin/arm64-v8a/' + library))
            # Preserve bundled upstream source notices/provenance.
            for entry in zipped.namelist():
                if entry.startswith(('licenses/', 'notices/')) or Path(entry).name in ('LICENSE', 'LICENSE.md', 'NOTICE', 'BUILD_PROVENANCE.json'):
                    if not entry.endswith('/'):
                        target = assets / 'notices' / entry; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(zipped.read(entry))
        flags += ['-L' + str(native), '-lnode', '-lc++_shared']
    else:
        shutil.copyfile(fetch('jre.zip'), assets / 'jre.zip')
        source = CACHE / 'libbluray-1.4.1'
        if not source.exists():
            with tarfile.open(fetch('libbluray.tar.xz')) as tar: tar.extractall(CACHE, filter='data')
        java8 = Path(os.environ.get('JAVA8_HOME', '/usr/lib/jvm/java-8-openjdk-amd64'))
        jar_output = CACHE / 'bdj-jars'; jar_output.mkdir(exist_ok=True)
        subprocess.run(['ant', '-f', str(source / 'src/libbluray/bdj/build.xml'),
                        '-Dbuild=' + str(jar_output / 'classes'), '-Ddist=' + str(jar_output),
                        '-Dsrc_awt=:java-j2se:java-build-support', '-Djava_version_asm=1.5', '-Djava_version_bdj=1.4',
                        '-Djavac_path=' + str(java8 / 'bin/javac'), '-Djavac_arg=-Xlint:-deprecation', '-Dversion=j2se-1.4.1'],
                       env={**os.environ, 'JAVA_HOME': str(java8)}, check=True, stdout=subprocess.DEVNULL)
        for jar in jar_output.glob('*.jar'): shutil.copyfile(jar, assets / jar.name)
        shutil.copyfile(source / 'COPYING', assets / 'LIBBLURAY-COPYING')
        flags += ['-ldl', '-static-libstdc++']
    library = 'libtvbox_' + ('node' if name == 'nodejs' else 'bdj') + '.so'
    subprocess.run([str(compiler), str(ROOT / f'runtime/{name}/src/main/cpp/bridge.cpp'), *flags, '-o', str(native / library)], check=True)
    (assets / 'provenance.txt').write_text('\n'.join(f'{key} {value[1]} {value[0]}' for key, value in INPUTS.items()) + '\n', encoding='utf-8')
    print(f'{name}: {output}')

if __name__ == '__main__':
    main(sys.argv[1])
