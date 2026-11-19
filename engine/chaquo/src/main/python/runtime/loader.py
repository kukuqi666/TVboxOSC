import builtins
import hashlib
import importlib
import json
import os
import shutil
import sys
import threading
import types
import uuid
from importlib.machinery import ModuleSpec
from importlib._bootstrap import _ModuleLockManager, _lock_unlock_module
from urllib.parse import urljoin, urlsplit


class SourceNotFoundError(FileNotFoundError):
    pass


def cleanup_failure(failure, error):
    if failure is None:
        return error
    contexts = set()
    current = failure
    while current is not None and id(current) not in contexts:
        if current is error:
            return failure
        contexts.add(id(current))
        current = current.__context__
    current = error
    while current.__context__ is not None and id(current.__context__) not in contexts:
        contexts.add(id(current))
        current = current.__context__
    current.__context__ = failure.__context__
    failure.__context__ = error
    return failure


def raise_failure(failure):
    context = failure.__context__
    try:
        raise failure
    except BaseException:
        failure.__context__ = context
        raise


class _Load:
    def __init__(self):
        self.owner = threading.get_ident()
        self.done = threading.Event()
        self.value = None
        self.error = None


class Runtime:
    def __init__(self, cache, source, site_key, read):
        identity = hashlib.sha256(json.dumps([site_key, source], ensure_ascii=False).encode()).hexdigest()
        generation = uuid.uuid4().hex
        self._cache = os.path.realpath(cache)
        self.directory = os.path.join(self._cache, identity, generation)
        self.namespace = "_tv_py_" + generation
        self.source = source
        self.site_key = site_key
        entry = os.path.basename(urlsplit(source).path).removesuffix(".py")
        self.entry = entry if entry.isidentifier() else "spider"
        self._read = read
        self._lock = threading.RLock()
        self._closed = False
        self._modules = {}
        self._spiders = {}
        self._files = {}
        self._references = {}
        self._declared = set()
        self._downloads = {}
        self._loading = {}
        self._waiting = {}
        self._executing = threading.local()
        os.makedirs(self.directory, exist_ok=True)
        self._package("")
        sys.meta_path.insert(0, self)

    def _check_open(self):
        if self._closed:
            raise RuntimeError("Python spider runtime is closed")

    def _name(self, name):
        reference = str(name).removeprefix("./").removesuffix(".py")
        if not reference:
            raise ValueError("invalid Python dependency name")
        name = reference.replace("/", ".")
        with self._lock:
            self._check_open()
            if any(not part.isidentifier() for part in reference.split("/")):
                name = "_file_" + hashlib.sha256(reference.encode()).hexdigest()
                self._references[name] = reference + ".py"
        return name

    def _package(self, name):
        full_name = self.namespace + ("." + name if name else "")
        with _ModuleLockManager(full_name), self._lock:
            self._check_open()
            if name in self._modules:
                return self._modules[name]
            module = types.ModuleType(full_name)
            module.__package__ = full_name
            module.__path__ = [os.path.join(self.directory, *name.split(".")) if name else self.directory]
            module.__spec__ = ModuleSpec(full_name, loader=self, is_package=True)
            self._register(name, module)
            return module

    def _register(self, name, module):
        self._modules[name] = module
        sys.modules[module.__name__] = module
        if name:
            parent, _, child = name.rpartition(".")
            setattr(self._modules[parent], child, module)

    def _remove(self, name, module):
        if self._modules.get(name) is not module:
            return
        self._modules.pop(name)
        if sys.modules.get(module.__name__) is module:
            sys.modules.pop(module.__name__)
        parent, _, child = name.rpartition(".")
        parent_module = self._modules.get(parent)
        if getattr(parent_module, child, None) is module:
            delattr(parent_module, child)

    def _resolve(self, reference):
        if urlsplit(self.source).scheme in ("http", "https", "file"):
            return urljoin(self.source, reference)
        return os.path.join(os.path.dirname(os.path.abspath(self.source)), reference)

    def _download(self, name, source=None):
        with self._lock:
            self._check_open()
            if name in self._files:
                return self._files[name]
            pending = self._downloads.get(name)
            if pending is None:
                pending = self._downloads[name] = _Load()
                owner = True
            else:
                owner = False
            reference = self._references.get(name, name.replace(".", "/") + ".py")
        if not owner:
            return self._wait(pending)
        try:
            payload = self._read(source if source is not None else self._resolve(reference))
            return self._save_download(name, payload, pending)
        except BaseException as error:
            with self._lock:
                self._downloads.pop(name, None)
                pending.error = error
                pending.done.set()
            raise

    def _save_download(self, name, payload, pending):
        path = os.path.join(self.directory, *name.split(".")) + ".py"
        with self._lock:
            self._check_open()
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as stream:
                stream.write(payload)
            pending.value = self._files[name] = (path, payload)
            self._downloads.pop(name)
            pending.done.set()
        return pending.value

    def _wait(self, pending, name=None):
        thread = threading.get_ident()
        with self._lock:
            self._check_open()
            if pending.done.is_set():
                if pending.error is not None:
                    raise pending.error
                return pending.value
            self._waiting[thread] = pending.owner
            owner = pending.owner
            while owner in self._waiting and owner != thread:
                owner = self._waiting[owner]
            if owner == thread:
                self._waiting.pop(thread)
                if name in self._modules:
                    return self._modules[name]
                raise ImportError("Circular Python source loading")
        try:
            pending.done.wait()
            with self._lock:
                self._check_open()
                if pending.error is not None:
                    raise pending.error
                return pending.value
        finally:
            with self._lock:
                self._waiting.pop(thread, None)

    def load_reference(self, reference):
        return self.load_module(self._name(reference))

    def _parents(self, name):
        parts = name.split(".")
        for index in range(1, len(parts)):
            self._load_parent(".".join(parts[:index]))

    def _load_parent(self, parent):
        with self._lock:
            self._check_open()
            initializer = parent + ".__init__"
            initialize = initializer in self._declared or initializer in self._files or parent in self._loading
        if initialize:
            self.load_module(parent)
        else:
            self._package(parent)

    def find_spec(self, fullname, path=None, target=None):
        if not fullname.startswith(self.namespace + "."):
            return None
        name = fullname[len(self.namespace) + 1:]
        with self._lock:
            self._check_open()
            known = self._declared | self._files.keys()
            package = name + ".__init__" in known or any(item.startswith(name + ".") for item in known)
        return ModuleSpec(fullname, self, is_package=package)

    def create_module(self, spec):
        return None

    def exec_module(self, module):
        name = module.__name__[len(self.namespace) + 1:]
        self._execute_module(name, module)

    def _configure_module(self, path, package, module):
        module.__file__ = path
        module.__package__ = module.__name__ if package else module.__name__.rpartition(".")[0]
        module.__spec__.origin = path
        if package:
            module.__path__ = [os.path.dirname(path)]
        module.__dict__["__builtins__"] = {**vars(builtins), "__import__": self._import}

    def _restore(self, name, module, snapshot):
        if snapshot is None or self._modules.get(name) is not module:
            self._remove(name, module)
            return
        module.__dict__.clear()
        module.__dict__.update(snapshot)
        for child_name, child in self._modules.items():
            parent, _, attribute = child_name.rpartition(".")
            if parent == name:
                setattr(module, attribute, child)

    def load_module(self, name):
        name = name.removesuffix(".__init__")
        self._parents(name)
        with self._lock:
            self._check_open()
            module = self._modules.get(name)
            pending = self._loading.get(name)
            upgrade = pending is None and module is not None and module.__spec__.origin is None and name + ".__init__" in self._declared | self._files.keys()
        if pending is not None:
            return self._await_module(name, pending)
        if upgrade:
            return self._initialize_package(name, module)
        module = importlib.import_module(self.namespace + "." + name)
        with self._lock:
            self._check_open()
        return module

    def _await_module(self, name, pending):
        if not getattr(self._executing, "depth", 0):
            return self._wait(pending, name)
        _lock_unlock_module(self.namespace + "." + name)
        with self._lock:
            self._check_open()
            if pending.error is not None:
                raise pending.error
            return self._modules[name]

    def _initialize_package(self, name, module):
        with _ModuleLockManager(module.__name__):
            with self._lock:
                self._check_open()
                if module.__spec__.origin is not None:
                    return module
                snapshot = module.__dict__.copy()
                module.__spec__ = ModuleSpec(module.__name__, self, is_package=True)
                module.__spec__._initializing = True
            try:
                self._execute_module(name, module, snapshot)
            finally:
                module.__spec__._initializing = False
        return module

    def _execute_module(self, name, module, snapshot=None):
        with self._lock:
            self._check_open()
            known = self._declared | self._files.keys()
            package = name + ".__init__" in known
            pending = self._loading[name] = _Load()
            self._register(name, module)
        depth = getattr(self._executing, "depth", 0)
        self._executing.depth = depth + 1
        try:
            self._evaluate_module(name, module, known, package)
            self._complete_module(name, module, pending)
        except BaseException as error:
            self._fail_module(name, module, snapshot, pending, error)
            raise
        finally:
            self._executing.depth = depth

    def _evaluate_module(self, name, module, known, package):
        if package or not any(item.startswith(name + ".") for item in known):
            path, payload = self._module_source(name, package)
            self._configure_module(path, package, module)
            exec(compile(payload, path, "exec"), module.__dict__)
        else:
            module.__path__ = [os.path.join(self.directory, *name.split("."))]

    def _module_source(self, name, package):
        try:
            return self._download(name + ".__init__" if package else name)
        except SourceNotFoundError as error:
            raise ModuleNotFoundError("No module named '" + name + "'", name=name) from error

    def _complete_module(self, name, module, pending):
        with self._lock:
            self._check_open()
            pending.value = module
            self._loading.pop(name)
            pending.done.set()

    def _fail_module(self, name, module, snapshot, pending, error):
        with self._lock:
            self._restore(name, module, snapshot)
            self._loading.pop(name, None)
            pending.error = error
            pending.done.set()

    def _import(self, name, globals=None, locals=None, fromlist=(), level=0):
        with self._lock:
            self._check_open()
            known = any(item == name or item.startswith(name + ".") for item in self._declared | self._files.keys())
        if level:
            package = (globals or {}).get("__package__", "")
            prefix = package.split(".")
            if level > len(prefix) or prefix[0] != self.namespace:
                raise ImportError("relative import outside Python spider runtime")
            local_name = ".".join(prefix[1 : len(prefix) - level + 1])
            local_name = ".".join(filter(None, (local_name, name)))
            module = self.load_module(local_name) if local_name else self._package("")
        elif known:
            module = self.load_module(name)
        else:
            try:
                return builtins.__import__(name, globals, locals, fromlist, level)
            except ModuleNotFoundError as error:
                if error.name != name.split(".")[0]:
                    raise
                try:
                    self._download(name)
                except SourceNotFoundError:
                    raise error from None
                module = self.load_module(name)
        if fromlist:
            self._import_children(module, fromlist)
            return module
        with self._lock:
            self._check_open()
            return self._modules[name.split(".")[0]] if not level else module

    def _import_children(self, module, fromlist, recursive=False):
        for item in fromlist:
            if not isinstance(item, str):
                raise TypeError("Import names must be strings")
            if item == "*":
                if not recursive and hasattr(module, "__all__"):
                    self._import_children(module, module.__all__, True)
            else:
                self._import_child(module, item)

    def _import_child(self, module, item):
        if hasattr(module, "__path__"):
            child = ".".join(filter(None, (module.__name__.removeprefix(self.namespace).lstrip("."), item)))
            with self._lock:
                self._check_open()
                loading = self._modules.get(child) if child in self._loading else None
            if not hasattr(module, item) or loading is not None and getattr(module, item) is loading:
                self.load_module(child)

    def spider(self, name=None):
        from base.spider import Spider

        name = self.entry if name is None else self._name(name)
        module = self.load_module(name)
        cls = getattr(module, "Spider", None)
        if not isinstance(cls, type) or cls is Spider or not issubclass(cls, Spider):
            raise TypeError("Python source must export a Spider subclass")
        cls.runtime = self
        cls.siteKey = self.site_key
        return self.bind(cls())

    def bind(self, spider):
        with self._lock:
            self._check_open()
            spider.runtime = self
            spider.siteKey = self.site_key
            self._spiders[id(spider)] = spider
        return spider

    def bind_helpers(self, spider):
        with self._lock:
            self._check_open()
            helpers = spider.net, spider.local, spider.js, spider.dialog
            for child in self._spiders.values():
                if child is not spider:
                    child.net, child.local, child.js, child.dialog = helpers

    def create(self):
        try:
            self._download(self.entry, self.source)
            return self.spider()
        except BaseException as error:
            try:
                self.close()
            except BaseException as closing:
                raise_failure(cleanup_failure(error, closing))
            raise

    def prepare(self, spider):
        names = [self._name(name) for name in spider.getDependence() or []]
        with self._lock:
            self._check_open()
            self._declared.update(names)
        for name in names:
            self._download(name)

    def close(self):
        with self._lock:
            directory = os.path.realpath(self.directory)
            if os.path.commonpath((directory, self._cache)) != self._cache or directory == self._cache:
                raise ValueError("Python runtime directory is outside its cache")
            if not self._closed:
                self._close_modules()
            self._remove_directory(directory)

    def _close_modules(self):
        self._closed = True
        sys.meta_path.remove(self)
        for name in list(sys.modules):
            if name == self.namespace or name.startswith(self.namespace + "."):
                sys.modules.pop(name)
        self._modules.clear()
        self._spiders.clear()
        self._files.clear()
        self._references.clear()
        self._declared.clear()
        for pending in (*self._downloads.values(), *self._loading.values()):
            pending.done.set()
        self._downloads.clear()
        self._loading.clear()

    def _remove_directory(self, directory):
        try:
            shutil.rmtree(directory)
        except FileNotFoundError:
            pass
