package com.kukuqi.tvbox.engine.server;

import com.kukuqi.tvbox.engine.api.config.LiveConfig;
import com.kukuqi.tvbox.engine.bean.Device;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.kukuqi.tvbox.engine.server.process.Action;
import com.kukuqi.tvbox.engine.server.process.Cache;
import com.kukuqi.tvbox.engine.server.process.Image;
import com.kukuqi.tvbox.engine.server.process.DownloadTask;
import com.kukuqi.tvbox.engine.server.process.Local;
import com.kukuqi.tvbox.engine.server.process.Upload;
import com.kukuqi.tvbox.engine.server.process.Log;
import com.kukuqi.tvbox.engine.server.process.Media;
import com.kukuqi.tvbox.engine.server.process.Parse;
import com.kukuqi.tvbox.engine.server.process.Proxy;
import com.kukuqi.tvbox.engine.server.process.Remote;
import com.kukuqi.tvbox.engine.server.process.SpiderBench;
import com.github.catvod.utils.Asset;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Nano extends NanoHTTPD {

    private static final String INDEX = "index.html";

    private List<Process> process;

    /** 分块上传要收数 MiB 的资料，NanoHTTPD 预设的 5 秒读超时不够用。 */
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    public Nano(int port) {
        super(port);
        addProcess();
    }

    @Override
    protected ClientHandler createClientHandler(java.net.Socket acceptSocket, java.io.InputStream inputStream) {
        try {
            acceptSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
        } catch (java.net.SocketException ignored) {
        }
        return super.createClientHandler(acceptSocket, inputStream);
    }

    private void addProcess() {
        process = new ArrayList<>();
        process.add(new Action());
        process.add(new Cache());
        process.add(new Image());
        process.add(new Local());
        process.add(new Upload());
        process.add(new DownloadTask());
        process.add(new Media());
        process.add(new Parse());
        process.add(new Proxy());
        process.add(new SpiderBench());
        process.add(new Remote());
        process.add(new Log());
    }

    public static Response ok() {
        return ok("OK");
    }

    public static Response ok(String text) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text);
    }

    public static Response error(String text) {
        return error(Response.Status.INTERNAL_ERROR, text);
    }

    public static Response error(Response.Status status, String text) {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, text);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String url = session.getUri().trim();
        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) parse(session, files);
        if (url.startsWith("/tvbus")) return ok(LiveConfig.getResp());
        if (url.startsWith("/device")) return ok(Device.get().toString());
        for (Process process : process) if (process.isRequest(session, url)) return process.doResponse(session, url, files);
        return getAssets(url.substring(1));
    }

    private void parse(IHTTPSession session, Map<String, String> files) {
        try {
            String ct = session.getHeaders().get("content-type");
            if (ct != null) session.getHeaders().put("content-type", ct.replace("multipart/form-data", "multipart/form-data; charset=utf-8"));
            session.parseBody(files);
        } catch (Exception e) {
            android.util.Log.w("Nano", "parseBody failed for " + session.getUri() + ": " + e);
        }
    }

    private Response getAssets(String path) {
        try {
            if (path.isEmpty()) path = INDEX;
            InputStream is = Asset.open(path);
            return newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(path), is, -1);
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null, 0);
        }
    }
}
