import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text("versionCode 300\nversionName '3.0.0'\napplicationId 'com.kukuqi.tvbox.osc'\n")
        (self.root / 'README.md').write_text('# 项目\n- TVboxOSC：[旧下载](https://example.com/old)\n\n## 𝟭. 更新记录\n\n>* **旧记录 v2.1.26**\n', encoding='utf-8')
        (self.root / 'update.json').write_text('{"version":"2.1.26"}')
        self.manifest = {'version': '3.0.0', 'version_code': 300, 'package_name': 'com.kukuqi.tvbox.osc',
                         'apk_url': 'https://github.com/kukuqi666/TVboxOSC/releases/download/v3.0.0/TVboxOSC-v3.0.0.apk',
                         'sha256': hashlib.sha256(b'apk').hexdigest(), 'size': 3}
        self.manifest_path = self.root / 'published.json'
        self.manifest_path.write_text(json.dumps(self.manifest))
    def tearDown(self):
        self.temp.cleanup()
    def test_sync_preserves_history_and_is_idempotent(self):
        release.sync(self.manifest_path, self.root)
        first = (self.root / 'README.md').read_text(encoding='utf-8')
        self.assertIn('史诗级大更新', first)
        self.assertIn('旧记录 v2.1.26', first)
        self.assertIn(f"[下载 v3.0.0](https://gh-proxy.com/{self.manifest['apk_url']})", first)
        self.assertEqual(self.manifest, json.loads((self.root / 'update.json').read_text()))
        release.sync(self.manifest_path, self.root)
        self.assertEqual(first, (self.root / 'README.md').read_text(encoding='utf-8'))
    def test_sync_existing_release_keeps_update_history_unchanged(self):
        readme_path = self.root / 'README.md'
        heading = '## 𝟭. 更新记录'
        original = readme_path.read_text(encoding='utf-8').replace(
            heading, heading + '\n\n>* **2026/10/10 TVboxOSC v3.0.0：** 原有更新说明。')
        readme_path.write_text(original, encoding='utf-8')
        release.sync(self.manifest_path, self.root)
        updated = readme_path.read_text(encoding='utf-8')
        self.assertEqual(original.split(heading, 1)[1], updated.split(heading, 1)[1])
        self.assertIn(f"(https://gh-proxy.com/{self.manifest['apk_url']})", updated)
        self.assertEqual(self.manifest, json.loads((self.root / 'update.json').read_text()))
    def test_sync_never_downgrades_main(self):
        (self.root / 'update.json').write_text('{"version":"3.0.1"}')
        with self.assertRaises(ValueError): release.sync(self.manifest_path, self.root)
    def test_metadata_refuses_wrong_package_or_debug_apk(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'apk')
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')):
            with patch('release.subprocess.check_output', return_value="package: name='wrong'"):
                with self.assertRaises(ValueError): release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
            with patch('release.subprocess.check_output', return_value="package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'\napplication-debuggable"):
                with self.assertRaises(ValueError): release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
    def test_metadata_uses_real_file_and_signed_certificate(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'actual signed bytes')
        outputs = ["package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'", 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64]
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')), patch('release.subprocess.check_output', side_effect=outputs):
            release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
        manifest = json.loads(self.manifest_path.read_text())
        self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), manifest['sha256'])
        self.assertEqual(apk.stat().st_size, manifest['size'])
        self.assertEqual('a' * 64, manifest['signer_sha256'])
        self.assertIn('/owner/repo/', manifest['apk_url'])
    def test_metadata_accepts_build_tools_37_scheme_labels(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'signed apk')
        certificate = 'A' * 64
        signatures = 'Number of signers: 1\nV2 Signer: certificate SHA-256 digest: ' + certificate + '\nV3.2 Signer: certificate SHA-256 digest: ' + certificate
        outputs = ["package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'", signatures]
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')), patch('release.subprocess.check_output', side_effect=outputs):
            release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
        self.assertEqual('a' * 64, json.loads(self.manifest_path.read_text())['signer_sha256'])

    def test_metadata_refuses_conflicting_signing_certificates(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'signed apk')
        signatures = 'V2 Signer: certificate SHA-256 digest: ' + 'a' * 64 + '\nV3 Signer: certificate SHA-256 digest: ' + 'b' * 64
        outputs = ["package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'", signatures]
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')), patch('release.subprocess.check_output', side_effect=outputs):
            with self.assertRaises(ValueError):
                release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')

    def test_tag_mismatch_fails_before_github_access(self):
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/tags/v3.0.1', 'GITHUB_REF_NAME': 'v3.0.1'})):
            with self.assertRaises(ValueError): release.plan()
    def test_main_bump_publishes_and_unchanged_version_skips(self):
        import subprocess
        output = self.root / 'outputs'
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': 'commit', 'GITHUB_OUTPUT': str(output)}),
              patch('release.github_api', side_effect=[{'tag_name':'v2.1.26','assets':[]}, None]),
              patch('release.subprocess.run', return_value=subprocess.CompletedProcess([], 1, '', ''))):
            release.plan()
        self.assertIn('mode=publish', output.read_text())
        output.write_text('')
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/main', 'GITHUB_OUTPUT': str(output)}),
              patch('release.github_api', side_effect=[{'tag_name':'v3.0.0'}, {'draft':False}]),
              patch('release.Path.read_text', return_value=json.dumps(self.manifest))):
            release.plan()
        self.assertIn('mode=none', output.read_text())




