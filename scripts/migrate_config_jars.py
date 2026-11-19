#!/usr/bin/env python3
"""语义迁移旧 QuickJS 配置 JAR；校验 SDK 后重建 DEX，保留资源与站点类。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

from prepare_optional_runtimes import ROOT, fetch
from audit_configs import dex_classes, dex_strings


def migrate(path):
    original = path.read_bytes()
    old_md5 = hashlib.md5(original).hexdigest()
    tools = [fetch('smali-dexlib2-3.0.9.jar'), fetch('guava-33.0.0-jre.jar')]
    wrapper = ROOT / 'runtime/quickjs/libs/wrapper-java.jar'
    classpath = os.pathsep.join(str(item) for item in [*tools, wrapper])
    cache = ROOT / 'build/config-jar-migration'
    cache.mkdir(parents=True, exist_ok=True)
    subprocess.run(['javac', '-cp', classpath, '-d', str(cache), str(ROOT / 'scripts/java/ConfigJarMigrator.java')], check=True)
    classpath = os.pathsep.join([str(cache), classpath])
    with tempfile.TemporaryDirectory(dir=cache) as temporary:
        directory = Path(temporary)
        output = directory / path.name
        changed = False
        removed_signatures = []
        with zipfile.ZipFile(path) as source, zipfile.ZipFile(output, 'w') as target:
            signed = any(re.match(r'META-INF/.*\.(RSA|DSA|EC|SF)$', name, re.I) for name in source.namelist())
            target.comment = source.comment
            for entry in source.infolist():
                # 派生包不能冒用原始签名；签名块与仅含旧摘要的清单一起移除，保留其他资源。
                if signed and (re.match(r'META-INF/.*\.(RSA|DSA|EC|SF)$', entry.filename, re.I) or entry.filename.upper() == 'META-INF/MANIFEST.MF'):
                    removed_signatures.append(entry.filename)
                    continue
                data = source.read(entry)
                if re.fullmatch(r'classes[0-9]*\.dex', entry.filename):
                    strings = dex_strings(data)
                    if any('com/fongmi/quickjs/' in item or 'com.fongmi.quickjs.' in item for item in strings):
                        before = directory / 'before.dex'
                        after = directory / 'after.dex'
                        before.write_bytes(data)
                        subprocess.run(['java', '-cp', classpath, 'ConfigJarMigrator', str(before), str(after)], check=True)
                        migrated = after.read_bytes()
                        if dex_classes(data) != dex_classes(migrated): raise ValueError('站点类集合发生变化')
                        if any('com/fongmi/quickjs/' in item or 'com.fongmi.quickjs.' in item for item in dex_strings(migrated)): raise ValueError('迁移后仍有旧包名')
                        data = migrated
                        changed = True
                target.writestr(entry, data)
        if not changed: return None
        updated = output.read_bytes()
        # 全部验证成功后才替换文件；失败不污染配置包。
        output.replace(path)
    new_md5 = hashlib.md5(updated).hexdigest()
    for config in (ROOT / 'website/tvbox-config').rglob('*'):
        if config.is_file() and config.suffix in {'.json', '.txt', '.md5'} and config.name != 'catalog.json':
            try: text = config.read_text(encoding='utf-8-sig')
            except UnicodeError: continue
            if old_md5 in text: config.write_text(text.replace(old_md5, new_md5), encoding='utf-8')
    return {'file': path.relative_to(ROOT / 'website/tvbox-config').as_posix(),
            'input_sha256': hashlib.sha256(original).hexdigest(),
            'output_sha256': hashlib.sha256(updated).hexdigest(),
            'removed_signatures': removed_signatures,
            'wrapper_sha256': hashlib.sha256(wrapper.read_bytes()).hexdigest(),
            'method': 'smali-dexlib2 3.0.9 类型重写与 DEX 重建；逐方法验证 kukuqi wrapper，保留全部站点类和资源'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jars', nargs='+', type=Path)
    args = parser.parse_args()
    catalog = ROOT / 'website/tvbox-config/catalog.json'
    document = json.loads(catalog.read_text(encoding='utf-8'))
    records = {item['file']: item for item in document.get('jar_migrations', [])}
    for path in args.jars:
        path = path.resolve()
        if not path.is_relative_to(ROOT / 'website/tvbox-config') or path.suffix != '.jar': raise ValueError('仅迁移本仓配置 JAR')
        record = migrate(path)
        if record:
            records[record['file']] = record
            document['jar_migrations'] = sorted(records.values(), key=lambda item: item['file'])
            catalog.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__': main()
