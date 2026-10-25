package com.dudu.codex;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Conversation reducer shared by the UI, persistence and protocol tests. */
final class Conversation {
    final JSONObject data;
    Conversation(JSONObject data) { this.data = data; }
    static Conversation create() {
        return new Conversation(Json.obj("id", UUID.randomUUID().toString(), "title", "新对话",
            "threadId", "", "messages", new JSONArray(), "updatedAt", System.currentTimeMillis()));
    }
    String id() { return data.optString("id"); }
    String threadId() { return data.optString("threadId"); }
    JSONArray messages() { return Json.array(data, "messages"); }
    void append(String role, String text, JSONArray attachments) {
        messages().put(Json.obj("id", UUID.randomUUID().toString(), "role", role, "text", text,
            "attachments", attachments == null ? new JSONArray() : attachments));
        Json.put(data, "updatedAt", System.currentTimeMillis());
        if (role.equals("user") && data.optString("title").equals("新对话")) {
            String title = text.trim().isEmpty() ? "文件对话" : text.replace('\n', ' ');
            Json.put(data, "title", title.substring(0, Math.min(title.length(), 28)));
        }
    }
    JSONObject assistant(String itemId) {
        JSONArray messages = messages();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (itemId.equals(item.optString("id"))) return item;
        }
        JSONObject result = Json.obj("id", itemId, "role", "assistant", "text", "", "attachments", new JSONArray());
        messages.put(result);
        return result;
    }
    void delta(String itemId, String text) {
        JSONObject message = assistant(itemId);
        Json.put(message, "text", message.optString("text") + text);
    }
    void complete(String itemId, String text) { Json.put(assistant(itemId), "text", text); }
}
