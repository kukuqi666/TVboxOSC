package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkDns implements Dns {

    private final ConcurrentHashMap<String, String> map;
    private volatile Supplier<Doh> supplier;
    private volatile DnsOverHttps doh;

    public OkDns() {
        this.map = new ConcurrentHashMap<>();
    }

    public synchronized void setDoh(Doh item) {
        HttpUrl url = HttpUrl.parse(item.getUrl());
        this.doh = url == null ? null : new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).bootstrapDnsHosts(item.getHosts()).build();
        this.supplier = null;
    }

    public synchronized void setDoh(Supplier<Doh> supplier) {
        this.supplier = supplier;
    }

    public void clear() {
        map.clear();
    }

    public void addAll(List<String> hosts) {
        map.putAll(hosts.stream().filter(Objects::nonNull).map(host -> host.split("=", 2)).filter(splits -> splits.length == 2).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim(), (oldHost, newHost) -> newHost)));
    }

    private String get(String hostname) {
        String target = map.get(hostname);
        if (target != null) return target;
        for (Map.Entry<String, String> entry : map.entrySet()) if (Util.containOrMatch(hostname, entry.getKey())) return entry.getValue();
        return hostname;
    }

    /** 在代理转发、DNS 和 hosts 映射前拒绝已发现的危险资源域名。 */
    public static void checkHost(String hostname) throws UnknownHostException {
        String host = hostname.toLowerCase(java.util.Locale.ROOT).replaceAll("\\.+$", "");
        String blocked = "fs-im-kefu.7moor-fs1.com";
        if (host.equals(blocked) || host.endsWith("." + blocked))
            throw new UnknownHostException("已阻止风险资源域名：" + host);
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        checkHost(hostname);
        checkHost(get(hostname));
        Supplier<Doh> supplier = this.supplier;
        if (supplier != null) initDoh(supplier);
        return (doh != null ? doh : Dns.SYSTEM).lookup(get(hostname));
    }

    private synchronized void initDoh(Supplier<Doh> supplier) {
        if (supplier != this.supplier) return;
        setDoh(supplier.get());
    }
}
