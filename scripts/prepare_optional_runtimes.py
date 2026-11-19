#!/usr/bin/env python3
"""Build ARM64 optional Node/BD-J payloads. Requires Android NDK r29, ant and JDK 8 (BD-J).
All downloaded archives are pinned; original notices remain inside each module APK.
"""
import hashlib
import json
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
    'quickjs-wrapper.tar.gz': ('https://codeload.github.com/FongMi/quickjs-wrapper/tar.gz/7fb3bb181d28f66d7057d171ff9f537b5a77f6c9', '4124f63c40b8e87c78b60cc6c9c5083001c8281c9f052a904ff724595f24b707'),
    'quickjs-engine.tar.gz': ('https://codeload.github.com/FongMi/quickjs/tar.gz/0b213e90cc56a950856abed2ee7dc85aaa160e7b', '1edce6b0c0a1e1ad24c3259844a67736d704c4022f73822815f26b582dfe4e8b'),
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

def prepare_quickjs():
    """固定源码生成 kukuqi Java/JNI，禁止对 ELF 符号做二进制替换。"""
    output = ROOT / 'runtime/quickjs/build/rebuild'
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    for archive, destination in [('quickjs-wrapper.tar.gz', output), ('quickjs-engine.tar.gz', output / 'engine')]:
        destination.mkdir(exist_ok=True)
        with tarfile.open(fetch(archive)) as tar:
            tar.extractall(destination, filter='data')
    source = next(output.glob('quickjs-wrapper-*'))
    engine = next((output / 'engine').glob('quickjs-*'))
    shutil.copytree(engine, source / 'vendor/quickjs', dirs_exist_ok=True)
    # Java、资源路径、JNI 导出、FindClass 与生成的头文件保持一致。
    for path in source.rglob('*'):
        if path.is_file() and path.suffix in {'.java', '.cpp', '.h', '.txt', '.pro', '.gradle', '.js'}:
            value = path.read_text()
            path.write_text(value.replace('com.fongmi.quickjs', 'com.kukuqi.quickjs')
                           .replace('com/fongmi/quickjs', 'com/kukuqi/quickjs')
                           .replace('com_fongmi_quickjs', 'com_kukuqi_quickjs'))
    for path in sorted(source.rglob('fongmi'), reverse=True):
        path.rename(path.with_name('kukuqi'))
    classes = output / 'classes'; classes.mkdir()
    headers = source / 'build/generated/jni'; headers.mkdir(parents=True)
    java = Path(os.environ.get('JAVA_HOME', '/opt/jdk-23')) / 'bin'
    subprocess.run([str(java / 'javac'), '--release', '17', '-encoding', 'UTF-8', '-h', str(headers),
                    '-cp', str(source / 'libs/chicory-runtime-android.jar'), '-d', str(classes),
                    *map(str, sorted((source / 'src/main/java').rglob('*.java')))], check=True)
    shutil.copytree(source / 'src/main/resources', classes, dirs_exist_ok=True)
    notices = classes / 'META-INF/notices/quickjs'; notices.mkdir(parents=True)
    for name, path in [('wrapper-LICENSE', source / 'LICENSE'), ('THIRD_PARTY.md', source / 'THIRD_PARTY.md'), ('engine-LICENSE', engine / 'LICENSE')]:
        shutil.copyfile(path, notices / name)
    (notices / 'kukuqi-build.json').write_text(json.dumps({
        'wrapper': INPUTS['quickjs-wrapper.tar.gz'], 'engine': INPUTS['quickjs-engine.tar.gz'],
        'package': 'com.kukuqi.quickjs', 'androidApi': 24, 'ndk': '29.0.14206865'
    }, ensure_ascii=False, indent=2) + '\n')
    with zipfile.ZipFile(source / 'libs/chicory-runtime-android.jar') as bundled:
        for item in bundled.infolist():
            if item.is_dir() or item.filename == 'META-INF/MANIFEST.MF': continue
            target = classes / item.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(bundled.read(item))
    def package(target, entries):
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
            for name, content in sorted(entries):
                info = zipfile.ZipInfo(name, (2026, 11, 19, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, content)
    libs = ROOT / 'runtime/quickjs/libs'
    package(libs / 'wrapper-java.jar', [(p.relative_to(classes).as_posix(), p.read_bytes()) for p in classes.rglob('*') if p.is_file()])
    sdk = Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk'))
    ndk = sdk / 'ndk/29.0.14206865'
    cmake = sdk / 'cmake/3.22.1/bin/cmake'
    entries = [('AndroidManifest.xml', b'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.kukuqi.quickjs.runtime"><uses-sdk android:minSdkVersion="24"/></manifest>')]
    import io
    empty = io.BytesIO()
    with zipfile.ZipFile(empty, 'w'): pass
    entries.append(('classes.jar', empty.getvalue()))
    for abi in ['arm64-v8a', 'armeabi-v7a']:
        directory = output / abi
        subprocess.run([str(cmake), '-S', str(source), '-B', str(directory), '-G', 'Ninja',
                        '-DCMAKE_MAKE_PROGRAM=' + str(cmake.with_name('ninja')),
                        '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
                        '-DANDROID_ABI=' + abi, '-DANDROID_PLATFORM=android-24', '-DANDROID_STL=c++_static',
                        '-DCMAKE_BUILD_TYPE=Release'], check=True)
        subprocess.run([str(cmake), '--build', str(directory), '--target', 'quickjs-wrapper', '--parallel', '4'], check=True)
        library = directory / 'libquickjs-wrapper.so'
        subprocess.run([str(ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'), '--strip-unneeded', str(library)], check=True)
        entries.append(('jni/' + abi + '/libquickjs-wrapper.so', library.read_bytes()))
    for path in notices.iterdir(): entries.append(('assets/quickjs-notices/' + path.name, path.read_bytes()))
    package(libs / 'runtime-release.aar', entries)
    print('已从固定源码重建 kukuqi QuickJS Java/JNI 与来源声明')


def main(name):
    if name == 'quickjs': return prepare_quickjs()
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
        flags += ['-L' + str(native), '-lnode', '-lc++_shared', '-llog']
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
