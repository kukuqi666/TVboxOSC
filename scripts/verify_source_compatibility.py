#!/usr/bin/env python3
"""准备实际配置的 Android 验证输入，并汇总逐站接口与首帧结果。"""
from __future__ import annotations

import argparse
import ast
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import warnings
from urllib.parse import quote

import audit_configs as audit

REPO = audit.REPO
OUTPUT = REPO / 'build/source-validation'


def prepare(base):
    catalog = json.loads(audit.CATALOG.read_text(encoding='utf-8'))
    base = base.rstrip('/') + '/website/tvbox-config/'

    def mirror(value):
        if isinstance(value, dict): return {key: mirror(item) for key, item in value.items()}
        if isinstance(value, list): return [mirror(item) for item in value]
        if isinstance(value, str): return value.replace(audit.CONFIG_RAW, base)
        return value

    configs = {}
    python_refs = defaultdict(list)
    for record in catalog['files']:
        if record['kind'] != 'config': continue
        rel = record['path']
        config = audit.parse_config((audit.ROOT / rel).read_text(encoding='utf-8-sig'))
        configs[rel] = config
        for site in config.get('sites', []):
            if not isinstance(site, dict) or not isinstance(site.get('api'), str): continue
            local = audit.local_reference(rel, site['api'])
            if local and local.endswith('.py'): python_refs[local].append((rel, site))

    encrypted = []
    references = [item for item in catalog['config_problems'] if item.get('severity') == 'warning']
    references.extend(item for item in catalog.get('config_repairs', []) if item.get('scope') == 'source-compatibility-20261119' and item.get('action') == 'replace_source_jar')
    for warning in references:
        config = configs[warning['file']]
        site = next(item for item in config['sites'] if item.get('key') == warning['key'])
        encrypted.append({
            'id': warning['file'] + ':' + warning['key'], 'file': warning['file'],
            'key': warning['key'], 'api': site['api'], 'ext': mirror(site.get('ext', '')),
            'jar': base + quote(warning['jar']) + ';md5;' + hashlib.md5((audit.ROOT / warning['jar']).read_bytes()).hexdigest(),
            'display_jar': warning['jar'],
        })
    python = []
    helpers = []
    for path in sorted(audit.ROOT.rglob('*.py')):
        rel = path.relative_to(audit.ROOT).as_posix()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', SyntaxWarning)
            tree = ast.parse(path.read_text(encoding='utf-8-sig'))
        if not any(isinstance(node, ast.ClassDef) and node.name == 'Spider' for node in ast.walk(tree)):
            helpers.append(rel)
            continue
        consumers = python_refs.get(rel, [])
        file, site = consumers[0] if consumers else (rel, {})
        python.append({'id': rel, 'file': file, 'key': site.get('key', path.stem),
                       'api': base + quote(rel), 'display_api': rel,
                       'ext': mirror(site.get('ext', '')), 'consumers': len(consumers)})
    corpus = {'encrypted': encrypted, 'python': python, 'helpers': helpers, 'subscriptions': [
        {'id': '饭太硬', 'api': 'http://www.饭太硬.cc/tv'},
        {'id': '王二小', 'api': 'http://tvbox.xn--4kq62z5rby2qupq9ub.top/'},
    ]}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / 'cases.json').write_text(json.dumps(corpus, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'encrypted_references': len(encrypted),
                      'unique_jar_classes': len({(x['display_jar'], x['api']) for x in encrypted}),
                      'python_spiders': len(python), 'python_helpers': len(helpers)}, ensure_ascii=False))
    print(OUTPUT / 'cases.json')


def report():
    for group in ['encrypted', 'python', 'subscriptions']:
        path = OUTPUT / (group + '.json')
        if not path.exists(): continue
        rows = json.loads(path.read_text(encoding='utf-8'))
        print(group, json.dumps(Counter(row.get('status', 'unknown') for row in rows), ensure_ascii=False))
        for row in rows:
            print(json.dumps({key: row[key] for key in ('id', 'stage', 'status', 'error') if key in row}, ensure_ascii=False))


