package com.dudu.codex;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class RpcClientTest {
    private static class FakeProcess extends Process {
        final PipedInputStream clientRead = new PipedInputStream();
        final PipedOutputStream serverWrite;
        final PipedInputStream serverRead = new PipedInputStream();
        final PipedOutputStream clientWrite;
        final BufferedReader requests;
        FakeProcess() throws IOException {
            serverWrite = new PipedOutputStream(clientRead); clientWrite = new PipedOutputStream(serverRead);
            requests = new BufferedReader(new InputStreamReader(serverRead, StandardCharsets.UTF_8));
        }
        void emit(JSONObject value) throws IOException { serverWrite.write((value + "\n").getBytes(StandardCharsets.UTF_8)); serverWrite.flush(); }
        JSONObject request() throws IOException { return Json.parse(requests.readLine()); }
        public OutputStream getOutputStream() { return clientWrite; }
        public InputStream getInputStream() { return clientRead; }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() { return 0; }
        public int exitValue() { return 0; }
        public void destroy() { try { serverWrite.close(); clientWrite.close(); } catch (IOException ignored) { } }
    }
    private static class Listener implements RpcClient.Listener {
        final CountDownLatch event = new CountDownLatch(1), disconnected = new CountDownLatch(1);
        JSONObject message;
        public void event(JSONObject message) { this.message = message; event.countDown(); }
        public void disconnected(String reason) { disconnected.countDown(); }
    }
    @Test(timeout = 5000) public void matchesOutOfOrderRepliesAndPreservesUnicodeNotifications() throws Exception {
        FakeProcess process = new FakeProcess(); Listener listener = new Listener();
        try (RpcClient client = new RpcClient(process, Runnable::run, listener)) {
            AtomicReference<String> first = new AtomicReference<>(), second = new AtomicReference<>();
            CountDownLatch callbacks = new CountDownLatch(2);
            client.request("first", Json.obj(), (r, e) -> { first.set(r.optString("text")); callbacks.countDown(); });
            client.request("second", Json.obj(), (r, e) -> { second.set(r.optString("text")); callbacks.countDown(); });
            JSONObject a = process.request(), b = process.request();
            process.emit(Json.obj("id", b.opt("id"), "result", Json.obj("text", "二")));
            process.emit(Json.obj("method", "item/agentMessage/delta", "params", Json.obj("delta", "你好🌏")));
            process.emit(Json.obj("id", a.opt("id"), "result", Json.obj("text", "一")));
            assertTrue(callbacks.await(2, TimeUnit.SECONDS)); assertTrue(listener.event.await(2, TimeUnit.SECONDS));
            assertEquals("一", first.get()); assertEquals("二", second.get());
            assertEquals("你好🌏", Json.object(listener.message, "params").optString("delta"));
        }
    }
    @Test(timeout = 5000) public void routesServerRequestsAndReturnsTheirResult() throws Exception {
        FakeProcess process = new FakeProcess(); Listener listener = new Listener();
        try (RpcClient client = new RpcClient(process, Runnable::run, listener)) {
            process.emit(Json.obj("id", 7, "method", "item/tool/call", "params", Json.obj()));
            assertTrue(listener.event.await(2, TimeUnit.SECONDS));
            client.respond(listener.message.opt("id"), Json.obj("success", false));
            JSONObject response = process.request();
            assertEquals(7, response.optInt("id")); assertFalse(Json.object(response, "result").optBoolean("success"));
        }
    }
    @Test(timeout = 5000) public void resolvesPendingRequestOnProcessExit() throws Exception {
        FakeProcess process = new FakeProcess(); Listener listener = new Listener();
        try (RpcClient client = new RpcClient(process, Runnable::run, listener)) {
            AtomicReference<String> error = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
            client.request("account/read", Json.obj(), (r, e) -> { error.set(e); done.countDown(); });
            process.request(); process.serverWrite.close();
            assertTrue(done.await(2, TimeUnit.SECONDS)); assertNotNull(error.get());
            assertTrue(listener.disconnected.await(2, TimeUnit.SECONDS));
        }
    }
    @Test(timeout = 5000) public void timeoutCompletesOnceAndTransportRemainsUsable() throws Exception {
        FakeProcess process = new FakeProcess(); Listener listener = new Listener();
        try (RpcClient client = new RpcClient(process, Runnable::run, listener)) {
            CountDownLatch expired = new CountDownLatch(1), complete = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger(); AtomicReference<String> error = new AtomicReference<>();
            client.request("initialize", Json.obj(), 30, (r, e) -> { calls.incrementAndGet(); error.set(e); expired.countDown(); });
            JSONObject first = process.request(); assertTrue(expired.await(2, TimeUnit.SECONDS)); assertNotNull(error.get());
            process.emit(Json.obj("id", first.opt("id"), "result", Json.obj()));
            client.request("account/read", Json.obj(), (r, e) -> complete.countDown());
            JSONObject second = process.request(); process.emit(Json.obj("id", second.opt("id"), "result", Json.obj()));
            assertTrue(complete.await(2, TimeUnit.SECONDS)); assertEquals(1, calls.get());
        }
    }
    @Test(timeout = 5000) public void queuedEventsFromClosedConnectionAreDiscarded() throws Exception {
        FakeProcess process = new FakeProcess(); Listener listener = new Listener();
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        try (RpcClient client = new RpcClient(process, callbacks::add, listener)) {
            process.emit(Json.obj("method", "item/agentMessage/delta", "params", Json.obj("delta", "旧连接")));
            Runnable queued = callbacks.poll(2, TimeUnit.SECONDS); assertNotNull(queued);
            client.close(); queued.run();
            assertEquals(1, listener.event.getCount());
        }
    }
}
