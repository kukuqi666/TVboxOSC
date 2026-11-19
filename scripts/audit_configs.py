#!/usr/bin/env python3
"""检查统一发布目录，生成文件类型、引用关系和 JAR 消费者索引。"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import quote, unquote
import zipfile

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'website/tvbox-config'
CATALOG = ROOT / 'catalog.json'
RAW = 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/'
CONFIG_RAW = RAW + 'website/tvbox-config/'
TEXT = {'.json', '.js', '.py', '.txt', '.m3u', '.m3u8', '.php'}
URL_RE = re.compile(r'https?://[^\s"\'<>\x60\\]+')
REL_RE = re.compile(r'["\'](\.\.?/[^"\'\r\n]+)["\']')


def split_ref(ref):
    match = re.search(r'[;?$#]', ref)
    return (ref[:match.start()], ref[match.start():]) if match else (ref, '')


def file_kind(path, text):
    suffix = path.suffix.lower()
    if suffix == '.jar':
        return 'jar'
    if suffix == '.js':
        return 'javascript'
    if suffix == '.py':
        return 'python'
    if suffix in {'.m3u', '.m3u8'}:
        return 'live'
    if suffix == '.json':
        if re.search(r'"(?:sites|urls|lives)"\s*:', text):
            return 'config'
        return 'rule'
    if suffix == '.txt':
        if '#EXTM3U' in text or '#genre#' in text:
            return 'live'
        if re.search(r'"(?:sites|urls|lives)"\s*:', text):
            return 'config'
    if suffix in {'.md', '.md5'}:
        return 'documentation' if suffix == '.md' else 'checksum'
    return 'resource'


def audit(write=False, accept_missing=False):
    previous = json.loads(CATALOG.read_text(encoding='utf-8')) if CATALOG.exists() else {}
    aliases = {key: value for key, value in previous.get('aliases', {}).items()
               if (ROOT / value).is_file()}
    records = {}
    duplicates = defaultdict(list)
    missing = []
    checksum_errors = []
    external_jars = set()
    for path in sorted(ROOT.rglob('*')):
        if not path.is_file() or path == CATALOG:
            continue
        if path.is_symlink():
            raise ValueError('发布目录不能使用 GitHub raw 无法展开的软链接：' + str(path))
        rel = path.relative_to(ROOT).as_posix()
        content = path.read_bytes()
        try:
            text = content.decode('utf-8-sig') if path.suffix.lower() in TEXT else ''
        except UnicodeDecodeError:
            text = ''
        record = {'path': rel, 'kind': file_kind(path, text), 'size': len(content),
                  'sha256': hashlib.sha256(content).hexdigest(),
                  'md5': hashlib.md5(content).hexdigest(), 'references': [], 'used_by': []}
        if path.suffix != '.md' and path.name.lower() not in {'license', 'readme.txt'}:
            duplicates[record['sha256']].append(rel)
        if path.suffix == '.jar':
            if not zipfile.is_zipfile(path):
                raise ValueError('JAR 不是 ZIP 容器：' + rel)
            with zipfile.ZipFile(path) as jar:
                if jar.testzip() is not None:
                    raise ValueError('JAR 内容损坏：' + rel)
        references = set()
        if text:
            for ref in set(URL_RE.findall(text)):
                body, suffix = split_ref(ref)
                if body.startswith(RAW):
                    target = REPO / unquote(body[len(RAW):])
                    if target.is_file() and target.is_relative_to(ROOT):
                        target_rel = target.relative_to(ROOT).as_posix()
                        references.add(target_rel)
                        match = re.search(r';md5;([a-fA-F0-9]{32})', suffix)
                        if match and hashlib.md5(target.read_bytes()).hexdigest() != match.group(1).lower():
                            checksum_errors.append({'file': rel, 'reference': ref})
                    else:
                        missing.append({'file': rel, 'reference': ref, 'kind': 'repository'})
                elif '.jar' in body.lower():
                    external_jars.add(ref)
            if path.suffix in {'.json', '.txt', '.m3u', '.m3u8'}:
                for ref in set(REL_RE.findall(text)):
                    body, _ = split_ref(ref)
                    if Path(body).suffix.lower() not in {'.jar', '.json', '.js', '.py', '.txt', '.m3u', '.m3u8', '.png', '.gz'}:
                        continue
                    target = (path.parent / unquote(body)).resolve()
                    if target.is_file() and target.is_relative_to(ROOT):
                        references.add(target.relative_to(ROOT).as_posix())
                    else:
                        missing.append({'file': rel, 'reference': ref, 'kind': 'upstream'})
        record['references'] = sorted(references)
        records[rel] = record
    for rel, record in records.items():
        for dependency in record['references']:
            if dependency in records:
                records[dependency]['used_by'].append(rel)
    missing.sort(key=lambda x: (x['file'], x['reference']))
    old_missing = {(x['file'], x['reference']) for x in previous.get('upstream_missing', [])}
    new_missing = [x for x in missing if (x['file'], x['reference']) not in old_missing]
    duplicate_groups = [paths for paths in duplicates.values() if len(paths) > 1]
    summary = {'files': len(records), 'jars': sum(x['kind'] == 'jar' for x in records.values()),
               'local_edges': sum(len(x['references']) for x in records.values()),
               'duplicate_groups': len(duplicate_groups),
               'repository_missing': sum(x['kind'] == 'repository' for x in missing),
               'upstream_missing': sum(x['kind'] == 'upstream' for x in missing),
               'checksum_errors': len(checksum_errors)}
    result = {**{key: previous[key] for key in ('source_repositories', 'deduplicated', 'symlinks_removed', 'removed_jar', 'saved_bytes') if key in previous},
              'summary': summary, 'aliases': aliases, 'files': list(records.values()),
              'external_jars': sorted(external_jars), 'upstream_missing': missing}
    failures = bool(duplicate_groups or checksum_errors or summary['repository_missing'] or (new_missing and not accept_missing))
    print(json.dumps(summary, ensure_ascii=False))
    for group in duplicate_groups:
        print('重复文件：' + '、'.join(group))
    for item in checksum_errors:
        print('MD5 不匹配：' + json.dumps(item, ensure_ascii=False))
    for item in new_missing:
        print('上游缺失：' + json.dumps(item, ensure_ascii=False))
    if write and not failures:
        CATALOG.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('已更新 catalog.json，包含文件正向依赖和反向消费者。')
    elif not write and CATALOG.exists() and previous != result:
        print('索引已过期，请执行 python3 scripts/audit_configs.py --write')
        failures = True
    return 1 if failures else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='校验通过后更新公开索引')
    parser.add_argument('--accept-upstream-missing', action='store_true', help='明确登记导入来源自身已缺失的相对文件')
    args = parser.parse_args()
    raise SystemExit(audit(args.write, args.accept_upstream_missing))
