package com.dudu.codex;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationTest {
    @Test public void streamsByItemAndFinalMessageDoesNotDuplicate() {
        Conversation c = Conversation.create();
        c.append("user", "分析文档", new JSONArray().put(Json.obj("name", "你好.txt")));
        c.delta("first", "你好"); c.delta("second", "另一条"); c.delta("first", "世界");
        c.complete("first", "你好世界！");
        assertEquals(3, c.messages().length());
        assertEquals("你好世界！", c.messages().optJSONObject(1).optString("text"));
        assertEquals("另一条", c.messages().optJSONObject(2).optString("text"));
    }
    @Test public void roundTripPreservesAttachmentsDraftAndThread() {
        Conversation c = Conversation.create();
        Json.put(c.data, "threadId", "thread-123");
        Json.put(c.data, "draftText", "未发送的内容");
        c.append("user", "", new JSONArray().put(Json.obj("name", "中文.pdf", "path", "attachments/a.pdf")));
        Conversation restored = new Conversation(Json.parse(c.data.toString()));
        assertEquals(c.id(), restored.id()); assertEquals("thread-123", restored.threadId());
        assertEquals("文件对话", restored.data.optString("title"));
        assertEquals("未发送的内容", restored.data.optString("draftText"));
        assertEquals("中文.pdf", Json.array(restored.messages().optJSONObject(0), "attachments").optJSONObject(0).optString("name"));
    }
}
