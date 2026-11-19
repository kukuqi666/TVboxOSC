package com.kukuqi.tvbox.engine.api;

import android.util.Base64;

import com.kukuqi.tvbox.engine.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class Decoder {

    private static final Pattern JS_URI = Pattern.compile("\"(\\.|\\.\\.)/(.?|.+?)\\.js\\?(.?|.+?)\"");

    public static String getJson(String url, String tag) throws Exception {
        try (Response res = OkHttp.newCall(url, tag).execute()) {
            HttpUrl httpUrl = res.request().url();
            int size = HttpUrl.parse(url).querySize();
            if (httpUrl.querySize() == size) url = httpUrl.toString();
            return verify(url, res.body().string());
        }
    }

    private static String verify(String url, String data) throws Exception {
        if (data.isEmpty()) throw new Exception();
        if (Json.isObj(data)) return fix(url, data);
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data.replaceAll("\\s+", ""));
        return fix(url, data);
    }

    public static String decode(String url, String data) throws Exception {
        return verify(url, data);
    }

    /**
     * 与 FongMi 对齐：脚本体里的 .js 相对引用必须按配置地址补全，
     * 否则多文件 JavaScript 源会取不到子脚本。
     * 先用 JS_URI 精确匹配带查询串的 .js 引用并原地补全，
     * 再用 __JS1__/__JS2__ 占位避免后续 ./ ../ 替换误伤已补全的绝对地址。
     */
    private static String fix(String url, String data) {
        // 只按 .js 引用的精确正则补全脚本体，随后交给结构化的 ConfigPaths 处理真正的路径字段。
        // 不能用 data.replace("../", ...) 这类全局替换：它会把 ext.token 里的 "a../b"、
        // rules.regex 里的 "../token" 也当成路径改写，导致源配置内容被破坏。
        Matcher matcher = JS_URI.matcher(data);
        while (matcher.find()) data = replaceJs(url, data, matcher.group());
        if (data.contains("__JS1__")) data = data.replace("__JS1__", "./");
        if (data.contains("__JS2__")) data = data.replace("__JS2__", "../");
        try {
            com.google.gson.JsonElement document = com.google.gson.JsonParser.parseString(data);
            com.kukuqi.tvbox.engine.utils.ConfigPaths.normalize(document, url);
            return document.toString();
        } catch (RuntimeException ignored) { return data; }
    }

    private static String replaceJs(String url, String data, String ext) {
        String t = ext.replace("\"./", "\"" + UriUtil.resolve(url, "./"));
        t = t.replace("\"../", "\"" + UriUtil.resolve(url, "../"));
        t = t.replace("./", "__JS1__").replace("../", "__JS2__");
        return data.replace(ext, t);
    }

    private static String cbc(String data) throws Exception {
        String decode = new String(Util.hex2byte(data), StandardCharsets.UTF_8).toLowerCase();
        String key = padEnd(decode.substring(decode.indexOf("$#") + 2, decode.indexOf("#$")));
        String iv = padEnd(decode.substring(decode.length() - 13));
        data = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        byte[] decrypted = Crypto.decryptAesCbc(Util.hex2byte(data), key.getBytes(StandardCharsets.UTF_8), iv.getBytes(StandardCharsets.UTF_8));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static String base64(String data) {
        String extract = extract(data);
        if (extract.isEmpty()) return data;
        return new String(Base64.decode(extract, Base64.DEFAULT));
    }

    private static String extract(String data) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9]{8}\\*\\*").matcher(data);
        return matcher.find() ? data.substring(data.indexOf(matcher.group()) + 10) : "";
    }

    private static String padEnd(String key) {
        return key + "0000000000000000".substring(key.length());
    }
}
