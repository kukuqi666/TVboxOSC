package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.bean.Header;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.Inflater;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.InflaterSource;
import okio.Okio;

public class ResponseInterceptor implements Interceptor {

    private final List<Header> headers;
    private final ConcurrentHashMap<String, String> redirectMap;

    public ResponseInterceptor() {
        headers = new CopyOnWriteArrayList<>();
        redirectMap = new ConcurrentHashMap<>();
    }

    public void addAll(List<Header> items) {
        headers.addAll(items);
    }

    public void clear() {
        headers.clear();
        redirectMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = check(chain.request());
        Response response = chain.proceed(request);
        String encoding = response.header(HttpHeaders.CONTENT_ENCODING);
        if ("deflate".equalsIgnoreCase(encoding)) return deflate(response);
        if (response.code() == 406 && redirectMap.containsKey(request.url().toString())) return redirect(request, response);
        if (response.code() == 302 && response.header(HttpHeaders.LOCATION) != null) redirectMap.put(response.header(HttpHeaders.LOCATION), request.url().toString());
        return response;
    }

    private Request check(Request request) {
        String host = request.url().host();
        Request.Builder builder = request.newBuilder();
        for (Header item : headers) if (Util.containOrMatch(host, item.getHost())) Json.toMap(item.getHeader()).forEach(builder::header);
        return builder.build();
    }

    private Response redirect(Request request, Response response) {
        response.close();
        return new Response.Builder().request(request).protocol(response.protocol()).code(302).message("Found").header(HttpHeaders.LOCATION, redirectMap.get(request.url().toString())).build();
    }

    private Response deflate(Response response) throws IOException {
        try {
            ResponseBody body = response.body();
            BufferedSource source = body.source();
            BufferedSource decoded = Okio.buffer(new InflaterSource(source, new Inflater(!isZlib(source))));
            return response.newBuilder().removeHeader(HttpHeaders.CONTENT_ENCODING).removeHeader(HttpHeaders.CONTENT_LENGTH).body(getBody(body, decoded)).build();
        } catch (IOException | RuntimeException | Error error) {
            closeAfterFailure(response, error);
            throw error;
        }
    }

    private void closeAfterFailure(Response response, Throwable failure) {
        try {
            response.close();
        } catch (RuntimeException | Error error) {
            if (failure != error) failure.addSuppressed(error);
        }
    }

    private boolean isZlib(BufferedSource source) throws IOException {
        if (!source.request(2)) return false;
        int cmf = source.getBuffer().getByte(0) & 0xff;
        int flg = source.getBuffer().getByte(1) & 0xff;
        return (cmf & 0x0f) == 8 && (cmf >> 4) <= 7 && ((cmf << 8) | flg) % 31 == 0;
    }

    private ResponseBody getBody(ResponseBody body, BufferedSource source) {
        return new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @NonNull
            @Override
            public BufferedSource source() {
                return source;
            }
        };
    }
}
