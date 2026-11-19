import json
from urllib.parse import urlencode

from com.github.catvod import Proxy
from com.github.catvod.crawler import Spider as JavaSpider


class Spider:
    _instance = None

    def __init__(self):
        self.extend = ""

    def __new__(cls, *args, **kwargs):
        if cls.__dict__.get("_instance") is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def init(self, extend=""):
        pass

    def homeContent(self, filter):
        return {"class": []}

    def homeVideoContent(self):
        return {"list": []}

    def categoryContent(self, tid, pg, filter, extend):
        return {"list": []}

    def detailContent(self, ids):
        return {"list": []}

    def searchContent(self, key, quick, pg="1"):
        return {"list": []}

    def playerContent(self, flag, id, vipFlags):
        return {}

    def liveContent(self, url):
        return ""

    def proxy(self, params):
        return [404, "text/plain", ""]

    def isVideoFormat(self, url):
        return False

    def manualVideoCheck(self):
        return False

    def action(self, action):
        return {}

    def danmaku(self, id, emit):
        raise NotImplementedError("Spider does not support live danmaku")

    def destroy(self):
        pass

    def getActivity(self):
        from com.github.catvod import Runtime

        return Runtime.activity()

    def getDependence(self):
        return []

    def loadSpider(self, name):
        spider = self.runtime.spider(name)
        if hasattr(self, "net"):
            spider.net = self.net
            spider.local = self.local
            spider.js = self.js
        if hasattr(self, "dialog"):
            spider.dialog = self.dialog
        return spider

    def loadModule(self, name):
        return self.runtime.load_reference(name)

    def proxyUrl(self, params=None, local=True):
        params = {} if params is None else params
        if not isinstance(params, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in params.items()):
            raise TypeError("Proxy parameters require a dict with string keys and values")
        if not isinstance(local, bool):
            raise TypeError("Proxy local flag requires a boolean")
        if "do" in params or "siteKey" in params:
            raise ValueError("Reserved proxy parameter")
        url = str(Proxy.getUrl(local))
        query = urlencode({"do": "py", "siteKey": self.siteKey, **params})
        return url + "?" + query

    def danmakuUrl(self, id):
        return str(JavaSpider.danmakuUrl(self.siteKey, id))

    # 旧 T3 脚本仍调用这些入口；请求、Cookie、取消均交给当前来源的宿主网络。
    def fetch(self, url, **kwargs):
        return self._request("GET", url, **kwargs)

    def post(self, url, data=None, **kwargs):
        if data is not None:
            kwargs["data"] = data
        return self._request("POST", url, **kwargs)

    def postJson(self, url, data=None, **kwargs):
        if data is not None and "json" not in kwargs:
            kwargs["json"] = data
        return self.post(url, **kwargs)

    def _request(self, method, url, **kwargs):
        import requests
        from requests.structures import CaseInsensitiveDict
        from requests.utils import get_encoding_from_headers

        timeout = kwargs.pop("timeout", 10)
        durations = timeout if isinstance(timeout, (tuple, list)) else (timeout,)
        if not durations or any(isinstance(value, bool) or not isinstance(value, (int, float)) or value <= 0 for value in durations):
            raise ValueError("timeout 必须是正数秒数")
        milliseconds = max(1, int(max(durations) * 1000))
        redirect = kwargs.pop("allow_redirects", True)
        # 宿主继续执行标准证书验证，不把脚本的 verify=False 扩散到共享客户端。
        kwargs.pop("verify", None)
        request = requests.Request(method, url, **kwargs).prepare()
        headers = dict(request.headers)
        options = {"method": method, "headers": headers, "buffer": 3,
                   "timeout": milliseconds, "callTimeout": max(1, int(sum(durations) * 1000)),
                   "redirect": 1 if redirect else 0}
        if request.body is not None:
            if not any(name.lower() == "content-type" for name in headers):
                headers["Content-Type"] = "application/octet-stream"
            options["body"] = list(request.body) if isinstance(request.body, bytes) else request.body
        value = self.net.req(request.url, options)
        if value.get("error"):
            raise requests.ConnectionError(value["error"])
        response = requests.Response()
        response.status_code = int(value["code"])
        response.url = value.get("url", request.url)
        response.request = request
        response.headers = CaseInsensitiveDict({key: ", ".join(item) if isinstance(item, list) else item
                                                 for key, item in value.get("headers", {}).items()})
        response._content = bytes(value["content"])
        response._content_consumed = True
        response.encoding = get_encoding_from_headers(response.headers)
        from http.cookies import SimpleCookie
        cookies = value.get("headers", {})
        for name, values in cookies.items():
            if name.lower() != "set-cookie":
                continue
            for item in values if isinstance(values, list) else [values]:
                parsed = SimpleCookie()
                parsed.load(item)
                for key, cookie in parsed.items():
                    response.cookies.set(key, cookie.value)
        return response

    def getCache(self, key):
        import time
        entry = self.local.get("legacy-cache:" + key)
        if entry is None:
            return None
        if entry.get("expires", 0) and entry["expires"] <= time.time():
            self.delCache(key)
            return None
        return entry.get("value")

    def setCache(self, key, value, duration=0):
        import time
        if isinstance(duration, bool) or not isinstance(duration, (int, float)) or duration < 0:
            raise ValueError("缓存时间必须是非负秒数")
        self.local.set("legacy-cache:" + key, {"value": value, "expires": time.time() + duration if duration else 0})

    def delCache(self, key):
        self.local.delete("legacy-cache:" + key)

    @staticmethod
    def html(content):
        from lxml import etree
        return etree.HTML(content)

    @staticmethod
    def regStr(content, reg, group=1):
        import re
        match = re.search(reg, content)
        return match.group(group) if match else ""

    @staticmethod
    def cleanText(content):
        return content.replace("\r", "").replace("\n", "").replace("\t", "").strip()

    @staticmethod
    def xpText(node, expression):
        value = node.xpath(expression)
        if isinstance(value, list):
            value = value[0] if value else ""
        return str(value).strip()

    def getProxyUrl(self, local=True):
        return self.proxyUrl(local=local)

    def log(self, msg):
        if isinstance(msg, (dict, list)):
            print(json.dumps(msg, ensure_ascii=False))
        else:
            print(f"{msg}")

    @staticmethod
    def link(name, target):
        if not isinstance(target, dict):
            raise TypeError("link requires a target dict")
        target = json.dumps(target, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        return f"[a=cr:{target}/]{name!s}[/a]"

    @staticmethod
    def page(items, pg, limit, total=None):
        pg = max(1, int(pg))
        if isinstance(limit, bool) or not isinstance(limit, int) or limit < 1:
            raise ValueError("page requires a positive limit")
        if total is None:
            total = len(items)
            items = items[(pg - 1) * limit : pg * limit]
        if isinstance(total, bool) or not isinstance(total, int) or total < 0:
            raise ValueError("page requires a nonnegative total")
        return {"list": items, "page": pg, "pagecount": max(1, (total + limit - 1) // limit), "limit": limit, "total": total}