def publish_report():
    """只发布完整收集的数据；保留失败状态，避免把测试收集器完成当作站点全部通过。"""
    corpus = json.loads((OUTPUT / 'cases.json').read_text(encoding='utf-8'))
    catalog = json.loads(audit.CATALOG.read_text(encoding='utf-8'))
    keep = {'id', 'file', 'key', 'api', 'jar', 'stage', 'status', 'class_resolved', 'class_loaded', 'home_valid',
            'class_count', 'list_count', 'player_url_present', 'parse', 'jx', 'first_frame', 'audio_progress',
            'position_ms', 'duration_ms', 'site_count'}

    def classify(row):
        error = row.get('error', '')
        if row.get('status') not in {'failed', 'timeout'}: return row.get('status')
        for kind, words in [
            ('class_missing', ['ClassNotFoundException']),
            ('missing_configuration', ['MissingSchema']),
            ('missing_dependency', ['ModuleNotFoundError', 'ImportError']),
            ('sdk_or_script_api', ['AttributeError', 'NoSuchMethodError']),
            ('timeout', ['TimeoutException', 'SocketTimeoutException']),
            ('network', ['ConnectionError', 'UnknownHostException', 'NameResolutionError']),
            ('media_response', ['ExoPlaybackException']),
            ('upstream_or_script_output', ['JSONDecodeError', 'NoneType', 'UnboundLocalError', 'KeyError', 'IndexError']),
        ]:
            if any(word in error for word in words): return kind
        return 'source_error'

    def clean(row):
        result = {key: value for key, value in row.items() if key in keep}
        result['result_kind'] = classify(row)
        # 原始异常留在本地 build 日志；公开报告不携带接口参数、播放链接或 Cookie。
        if 'error' in row: result['error_type'] = row['error'].split(':', 1)[0]
        if 'sources' in row: result['sources'] = [clean(child) for child in row['sources']]
        return result

    document = {'metadata': json.loads((OUTPUT / 'metadata.json').read_text(encoding='utf-8')), 'summary': {}, 'results': {}}
    for group in ['encrypted', 'python', 'subscriptions']:
        rows = json.loads((OUTPUT / (group + '.json')).read_text(encoding='utf-8'))
        if len(rows) != len(corpus[group]) or {row['id'] for row in rows} != {row['id'] for row in corpus[group]}:
            raise ValueError(group + ' 验证覆盖不完整')
        if any(row.get('status') == 'running' for row in rows): raise ValueError(group + ' 尚未完成')
        document['summary'][group] = {'cases': len(rows), 'class_resolved': sum(row.get('class_resolved', False) for row in rows),
                                     'initialized': sum(row.get('class_loaded', False) for row in rows),
                                     'home_valid': sum(row.get('home_valid', False) for row in rows),
                                     'statuses': dict(Counter(row['status'] for row in rows)),
                                     'result_kinds': dict(Counter(classify(row) for row in rows))}
        if group == 'subscriptions':
            children = [child for row in rows for child in row.get('sources', [])]
            document['summary'][group] = {'cases': len(rows), 'site_counts': {row['id']: row.get('site_count', 0) for row in rows},
                                           'source_cases': len(children), 'class_resolved': sum(row.get('class_resolved', False) for row in children),
                                           'initialized': sum(row.get('class_loaded', False) for row in children),
                                           'statuses': dict(Counter(row['status'] for row in rows)),
                                           'source_statuses': dict(Counter(row['status'] for row in children))}
        document['results'][group] = [clean(row) for row in rows]
    document['repairs'] = [item for item in catalog.get('config_repairs', []) if item.get('scope') == 'source-compatibility-20261119']
    document['python_helpers'] = corpus['helpers']
    target = REPO / 'website/tvbox-docs/docs/public/source-validation.json'
    target.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(document['summary'], ensure_ascii=False))
    print(target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'report', 'publish'])
    parser.add_argument('--base', default='http://127.0.0.1:18080')
    args = parser.parse_args()
    if args.action == 'prepare': prepare(args.base)
    elif args.action == 'publish': publish_report()
    else: report()