class WebsiteAuditTest(unittest.TestCase):
    """防止整理后的重复、断链和过期索引重新进入发布。"""

    def setUp(self):
        import audit_configs
        self.audit = audit_configs
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        self.root = self.repo / 'website/tvbox-config'
        self.root.mkdir(parents=True)
        self.patchers = [
            patch.object(audit_configs, 'REPO', self.repo),
            patch.object(audit_configs, 'ROOT', self.root),
            patch.object(audit_configs, 'CATALOG', self.root / 'catalog.json'),
        ]
        for item in self.patchers:
            item.start()

    def tearDown(self):
        for item in reversed(self.patchers):
            item.stop()
        self.temp.cleanup()

    def test_graph_tracks_consumers_and_rejects_stale_catalog(self):
        from contextlib import redirect_stdout
        import io
        (self.root / 'rule.json').write_text('{"name":"规则"}')
        (self.root / 'tvbox.json').write_text(
            '{"ext":"' + self.audit.CONFIG_RAW + 'rule.json"}')
        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, self.audit.audit(write=True))
            self.assertEqual(0, self.audit.audit())
            data = json.loads((self.root / 'catalog.json').read_text())
            records = {item['path']: item for item in data['files']}
            self.assertEqual(['rule.json'], records['tvbox.json']['references'])
            self.assertEqual(['tvbox.json'], records['rule.json']['used_by'])
            (self.root / 'rule.json').write_text('{"name":"已修改"}')
            self.assertEqual(1, self.audit.audit())

    def test_duplicate_assets_are_rejected(self):
        from contextlib import redirect_stdout
        import io
        (self.root / 'one.js').write_text('const value = 1;')
        (self.root / 'two.js').write_text('const value = 1;')
        with redirect_stdout(io.StringIO()):
            self.assertEqual(1, self.audit.audit(write=True))
        self.assertFalse((self.root / 'catalog.json').exists())

    def test_repository_missing_and_bad_md5_are_rejected(self):
        from contextlib import redirect_stdout
        import io
        config = self.root / 'tvbox.json'
        config.write_text('{"ext":"' + self.audit.CONFIG_RAW + 'missing.json"}')
        with redirect_stdout(io.StringIO()):
            self.assertEqual(1, self.audit.audit(write=True))
            (self.root / 'script.js').write_text('const value = 1;')
            config.write_text('{"ext":"' + self.audit.CONFIG_RAW + 'script.js;md5;' + '0' * 32 + '"}')
            self.assertEqual(1, self.audit.audit(write=True))

    def test_recording_missing_dependency_never_bypasses_validation(self):
        from contextlib import redirect_stdout
        import io
        config = self.root / 'tvbox.json'
        config.write_text('{"sites":[],"ext":"./missing.js"}')
        (self.root / 'catalog.json').write_text(json.dumps({'upstream_missing': [{'file': 'tvbox.json', 'reference': './missing.js'}]}))
        with redirect_stdout(io.StringIO()):
            self.assertEqual(1, self.audit.audit(write=True))
            self.assertEqual(1, self.audit.audit())

    def test_schema_and_source_keys_are_checked(self):
        from contextlib import redirect_stdout
        import io
        (self.root / 'config.json').write_text('{"sites":{"data":[{"key":"a","api":"https://example.test/one"},{"key":"a","api":"https://example.test/two"}]}}')
        with redirect_stdout(io.StringIO()): self.assertEqual(1, self.audit.audit(write=True))
        (self.root / 'config.json').write_text('{"sites":{"data":[{"key":"a","api":"https://example.test/one"}]}}')
        with redirect_stdout(io.StringIO()): self.assertEqual(0, self.audit.audit(write=True))

    def test_js_imports_ignore_comments_and_template_examples(self):
        source = "// import x from './missing.js'\n const example=`import y from './also-missing.js'`; import a from './real.js'; const b=require('../lib.js');"
        self.assertEqual({'./real.js', '../lib.js'}, self.audit.js_imports(source))

    def test_legacy_quickjs_types_are_rejected_even_when_jar_path_exists(self):
        import io
        import struct
        import zipfile
        from contextlib import redirect_stdout
        def dex_with_reference(reference):
            data = bytearray(116)
            data[:8] = b'dex\n035\0'
            struct.pack_into('<II', data, 56, 1, 112)
            struct.pack_into('<I', data, 112, 116)
            data.extend(bytes([len(reference)]) + reference.encode() + b'\0')
            return data
        jar = self.root / 'source.jar'
        with zipfile.ZipFile(jar, 'w') as archive: archive.writestr('classes.dex', dex_with_reference('Lcom/fongmi/quickjs/QuickJSContext;'))
        with redirect_stdout(io.StringIO()): self.assertEqual(1, self.audit.audit(write=True))
        self.assertFalse((self.root / 'catalog.json').exists())
        with zipfile.ZipFile(jar, 'w') as archive: archive.writestr('classes.dex', dex_with_reference('Lcom/kukuqi/quickjs/QuickJSContext;'))
        with redirect_stdout(io.StringIO()): self.assertEqual(0, self.audit.audit(write=True))

    def test_probe_preserves_failure_and_uses_bounded_read(self):
        import io
        import urllib.error
        response = unittest.mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"sites":[]}'
        with patch.object(self.audit.urllib.request, 'urlopen', return_value=response) as request:
            result = self.audit.probe_urls(['https://example.test/a.json', 'https://example.test/a.json'], 3)
            self.assertEqual(1, len(result))
            self.assertEqual('configuration-json', result[0]['format'])
            response.read.assert_called_once_with(2 * 1024 * 1024 + 1)
            self.assertEqual(3, request.call_args.kwargs['timeout'])
        with patch.object(self.audit.urllib.request, 'urlopen', side_effect=urllib.error.HTTPError('https://example.test', 403, 'denied', {}, None)):
            self.assertEqual(403, self.audit.probe_urls(['https://example.test'])[0]['status'])


