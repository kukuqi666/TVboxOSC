#!/usr/bin/env python3
"""检查统一发布目录，生成文件类型、引用关系和 JAR 消费者索引。"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import struct
from urllib.parse import quote, unquote, urlsplit, urlunsplit
from concurrent.futures import ThreadPoolExecutor
import urllib.request
import urllib.error
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
    if '模板' in path.name and suffix in {'.json', '.txt'}: return 'template'
    if suffix == '.jar':
        return 'jar'
    if suffix == '.js':
        return 'javascript'
    if suffix == '.py':
        return 'python'
    if suffix in {'.m3u', '.m3u8'}:
        return 'live'
    if suffix == '.json':
        if re.search(r'"(?:sites|urls|lives|storeHouse)"\s*:', text):
            return 'config'
        return 'rule'
    if suffix == '.txt':
        if '#EXTM3U' in text or '#genre#' in text:
            return 'live'
        if re.search(r'"(?:sites|urls|lives|storeHouse)"\s*:', text):
            return 'config'
    if suffix in {'.md', '.md5'}:
        return 'documentation' if suffix == '.md' else 'checksum'
    return 'resource'


def js_imports(text):
    """仅取代码中的静态 import/from/require，跳过注释和模板字符串示例。"""
    lexer = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|`(?:\\.|[^`\\])*`|//[^\n]*|/\*[\s\S]*?\*/|[A-Za-z_$][\w$]*|[^\s]')
    tokens = [m.group() for m in lexer.finditer(text) if not m.group().startswith(('//', '/*'))]
    references = set()
    for index, token in enumerate(tokens):
        if token not in {'import', 'from', 'require'}: continue
        tail = tokens[index + 1:index + 3]
        if tail and tail[0] == '(': tail = tail[1:]
        if tail and tail[0].startswith(('"', "'")):
            value = tail[0][1:-1]
            if value.startswith(('./', '../')): references.add(value)
    return references


def parse_config(text):
    """只放宽 Gson 支持的注释/末尾逗号；不修改字符串中的 URL 和正则。"""
    tokens = re.compile(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/|,\s*(?=[}\]])')
    content = tokens.sub(lambda m: m.group() if m.group().startswith('"') else ''.join('\n' if c == '\n' else ' ' for c in m.group()), text)
    document = json.loads(content)
    if not isinstance(document, dict): raise ValueError('配置根节点必须是对象')
    for key in ['sites', 'parses', 'lives']:
        if isinstance(document.get(key), dict) and isinstance(document[key].get('data'), list):
            document[key] = document[key]['data']
    for source, target in [('iptv', 'lives'), ('analyzes', 'parses')]:
        if target not in document and isinstance(document.get(source), dict) and isinstance(document[source].get('data'), list):
            document[target] = document[source]['data']
    if isinstance(document.get('storeHouse'), list):
        urls = document.setdefault('urls', [])
        seen = {entry.get('url') for entry in urls if isinstance(entry, dict)}
        for entry in document.pop('storeHouse'):
            if isinstance(entry, dict) and entry.get('sourceUrl') and entry['sourceUrl'] not in seen:
                urls.append({'name': entry.get('sourceName', ''), 'url': entry['sourceUrl']})
                seen.add(entry['sourceUrl'])
    for key in ['sites', 'urls', 'lives']:
        if key in document and not isinstance(document[key], list): raise ValueError(key + ' 必须是数组')
    return document


def dex_strings(data):
    """提取 DEX 字符串，检测跨模块类型及反射引用。"""
    if not data.startswith(b'dex\n'): raise ValueError('DEX 文件头无效')
    uint = lambda offset: struct.unpack_from('<I', data, offset)[0]
    strings = []
    for index in range(uint(56)):
        offset = uint(uint(60) + 4 * index)
        while data[offset] & 128: offset += 1
        offset += 1
        strings.append(data[offset:data.index(0, offset)].decode('utf-8', errors='replace'))
    return strings


def dex_classes(data):
    """读取实际 class_defs，引用了某类不代表 JAR 实现了该类。"""
    strings = dex_strings(data)
    uint = lambda offset: struct.unpack_from('<I', data, offset)[0]
    types = [strings[uint(uint(68) + 4 * index)] for index in range(uint(64))]
    return {types[uint(uint(100) + 32 * index)] for index in range(uint(96))}


def local_reference(owner, ref):
    body, _ = split_ref(ref)
    if body.startswith(RAW): target = REPO / unquote(body[len(RAW):])
    elif body.startswith(('./', '../')): target = (ROOT / owner).parent / unquote(body)
    else: return None
    target = target.resolve()
    return target.relative_to(ROOT).as_posix() if target.is_relative_to(ROOT) else None


def inspect_configs(records):
    problems, jars = [], {}
    for rel, record in records.items():
        if record['kind'] == 'jar':
            with zipfile.ZipFile(ROOT / rel) as archive:
                dex = [name for name in archive.namelist() if re.fullmatch(r'classes[0-9]*\.dex', name)]
                names = set().union(*(dex_classes(archive.read(name)) for name in dex)) if dex else set()
                incompatible = sorted({value for name in dex for value in dex_strings(archive.read(name)) if 'com/fongmi/quickjs/' in value or 'com.fongmi.quickjs.' in value})
                record['incompatible_sdk_references'] = incompatible
                if incompatible: problems.append({'file': rel, 'reason': 'JAR 引用了旧 QuickJS 类型，与 kukuqi 模块不兼容', 'references': incompatible})
                jars[rel] = names
                record['spider_classes'] = sorted(name[1:-1].replace('/', '.') for name in names if name.startswith('Lcom/github/catvod/spider/') and '$' not in name)
                record['android_dex'] = bool(dex)
                record['encrypted_payload'] = any(name.endswith('.guard') for name in archive.namelist())
    for rel, record in records.items():
        if record['kind'] not in {'config', 'template'}: continue
        try: document = parse_config((ROOT / rel).read_text(encoding='utf-8-sig'))
        except (ValueError, UnicodeError) as error:
            problems.append({'file': rel, 'reason': str(error)}); continue
        record['format'] = 'json-with-comments'
        if record['kind'] == 'template': continue
        seen = set()
        for site in document.get('sites', []):
            if not isinstance(site, dict):
                problems.append({'file': rel, 'reason': 'sites 成员必须是对象'}); continue
            key = str(site.get('key', ''))
            if not key or key in seen: problems.append({'file': rel, 'key': key, 'reason': '来源 key 为空或重复'})
            seen.add(key)
            api = site.get('api', '')
            if not isinstance(api, str) or not api: problems.append({'file': rel, 'key': key, 'reason': 'api 为空'}); continue
            jar = local_reference(rel, site.get('jar') or document.get('spider', ''))
            if api.startswith('csp_') and jar in jars:
                name = 'Lcom/github/catvod/spider/' + api[4:] + ';'
                if name not in jars[jar]: problems.append({'file': rel, 'key': key, 'api': api, 'jar': jar, 'reason': '静态 DEX 未发现爬虫类，需设备验证' if records[jar]['encrypted_payload'] else 'JAR 未实现该爬虫类', 'severity': 'warning' if records[jar]['encrypted_payload'] else 'error'})
    return problems


def audit(write=False, return_result=False):
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
        if path.suffix == '.js':
            # 保留字符串中的 URL，排除注释里的示例 import。
            tokens = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|`(?:\\.|[^`\\])*`|//[^\n]*|/\*[\s\S]*?\*/')
            text = tokens.sub(lambda m: m.group() if m.group()[0] in "\"'`" else ''.join('\n' if ch == '\n' else ' ' for ch in m.group()), text)
        # 配置中的注释不是运行时引用，按解析后的真实字段建立依赖。
        if record['kind'] in {'config', 'template'}:
            try: text = json.dumps(parse_config(text), ensure_ascii=False)
            except ValueError: pass
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
            imports = set()
            if path.suffix == '.js':
                imports = js_imports(content.decode('utf-8-sig', errors='replace'))
            if path.suffix in {'.json', '.txt', '.m3u', '.m3u8', '.js'}:
                for ref in (imports if path.suffix == '.js' else set(REL_RE.findall(text))):
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
    problems = inspect_configs(records)
    errors = [item for item in problems if item.get('severity', 'error') == 'error']
    missing.sort(key=lambda x: (x['file'], x['reference']))
    duplicate_groups = [paths for paths in duplicates.values() if len(paths) > 1]
    summary = {'files': len(records), 'jars': sum(x['kind'] == 'jar' for x in records.values()),
               'local_edges': sum(len(x['references']) for x in records.values()),
               'duplicate_groups': len(duplicate_groups),
               'repository_missing': sum(x['kind'] == 'repository' for x in missing),
               'upstream_missing': sum(x['kind'] == 'upstream' for x in missing),
               'checksum_errors': len(checksum_errors), 'config_errors': len(errors),
               'config_warnings': len(problems) - len(errors)}
    result = {**{key: previous[key] for key in ('source_repositories', 'deduplicated', 'symlinks_removed', 'removed_jar', 'saved_bytes', 'config_repairs', 'removed_configs', 'jar_migrations') if key in previous},
              'summary': summary, 'aliases': aliases, 'files': list(records.values()),
              'external_jars': sorted(external_jars), 'upstream_missing': missing, 'config_problems': problems}
    failures = bool(duplicate_groups or checksum_errors or missing or errors)
    if return_result: return result
    print(json.dumps(summary, ensure_ascii=False))
    for group in duplicate_groups:
        print('重复文件：' + '、'.join(group))
    for item in checksum_errors:
        print('MD5 不匹配：' + json.dumps(item, ensure_ascii=False))
    for item in missing:
        print('上游缺失：' + json.dumps(item, ensure_ascii=False))
    for item in errors:
        print('配置错误：' + json.dumps(item, ensure_ascii=False))
    if write and not failures:
        CATALOG.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('已更新 catalog.json，包含文件正向依赖和反向消费者。')
    elif not write and CATALOG.exists() and previous != result:
        print('索引已过期，请执行 python3 scripts/audit_configs.py --write')
        failures = True
    return 1 if failures else 0


def probe_urls(urls, timeout=10):
    """有界探测显式指定的地址；HTTP 可达不能替代 JAR 执行和实际播放验收。"""
    def probe(url):
        result = {'url': url}
        try:
            parts = urlsplit(url)
            if parts.scheme not in {'http', 'https'} or not parts.netloc: raise ValueError('仅支持 HTTP/HTTPS 地址')
            address = urlunsplit((parts.scheme, parts.netloc.encode('idna').decode(), quote(parts.path, safe='/%:@'), quote(parts.query, safe='=&%/:@?'), ''))
            request = urllib.request.Request(address, headers={'User-Agent': 'okhttp/3.15'})
            with urllib.request.urlopen(request, timeout=timeout) as response:
                content = response.read(2 * 1024 * 1024 + 1)
                result['status'] = response.status
                if len(content) > 2 * 1024 * 1024: raise ValueError('响应超出 2 MiB 探测上限')
                try:
                    document = parse_config(content.decode('utf-8-sig'))
                    result['format'] = 'configuration-json'
                    result['sites'] = len(document.get('sites', []))
                    result['depots'] = len(document.get('urls', []))
                except (UnicodeError, ValueError): result['format'] = 'encoded-or-other'
        except urllib.error.HTTPError as error:
            result.update(status=error.code, error='HTTP ' + str(error.code))
        except Exception as error: result['error'] = str(error)
        return result
    with ThreadPoolExecutor(max_workers=4) as executor: return list(executor.map(probe, dict.fromkeys(urls)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='校验通过后更新公开索引')
    parser.add_argument('--probe', action='append', default=[], metavar='URL', help='额外探测指定外网地址，最多并发 4 个；不代表实际播放成功')
    parser.add_argument('--timeout', type=int, choices=range(1, 31), default=10, help='每个网络请求的超时秒数（1–30）')
    args = parser.parse_args()
    status = audit(args.write)
    for item in probe_urls(args.probe, args.timeout) if args.probe else []:
        print('网络探测：' + json.dumps(item, ensure_ascii=False))
        if item.get('error'): status = 1
    raise SystemExit(status)
