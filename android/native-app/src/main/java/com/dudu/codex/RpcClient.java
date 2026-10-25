package com.dudu.codex;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** JSON-lines app-server transport. All application callbacks run on the UI executor. */
final class RpcClient implements AutoCloseable {
    interface Callback { void done(JSONObject result, String error); }
    interface Listener {
        void event(JSONObject message);
        void disconnected(String reason);
    }
    private final Process process;
    private final Executor callbacks;
    private final Listener listener;
    private final BufferedWriter writer;
    private final Map<String, Callback> pending = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final ExecutorService writes = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    private final StringBuilder diagnostic = new StringBuilder();

    RpcClient(Process process, Executor callbacks, Listener listener) {
        this.process = process; this.callbacks = callbacks; this.listener = listener;
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        new Thread(this::read, "codex-rpc-read").start();
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) synchronized (diagnostic) {
                    diagnostic.append(line).append('\n');
                    if (diagnostic.length() > 4000) diagnostic.delete(0, diagnostic.length() - 4000);
                }
            } catch (IOException ignored) { }
        }, "codex-diagnostics").start();
    }

    void request(String method, JSONObject params, Callback callback) {
        if (closed) { callbacks.execute(() -> callback.done(null, "连接已关闭，请重新连接")); return; }
        String id = Long.toString(ids.incrementAndGet());
        pending.put(id, callback);
        send(Json.obj("id", id, "method", method, "params", params));
        timer.schedule(() -> {
            Callback expired = pending.remove(id);
            if (expired != null) callbacks.execute(() -> expired.done(null, "请求超时：" + method));
        }, 90, TimeUnit.SECONDS);
    }
    void notify(String method) { send(Json.obj("method", method)); }
    void respond(Object id, JSONObject result) { send(Json.obj("id", id, "result", result)); }
    void reject(Object id, String reason) {
        send(Json.obj("id", id, "error", Json.obj("code", -32601, "message", reason)));
    }
    private void send(JSONObject message) {
        if (closed) return;
        try {
            writes.execute(() -> {
                try { writer.write(message.toString()); writer.newLine(); writer.flush(); }
                catch (IOException e) { fail("无法写入 Codex 进程：" + e.getMessage()); }
            });
        } catch (RejectedExecutionException ignored) { }
    }
    private void read() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                JSONObject message = Json.parse(line);
                if (message.has("id") && !message.has("method")) {
                    Callback callback = pending.remove(message.optString("id"));
                    if (callback != null) {
                        JSONObject error = message.optJSONObject("error");
                        callbacks.execute(() -> callback.done(message.optJSONObject("result"),
                            error == null ? null : error.optString("message", error.toString())));
                    }
                } else callbacks.execute(() -> listener.event(message));
            }
            if (!closed) {
                String detail;
                synchronized (diagnostic) { detail = diagnostic.toString(); }
                fail("Codex 进程已退出。" + (detail.isEmpty() ? "" : "\n" + detail));
            }
        } catch (Exception e) { if (!closed) fail("Codex 连接中断：" + e.getMessage()); }
    }
    private synchronized void fail(String reason) {
        if (closed) return;
        closed = true;
        for (Callback callback : pending.values()) callbacks.execute(() -> callback.done(null, reason));
        pending.clear(); process.destroy(); writes.shutdownNow(); timer.shutdownNow();
        callbacks.execute(() -> listener.disconnected(reason));
    }
    public synchronized void close() {
        if (closed) return;
        closed = true; pending.clear(); process.destroy(); writes.shutdownNow(); timer.shutdownNow();
    }
}
