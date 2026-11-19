#!/usr/bin/env python3
"""校验并打包参考 Python 扩展和原生库；当前发布 ABI 为 arm64-v8a。"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import zipfile
from prepare_optional_runtimes import download

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/python-runtime-inputs'
LOCK = ROOT / 'engine/chaquo/runtime.lock.json'
ASSETS = 'assets/chaquopy/'
CXX = 'chaquopy/lib/libc++_shared.so'


def digest(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def fetch(name, expected, base):
    if Path(name).name != name: raise ValueError('运行时输入文件名越界')
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / name
    if not path.is_file() or digest(path.read_bytes()) != expected:
        part = path.with_suffix('.part')
        try:
            download(base + name, part)
            if digest(part.read_bytes()) != expected: raise ValueError('Python 输入校验失败：' + name)
            part.replace(path)
        finally: part.unlink(missing_ok=True)
    return path


def sync():
    lock = json.loads(LOCK.read_text())
    base = 'https://github.com/' + lock['repository'] + '/releases/download/' + lock['tag'] + '/'
    manifests = {}
    for kind, entry in lock['manifests'].items():
        doc = json.loads(fetch(entry['file'], entry['sha256'], base).read_text())
        if kind != 'pure-wheels':
            if any(doc['producer'][key] != lock[key] for key in ['repository', 'tag', 'commit']): raise ValueError('Python 原生产物来源不匹配')
            if doc['abis'][lock['abi']]['abi'] != lock['abi']: raise ValueError('Python ABI 不匹配')
        manifests[kind] = doc
    wheels = manifests['wheels']['abis'][lock['abi']]['wheels'] + manifests['pure-wheels']['wheels']
    expected = {item['file']: item['sha256'] for item in lock['wheels']}
    if {item['file']: item['sha256'] for item in wheels} != expected: raise ValueError('Python wheels 与固定输入不符')
    native = manifests['native-bundles']['abis'][lock['abi']]
    for entry in [*wheels, native]: fetch(entry['file'], entry['sha256'], base)
    print('已校验 Python 原生包与 ' + str(len(wheels)) + ' 个扩展 wheel', flush=True)
    return lock, native


def verify_elf(data, name):
    if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183: raise ValueError('原生库 ABI 错误：' + name)
    headers = struct.unpack_from('<Q', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 54)
    for index in range(count):
        position = headers + index * stride
        kind = struct.unpack_from('<I', data, position)[0]
        offset, address = struct.unpack_from('<QQ', data, position + 8)
        size, align = struct.unpack_from('<QQ', data, position + 40)
        if kind == 1 and (align < 16384 or (address - offset) % 16384): raise ValueError('原生库不支持 16 KB 页面：' + name)
        # Android 的 16 KB 兼容要求检查 PT_LOAD；NDK 标准 C++ 库的 RELRO 末端可能不满页。
        if kind == 0x6474e552 and not name.endswith('libc++_shared.so') and (address + size) % 16384: raise ValueError('原生库 RELRO 未对齐：' + name)


def rewrite_zip(data, replace, omit_tests=False):
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            if omit_tests and (entry.filename == 'yapftests' or entry.filename.startswith('yapftests/')): continue
            target.writestr(entry, replace(entry.filename, source.read(entry)))
    return output.getvalue()


def prepare(source_path, output_path):
    if source_path.resolve() == output_path.resolve(): raise ValueError('禁止覆盖输入 AAR')
    lock, native = sync()
    abi = lock['abi']
    if native['inputs']['chaquopyVersion'] != '17.2.0': raise ValueError('Python 桥接版本不匹配')
    libraries = {}
    with zipfile.ZipFile(CACHE / native['file']) as source:
        if set(source.namelist()) != {entry['path'] for entry in native['libraries']}: raise ValueError('Python 原生包内容不匹配')
        for entry in native['libraries']:
            data = source.read(entry['path'])
            if digest(data) != entry['sha256']: raise ValueError('Python 原生库摘要错误')
            verify_elf(data, entry['path'])
            libraries[entry['path']] = data
    with zipfile.ZipFile(ROOT / 'engine/core/libs/lib-mpvplayer-release.aar') as source:
        cxx = source.read('jni/' + abi + '/libc++_shared.so')
        verify_elf(cxx, 'libc++_shared.so')
    with zipfile.ZipFile(source_path) as source:
        contents = {entry.filename: source.read(entry) for entry in source.infolist() if not entry.is_dir()}
    for name in list(contents):
        if name.startswith('jni/' + abi + '/'):
            contents[name] = libraries[name]
        elif name.startswith(ASSETS + 'bootstrap-native/' + abi + '/') and name.endswith('.so'):
            key = 'bridge/chaquopy.so' if name.endswith('/java/chaquopy.so') else 'dynload/' + Path(name).name
            contents[name] = libraries[key]
        elif name == ASSETS + 'stdlib-' + abi + '.imy':
            contents[name] = rewrite_zip(contents[name], lambda path, data: libraries['dynload/' + Path(path).name] if path.endswith('.so') else data)
        elif name in {ASSETS + 'requirements-common.imy', ASSETS + 'requirements-' + abi + '.imy'}:
            contents[name] = rewrite_zip(contents[name], lambda path, data: cxx if path == CXX else data, omit_tests=True)
    patchelf = shutil.which('patchelf')
    if not patchelf: raise ValueError('Python 原生包需要 patchelf，请先安装构建工具')
    with tempfile.TemporaryDirectory(dir=CACHE) as temporary:
        python = Path(temporary) / 'libpython3.10.so'
        python.write_bytes(contents['jni/' + abi + '/libpython3.10.so'])
        subprocess.run([patchelf, '--page-size', '16384', '--set-soname', 'libpython3.10.so', str(python)], check=True)
        contents['jni/' + abi + '/libpython3.10.so'] = python.read_bytes()
    for name, data in contents.items():
        if name.endswith('.so'): verify_elf(data, name)
        if name.endswith('.imy') and ('requirements-' in name or 'stdlib-' in name):
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for path in archive.namelist():
                    if path.endswith('.so'): verify_elf(archive.read(path), path)
    build = json.loads(contents[ASSETS + 'build.json'])
    build['assets'] = {name[len(ASSETS):]: digest(data, 'sha1') for name, data in contents.items() if name.startswith(ASSETS) and name != ASSETS + 'build.json'}
    contents[ASSETS + 'build.json'] = json.dumps(build).encode()
    contents['assets/python-notices/runtime.lock.json'] = LOCK.read_bytes()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    part = output_path.with_suffix('.part')
    try:
        with zipfile.ZipFile(part, 'w', zipfile.ZIP_DEFLATED) as target:
            for name, data in sorted(contents.items()):
                entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)); entry.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(entry, data)
        part.replace(output_path)
    finally: part.unlink(missing_ok=True)
    print('已生成扩展及原生库一致的 Python AAR：' + str(output_path), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync', action='store_true')
    parser.add_argument('--aar', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.sync: sync()
    elif args.aar and args.output: prepare(args.aar, args.output)
    else: parser.error('指定 --sync 或 --aar/--output')
