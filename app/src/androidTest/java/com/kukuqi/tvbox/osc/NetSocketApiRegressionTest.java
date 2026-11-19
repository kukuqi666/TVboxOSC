package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.github.catvod.net.Net;
import com.github.catvod.net.NetSocket;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 验证 NetSocket 的取消语义与事件扁平化在真机上的行为。
 *
 * <p>用内置的最小 WebSocket 服务端，避免依赖外网。</p>
 */
public class NetSocketApiRegressionTest extends InstrumentationTestCase {

    private Net net;
    private final BlockingQueue<NetSocket.Event> events = new LinkedBlockingQueue<>();
    private ServerSocket server;
    private Thread acceptor;
    private final CountDownLatch ready = new CountDownLatch(1);
    private volatile boolean running = true;
    private volatile String lastCloseFrame;

    @Override protected void setUp() throws Exception {
        super.setUp();
        net = new Net();
        server = new ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"));
        ready.countDown();
        acceptor = new Thread(() -> {
            while (running) {
                try {
                    Socket client = server.accept();
                    new Thread(() -> handle(client)).start();
                } catch (Exception stop) {
                    return;
                }
            }
        });
        acceptor.start();
    }

    @Override protected void tearDown() throws Exception {
        net.close();
        running = false;
        if (server != null) server.close();
        super.tearDown();
    }

    private String url() {
        return "ws://127.0.0.1:" + server.getLocalPort() + "/ws";
    }

    /** 最小握手 + 回一帧文本；接住客户端关闭帧。 */
    private void handle(Socket client) {
        try (Socket socket = client) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            StringBuilder head = new StringBuilder();
            int b;
            while ((b = input.read()) != -1) {
                head.append((char) b);
                if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
            }
            String key = null;
            for (String line : head.toString().split("\r\n"))
                if (line.toLowerCase().startsWith("sec-websocket-key:")) key = line.substring(18).trim();
            if (key == null) return;
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")));
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes("UTF-8"));
            output.flush();
            // 主动回一帧文本，验证能读到
            byte[] payload = "hello".getBytes("UTF-8");
            output.write(0x81);
            output.write(payload.length);
            output.write(payload);
            output.flush();
            // 循环读客户端帧，直到关闭
            while (true) {
                int op = input.read();
                if (op == -1) return;
                int len = input.read();
                if (len == -1) return;
                boolean masked = (len & 0x80) != 0;
                int length = len & 0x7f;
                if (length == 126) { length = (input.read() << 8) | input.read(); }
                byte[] mask = new byte[4];
                if (masked) { for (int i = 0; i < 4; i++) mask[i] = (byte) input.read(); }
                byte[] data = new byte[length];
                for (int i = 0; i < length; i++) {
                    int v = input.read();
                    if (v == -1) return;
                    data[i] = (byte) (masked ? (v ^ mask[i % 4]) : v);
                }
                int opcode = op & 0x0f;
                if (opcode == 0x8) {
                    lastCloseFrame = length >= 2 ? String.valueOf(((data[0] & 0xff) << 8) | (data[1] & 0xff)) : "none";
                    output.write(0x88);
                    output.write(data.length);
                    output.write(data);
                    output.flush();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private NetSocket connect(String address) throws Exception {
        return net.connect(address, "{\"timeout\":3000}", (socket, event) -> events.add(event));
    }

    private NetSocket.Event await(String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            long remaining = deadline - System.nanoTime();
            assertTrue("等待事件超时：" + type, remaining > 0);
            NetSocket.Event event = events.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull("缺少事件：" + type, event);
            if (type.equals(event.type)) return event;
            assertFalse("等待时出现网络错误：" + event.error, "error".equals(event.type));
        }
    }

    /** 连接和文本消息分别通过回调交付。 */
    public void testOpenAndReceiveText() throws Exception {
        try (NetSocket socket = connect(url())) {
            assertEquals(101, await("open").code);
            NetSocket.Event event = await("message");
            assertEquals("hello", event.content);
            assertFalse(event.binary);
        }
    }

    /** 取消以 1001 关闭且幂等，之后拒绝发送。 */
    public void testCancelMarksClosedWithReason() throws Exception {
        try (NetSocket socket = connect(url())) {
            await("open");
            socket.cancel();
            assertEquals(1001, await("close").code);
            assertFalse(socket.send("after-cancel"));
            socket.cancel();
            assertNull("重复取消不应产生第二次关闭事件", events.poll(200, TimeUnit.MILLISECONDS));
        }
    }

    /** parts() 保留消息元数据和正文。 */
    public void testEventPartsShape() throws Exception {
        try (NetSocket socket = connect(url())) {
            NetSocket.Event event = await("message");
            Object[] parts = event.parts();
            assertEquals(2, parts.length);
            com.google.gson.JsonObject meta =
                    com.google.gson.JsonParser.parseString((String) parts[0]).getAsJsonObject();
            assertEquals("message", meta.get("type").getAsString());
            assertFalse(meta.get("binary").getAsBoolean());
            assertEquals(event.content, parts[1]);
        }
    }

    /** 正常关闭必须回传 1000，不能把取消状态当作通过。 */
    public void testClosedEventCarriesCode() throws Exception {
        NetSocket socket = connect(url());
        await("message");
        socket.close();
        assertEquals(1000, await("close").code);
        assertEquals("1000", lastCloseFrame);
        assertFalse(socket.send("after-close"));
    }

    /** 不可达地址经异步错误和关闭事件交付。 */
    public void testUnreachableReportsError() throws Exception {
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) { deadPort = probe.getLocalPort(); }
        try (NetSocket socket = connect("ws://127.0.0.1:" + deadPort + "/ws")) {
            NetSocket.Event error = events.poll(5, TimeUnit.SECONDS);
            assertNotNull(error);
            assertEquals("error", error.type);
            assertNotNull(error.error);
            assertEquals(1006, await("close").code);
            assertFalse(socket.send("unreachable"));
        }
    }
}
