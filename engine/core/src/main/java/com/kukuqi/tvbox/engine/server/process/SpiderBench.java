package com.kukuqi.tvbox.engine.server.process;

import android.text.TextUtils;

import com.kukuqi.tvbox.engine.api.loader.BaseLoader;
import com.kukuqi.tvbox.engine.server.Nano;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.kukuqi.tvbox.engine.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.NetTrace;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Backend of the spider test bench ({@code /spider.html}).
 *
 * <p>The bench keeps one prepared spider per session id so a source can be inspected method by
 * method without paying the module load cost again. Every call is bounded by a deadline and runs on
 * a worker thread, because a source that hangs must never block the local web server.</p>
 */
public class SpiderBench implements Process {

    private static final String PREFIX = "/spider/api";
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MIN_TIMEOUT = 1;
    private static final int MAX_TIMEOUT = 300;
    private static final int MAX_RESULT = 512 * 1024;

    /** Methods the bench offers; the argument list mirrors each signature. */
    private static final List<MethodSpec> SPECS = List.of(
            new MethodSpec("homeContent", boolean.class),
            new MethodSpec("homeVideoContent"),
            new MethodSpec("categoryContent", String.class, String.class, boolean.class, HashMap.class),
            new MethodSpec("detailContent", List.class),
            new MethodSpec("searchContent", String.class, boolean.class),
            new MethodSpec("searchContent", String.class, boolean.class, String.class),
            new MethodSpec("playerContent", String.class, String.class, List.class),
            new MethodSpec("action", String.class),
            new MethodSpec("proxy", Map.class),
            new MethodSpec("manualVideoCheck")
    );

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith(PREFIX);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String action = params.get("do");
            if (action == null) action = "";
            return switch (action) {
                case "load" -> load(params);
                case "call" -> call(params);
                case "clear" -> clear(params);
                default -> Nano.error("unknown action");
            };
        } catch (Throwable e) {
            return Nano.error(message(e));
        }
    }

    private Response load(Map<String, String> params) throws Exception {
        String id = required(params, "id");
        String api = required(params, "api").trim();
        String jar = value(params, "jar").trim();
        String ext = value(params, "ext");
        if (!api.startsWith("csp_") && !api.contains(".py") && !api.contains(".js") && !isNode(api))
            throw new IllegalArgumentException("api 需为 csp_ 类名、.py 或 .js 路径");
        String siteKey = "bench_" + id;
        long started = System.nanoTime();
        // 每个测试会话独享加载器，重新加载不影响正在播放的正式来源。
        BaseLoader loader = new BaseLoader();
        Spider spider;
        try {
            if (!jar.isEmpty()) loader.parseJar(jar, false);
            spider = loader.getSpider(siteKey, api, ext, jar);
        } catch (Throwable failure) { loader.destroy(); throw failure; }
        long loadMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Session previous = sessions.put(id, new Session(api, jar, ext, spider, loader));
        if (previous != null) previous.close();
        JSONObject result = new JSONObject();
        result.put("api", api);
        result.put("loadMillis", loadMillis);
        result.put("methods", methods(spider));
        return Nano.ok(result.toString());
    }

    private Response call(Map<String, String> params) throws Exception {
        String id = required(params, "id");
        Session session = sessions.get(id);
        if (session == null) throw new IllegalStateException("尚未载入来源");
        String name = required(params, "method");
        int index = parseInt(value(params, "overload"), 0);
        int timeout = clamp(parseInt(value(params, "timeout"), DEFAULT_TIMEOUT));
        JSONArray arguments = parseArguments(value(params, "args"));
        MethodSpec spec = session.spec(name, index);
        Method method = spec.find(session.spider.getClass());
        if (method == null) throw new IllegalStateException("来源未实现方法 " + name);
        Object[] values = spec.convert(arguments);
        Future<Object> future = Task.executor().submit(() -> spec.invoke(method, session.spider, values));
        long started = System.nanoTime();
        Object output;
        try {
            output = future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("执行超过 " + timeout + " 秒");
        } catch (ExecutionException e) {
            throw new Exception(message(e.getCause()), e.getCause());
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        JSONObject result = new JSONObject();
        result.put("method", name);
        result.put("elapsed", elapsed);
        result.put("type", output == null ? "null" : output.getClass().getSimpleName());
        result.put("value", render(output));
        result.put("play", playable(output));
        return Nano.ok(result.toString());
    }

    private Response clear(Map<String, String> params) {
        String id = value(params, "id");
        if (id.isEmpty()) {
            sessions.values().forEach(Session::close);
            sessions.clear();
        } else {
            Session session = sessions.remove(id);
            if (session != null) session.close();
        }
        return Nano.ok();
    }

    private static JSONArray methods(Spider spider) throws JSONException {
        JSONArray result = new JSONArray();
        for (MethodSpec spec : SPECS) {
            Method method = spec.find(spider.getClass());
            if (method == null) continue;
            JSONObject item = new JSONObject();
            item.put("name", spec.name);
            item.put("overload", spec.index(spider.getClass()));
            item.put("args", spec.describe());
            result.put(item);
        }
        return result;
    }

    /** {@code playerContent} output is forwarded to the App only when it carries a URL. */
    private static boolean playable(Object output) {
        if (!(output instanceof String text) || text.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject(text);
            String url = json.optString("url", "");
            if (!url.isEmpty()) return true;
            JSONArray candidates = json.optJSONArray("url");
            return candidates != null && candidates.length() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String render(Object output) throws Exception {
        if (output == null) return "null";
        if (output instanceof String text) return truncate(text);
        if (output instanceof JSONObject || output instanceof JSONArray || output instanceof Number || output instanceof Boolean) return truncate(String.valueOf(output));
        if (output.getClass().isArray()) {
            JSONArray result = new JSONArray();
            int length = Array.getLength(output);
            for (int i = 0; i < length; i++) result.put(renderValue(Array.get(output, i)));
            return truncate(result.toString());
        }
        return truncate(String.valueOf(output));
    }

    private static Object renderValue(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Map<?, ?> map) {
            JSONObject result = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), renderValue(entry.getValue()));
            return result;
        }
        if (value instanceof byte[] bytes) return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return value instanceof Number || value instanceof Boolean ? value : String.valueOf(value);
    }

    private static JSONArray parseArguments(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) return new JSONArray();
        String text = raw.trim();
        if (!text.startsWith("[")) text = "[" + text + "]";
        return new JSONArray(text);
    }

    static String message(Throwable error) {
        if (error == null) return "未知错误";
        Throwable cause = error;
        while (cause.getCause() != null && TextUtils.isEmpty(cause.getMessage())) cause = cause.getCause();
        String message = cause.getMessage();
        return TextUtils.isEmpty(message) ? cause.getClass().getSimpleName() : message;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_RESULT ? text : text.substring(0, MAX_RESULT) + "\n…（已截断）";
    }

    private static int clamp(int timeout) {
        return Math.max(MIN_TIMEOUT, Math.min(MAX_TIMEOUT, timeout));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String required(Map<String, String> params, String key) {
        String value = value(params, key).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("缺少参数 " + key);
        return value;
    }

    private static String value(Map<String, String> params, String key) {
        String value = params == null ? null : params.get(key);
        return value == null ? "" : value;
    }

    private static boolean isNode(String api) {
        return com.kukuqi.tvbox.engine.api.loader.NodeLoader.accepts(api);
    }

    private static final class Session {

        final String api;
        final String jar;
        final String ext;
        final Spider spider;
        final BaseLoader loader;
        final Map<String, Method> resolved = new ConcurrentHashMap<>();

        Session(String api, String jar, String ext, Spider spider, BaseLoader loader) {
            this.loader = loader;
            this.api = api;
            this.jar = jar;
            this.ext = ext;
            this.spider = spider;
        }

        MethodSpec spec(String name, int index) {
            List<MethodSpec> matches = SPECS.stream().filter(spec -> spec.name.equals(name)).toList();
            if (matches.isEmpty()) throw new IllegalArgumentException("不支持的方法 " + name);
            return index >= 0 && index < matches.size() ? matches.get(index) : matches.get(0);
        }

        void close() {
            // 释放来源时同步结束它留下的网络追踪，避免容器泄漏
            NetTrace.end("bench:" + api);
            loader.destroy();
        }
    }

    /** One bench entry: the name plus the declared parameter types of the spider contract. */
    private record MethodSpec(String name, Class<?>... parameters) {

        Method find(Class<?> type) {
            try {
                return type.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        int index(Class<?> type) {
            List<MethodSpec> matches = SPECS.stream().filter(spec -> spec.name.equals(name)).toList();
            return Math.max(0, matches.indexOf(this));
        }

        JSONArray describe() throws JSONException {
            JSONArray result = new JSONArray();
            for (Class<?> parameter : parameters) result.put(parameter.getSimpleName());
            return result;
        }

        Object[] convert(JSONArray arguments) throws Exception {
            Object[] values = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i];
                Object raw = i < arguments.length() ? arguments.get(i) : JSONObject.NULL;
                values[i] = coerce(type, raw);
            }
            return values;
        }

        @SuppressWarnings("unchecked")
        private static Object coerce(Class<?> type, Object raw) throws JSONException {
            boolean missing = raw == JSONObject.NULL || raw == null;
            if (type == String.class) return missing ? "" : String.valueOf(raw);
            if (type == boolean.class || type == Boolean.class) {
                if (missing) return false;
                if (raw instanceof Boolean value) return value;
                return "true".equalsIgnoreCase(String.valueOf(raw)) || "1".equals(String.valueOf(raw));
            }
            if (type == int.class || type == Integer.class) return missing ? 0 : ((Number) raw).intValue();
            if (type == long.class || type == Long.class) return missing ? 0L : ((Number) raw).longValue();
            if (type == double.class || type == Double.class) return missing ? 0D : ((Number) raw).doubleValue();
            if (type == List.class) {
                List<String> list = new ArrayList<>();
                if (raw instanceof JSONArray array) for (int i = 0; i < array.length(); i++) list.add(String.valueOf(array.get(i)));
                else if (!missing) list.add(String.valueOf(raw));
                return list;
            }
            if (type == Map.class || type == HashMap.class) {
                HashMap<String, String> map = new LinkedHashMap<>();
                if (raw instanceof JSONObject object) {
                    // keySet() 需要 API 27，minSdk 24 只能走 keys()
                    java.util.Iterator<String> iterator = object.keys();
                    while (iterator.hasNext()) {
                        String key = iterator.next();
                        map.put(key, object.optString(key, ""));
                    }
                }
                return map;
            }
            return missing ? null : raw;
        }

        Object invoke(Method method, Spider spider, Object[] values) throws Exception {
            try {
                return method.invoke(spider, values);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                throw cause instanceof Exception exception ? exception : new Exception(cause);
            }
        }
    }
}