class PythonRuntimePackagingTest(unittest.TestCase):
    def test_requirement_rewrite_keeps_resources_and_removes_formatter_tests(self):
        import io
        import zipfile
        import prepare_python_runtime as runtime
        original = io.BytesIO()
        with zipfile.ZipFile(original, 'w') as archive:
            archive.writestr('pkg/data.json', b'{"ok":true}')
            archive.writestr(runtime.CXX, b'old-cxx')
            archive.writestr('yapftests/test_format.py', b'test')
        updated = runtime.rewrite_zip(original.getvalue(), lambda name, data: b'host-cxx' if name == runtime.CXX else data, omit_tests=True)
        with zipfile.ZipFile(io.BytesIO(updated)) as archive:
            self.assertEqual(b'{"ok":true}', archive.read('pkg/data.json'))
            self.assertEqual(b'host-cxx', archive.read(runtime.CXX))
            self.assertNotIn('yapftests/test_format.py', archive.namelist())

    def test_native_libraries_reject_wrong_abi_and_page_alignment(self):
        import struct
        import prepare_python_runtime as runtime
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<H', data, 18, 183)
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<I', data, 64, 1)
        struct.pack_into('<Q', data, 112, 16384)
        runtime.verify_elf(data, 'valid.so')
        struct.pack_into('<Q', data, 112, 4096)
        with self.assertRaises(ValueError): runtime.verify_elf(data, '4k.so')
        struct.pack_into('<H', data, 18, 40)
        with self.assertRaises(ValueError): runtime.verify_elf(data, 'arm32.so')

    def test_corrupt_download_never_replaces_verified_input(self):
        import prepare_python_runtime as runtime
        with tempfile.TemporaryDirectory() as temporary, patch.object(runtime, 'CACHE', Path(temporary)):
            path = Path(temporary) / 'input.whl'
            path.write_bytes(b'previous')
            def corrupt(url, part): part.write_bytes(b'corrupt')
            with patch.object(runtime, 'download', side_effect=corrupt), self.assertRaises(ValueError):
                runtime.fetch('input.whl', '0' * 64, 'https://example.test/')
            self.assertEqual(b'previous', path.read_bytes())
            self.assertFalse(path.with_suffix('.part').exists())


if __name__ == '__main__':
    unittest.main()
