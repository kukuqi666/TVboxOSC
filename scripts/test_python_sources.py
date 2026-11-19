"""验证 Python 来源的导入隔离、网络错误与失败释放。"""
import importlib.util
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch, Mock


class PythonSourceTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1] / 'engine/chaquo/src/main/python'
        self.path = patch.object(sys, 'path', [str(root)] + sys.path)
        self.path.start()
        self.addCleanup(self.path.stop)
        base = types.ModuleType('base.spider')
        class Spider:
            def destroy(self): pass
            def getDependence(self): return []
        base.Spider = Spider
        self.modules = patch.dict(sys.modules, {'base.spider': base})
        self.modules.start()
        self.addCleanup(self.modules.stop)
        spec = importlib.util.spec_from_file_location('runtime_source_fixture', root / 'app.py')
        self.app = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.app)
        self.cache = tempfile.TemporaryDirectory()
        self.addCleanup(self.cache.cleanup)
        self.sources = {}
        self.reader = patch.object(self.app, 'read_source', side_effect=lambda url, *a, **kw: self.sources[url].encode())
        self.reader.start()
        self.addCleanup(self.reader.stop)
        self.spiders = []
        self.addCleanup(self.close_all)

    def close_all(self):
        for spider in self.spiders:
            self.app.release(spider)

    def load(self, url, key=''):
        spider = self.app.spider(self.cache.name, url, key)
        self.spiders.append(spider)
        return spider

    def test_same_filename_sites_keep_independent_module_globals(self):
        for host, title in [('first', 'one'), ('second', 'two')]:
            self.sources[f'https://{host}.example/spider.py'] = f"from base.spider import Spider as Base\nsite={title!r}\nclass Spider(Base):\n def title(self): return site\n"
        first = self.load('https://first.example/spider.py')
        second = self.load('https://second.example/spider.py')
        self.assertEqual('one', first.title())
        self.assertEqual('two', second.title())
        self.assertNotEqual(type(first).__module__, type(second).__module__)

    def test_same_script_instances_and_relative_imports_are_isolated(self):
        url = 'https://example.org/spider.py'
        self.sources[url] = "from base.spider import Spider as Base\nfrom .helper import values\nclass Spider(Base):\n def init(self, ext): values.append(ext)\n def title(self): return values\n"
        self.sources['https://example.org/helper.py'] = 'values=[]'
        first, second = self.load(url, 'one'), self.load(url, 'two')
        self.app.init(first, 'one')
        self.app.init(second, 'two')
        self.assertEqual(['one'], first.title())
        self.assertEqual(['two'], second.title())
        namespace = first.runtime.namespace
        directory = Path(first.runtime.directory)
        self.app.destroy(first)
        self.assertFalse(directory.exists())
        self.assertFalse(any(name == namespace or name.startswith(namespace + '.') for name in sys.modules))
        self.assertEqual(['two'], second.title())

    def test_failed_import_leaves_no_namespace_or_runtime_directory(self):
        url = 'https://example.org/spider.py'
        self.sources[url] = "raise RuntimeError('broken source')"
        before = set(sys.modules)
        with self.assertRaisesRegex(RuntimeError, 'broken source'):
            self.load(url)
        self.assertFalse(any(name.startswith('_tv_py_') for name in set(sys.modules) - before))
        self.assertFalse(any(path.name.endswith('.py') for path in Path(self.cache.name).rglob('*')))

    def test_rejects_non_spider_export(self):
        url = 'https://example.org/spider.py'
        self.sources[url] = 'class Spider: pass'
        with self.assertRaisesRegex(TypeError, 'Spider subclass'):
            self.load(url)

    def test_http_uses_owned_network_and_bounded_timeout(self):
        net = Mock()
        net.reqParts.return_value = [json.dumps({'code': 200, 'headers': {}}), b'valid source']
        self.assertEqual(b'valid source', self.app.read_remote('https://example.org/source.py', net))
        url, options = net.reqParts.call_args.args
        self.assertEqual('https://example.org/source.py', url)
        self.assertEqual({'timeout': 10000, 'callTimeout': 10000, 'buffer': 3}, json.loads(options))

    def test_http_and_network_errors_cannot_be_executed_as_python(self):
        net = Mock()
        for code in [403, 404, 500]:
            net.reqParts.return_value = [json.dumps({'code': code}), b'not source']
            with self.assertRaises(Exception): self.app.read_remote('https://example.org/source.py', net)
        net.reqParts.return_value = [json.dumps({'code': 0, 'error': 'connection refused'}), b'']
        with self.assertRaisesRegex(RuntimeError, 'connection refused'):
            self.app.read_remote('https://example.org/source.py', net)
        with self.assertRaisesRegex(RuntimeError, 'network owner'):
            self.app.read_remote('https://example.org/source.py', None)

    def test_async_or_nan_output_is_rejected(self):
        async def result(): return {}
        with self.assertRaises(TypeError): self.app.encode_json(result())
        with self.assertRaises(ValueError): self.app.encode_json({'value': float('nan')})


if __name__ == '__main__':
    unittest.main()
