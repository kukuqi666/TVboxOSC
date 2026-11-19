package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Normalizes supported source conventions independently of Android and the selected player. */
public final class ConfigCompat {
    public interface Fetcher { Resource fetch(String url) throws Exception; }
    public static final class Resource {
        public final String url, content;
        public Resource(String url, String content) { this.url = url; this.content = content; }
    }
    private ConfigCompat() {}

    public static JsonObject resolve(Resource resource, Fetcher fetcher) throws Exception {
        return resolve(resource, fetcher, new HashSet<>(), 0);
    }
    private static JsonObject resolve(Resource resource, Fetcher fetcher, Set<String> visited, int depth) throws Exception {
        if (depth > 8 || !visited.add(resource.url)) throw new IllegalArgumentException("订阅仓库存在循环引用");
        JsonObject object = JsonParser.parseString(resource.content).getAsJsonObject();
        normalizeSchema(object);
        if (object.has("msg")) throw new IllegalArgumentException(SourceDescriptor.string(object, "msg"));
        if (object.has("urls")) {
            JsonArray urls = array(object, "urls");
            if (urls.size() == 0 || !urls.get(0).isJsonObject()) throw new IllegalArgumentException("订阅仓库没有配置地址");
            String url = SourceDescriptor.string(urls.get(0).getAsJsonObject(), "url");
            if (url.isEmpty()) throw new IllegalArgumentException("订阅仓库地址为空");
            return resolve(fetcher.fetch(SourceDescriptor.resolve(resource.url, url)), fetcher, visited, depth + 1);
        }
        normalize(object, resource.url);
        // Optional rule, header, proxy and DNS arrays may be loaded from external files.
        for (String key : new String[]{"rules", "headers", "proxy", "doh"}) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) continue;
            JsonArray result = new JsonArray();
            JsonArray inputs = new JsonArray();
            if (value.isJsonArray()) inputs = value.getAsJsonArray(); else if (value.isJsonPrimitive()) inputs.add(value);
            for (JsonElement item : inputs) {
                if (item.isJsonObject()) result.add(item);
                else if (item.isJsonPrimitive()) {
                    try {
                        Resource fetched = fetcher.fetch(SourceDescriptor.resolve(resource.url, item.getAsString()));
                        JsonElement parsed = JsonParser.parseString(fetched.content);
                        if (parsed.isJsonArray()) for (JsonElement entry : parsed.getAsJsonArray()) {
                            if (entry.isJsonObject()) { normalize(entry, fetched.url); result.add(entry); }
                        }
                    } catch (Exception ignored) { /* An unavailable optional list is treated as empty. */ }
                }
            }
            object.add(key, result);
        }
        return object;
    }

    /** 仓库与 zyplayer 导出统一为宿主使用的数组结构。 */
    public static void normalizeSchema(JsonObject object) {
        for (String key : new String[]{"sites", "parses", "lives"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("data")
                    && value.getAsJsonObject().get("data").isJsonArray()) {
                object.add(key, value.getAsJsonObject().getAsJsonArray("data"));
            }
        }
        for (String[] alias : new String[][]{{"iptv", "lives"}, {"analyzes", "parses"}}) {
            JsonElement value = object.get(alias[0]);
            if (!object.has(alias[1]) && value != null && value.isJsonObject()
                    && value.getAsJsonObject().has("data") && value.getAsJsonObject().get("data").isJsonArray()) {
                object.add(alias[1], value.getAsJsonObject().getAsJsonArray("data"));
            }
        }
        if (object.has("storeHouse") && object.get("storeHouse").isJsonArray()) {
            JsonArray urls = array(object, "urls");
            Set<String> seen = new HashSet<>();
            for (JsonElement item : urls) if (item.isJsonObject()) seen.add(SourceDescriptor.string(item.getAsJsonObject(), "url"));
            for (JsonElement item : object.getAsJsonArray("storeHouse")) {
                if (!item.isJsonObject()) continue;
                JsonObject source = item.getAsJsonObject();
                String url = SourceDescriptor.string(source, "sourceUrl");
                if (url.isEmpty() || !seen.add(url)) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("name", SourceDescriptor.string(source, "sourceName"));
                entry.addProperty("url", url);
                urls.add(entry);
            }
            object.add("urls", urls);
            object.remove("storeHouse");
        }
    }
    public static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    public static String text(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString().trim() : value.toString();
    }
    public static Map<String, String> headers(JsonElement value) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            if (value != null && value.isJsonPrimitive()) value = JsonParser.parseString(value.getAsString());
            if (value != null && value.isJsonObject()) for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) result.put(headerKey(entry.getKey()), entry.getValue().getAsString().trim());
            }
        } catch (Exception ignored) {}
        return result;
    }
    public static String headerKey(String key) {
        if (key.equalsIgnoreCase("user-agent") || key.equalsIgnoreCase("ua")) return "User-Agent";
        if (key.equalsIgnoreCase("referer")) return "Referer";
        if (key.equalsIgnoreCase("cookie")) return "Cookie";
        if (key.equalsIgnoreCase("origin")) return "Origin";
        return key;
    }

    /** Shared structural path handling keeps decoded and plain configurations consistent. */
    public static void normalize(JsonElement element, String base) {
        if (element.isJsonObject()) normalizeSchema(element.getAsJsonObject());
        com.kukuqi.tvbox.engine.utils.ConfigPaths.normalize(element, base);
        normalizeHeaders(element);
    }
    private static void normalizeHeaders(JsonElement element) {
        if (element.isJsonArray()) { for (JsonElement item : element.getAsJsonArray()) normalizeHeaders(item); return; }
        if (!element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("ext")) continue;
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("header") && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                JsonObject header = new JsonObject(); headers(value).forEach(header::addProperty); entry.setValue(header);
            } else normalizeHeaders(value);
        }
    }
}
