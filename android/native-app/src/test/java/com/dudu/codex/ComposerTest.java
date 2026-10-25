package com.dudu.codex;

import android.app.Application;
import android.content.ComponentName;
import android.os.Looper;
import android.text.Editable;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the real views/InputConnection, including IME composing spans. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerTest {
    private ChatService service;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private EditText editor;
    private InputConnection ime;

    @Before public void setUp() {
        // Attach a real Service context but skip onCreate/native process startup.
        service = Robolectric.buildService(ChatService.class).get();
        service.conversation = Conversation.create(); service.ready = true;
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).setComponentNameAndServiceForBindService(
            new ComponentName(app, ChatService.class), service.new LocalBinder());
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get(); flushUi();
        editor = ReflectionHelpers.getField(activity, "compose");
        assertTrue(editor.requestFocus());
        ime = editor.onCreateInputConnection(new EditorInfo());
        assertNotNull(ime);
    }
    private void flushUi() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40)); }
    @After public void tearDown() {
        controller.pause().stop().destroy(); service.onDestroy();
    }
    @Test public void composingChineseSurvivesStreamingAndKeepsAcceptingKeystrokes() {
        ime.setComposingText("ni", 1);
        Editable original = editor.getText();
        for (int i = 0; i < 4; i++) {
            service.conversation.delta("reply", "收到"); activity.changed(); flushUi();
            assertTrue("Chat scrolling must not steal keyboard focus", editor.hasFocus());
            assertSame("Rendering must preserve the IME Editable", original, editor.getText());
            assertEquals(0, BaseInputConnection.getComposingSpanStart(editor.getText()));
            assertEquals(2, BaseInputConnection.getComposingSpanEnd(editor.getText()));
        }
        ime.setComposingText("你好", 1); ime.commitText("你好", 1); ime.commitText("世界", 1); flushUi();
        assertEquals("你好世界", editor.getText().toString());
        assertEquals("你好世界", service.conversation.data.optString("draftText"));
        assertEquals(1, service.conversation.messages().length()); // Only the simulated assistant reply.
    }
    @Test public void voicePartialResultsAndEditorActionsNeverSubmitTheDraft() {
        for (String partial : new String[]{"我", "我要", "我要上传文件"}) {
            ime.setComposingText(partial, 1);
            service.status = "已就绪"; activity.changed(); flushUi();
            assertTrue(editor.hasFocus()); assertEquals(partial, editor.getText().toString());
        }
        ime.commitText("我要上传文件", 1);
        ime.performEditorAction(EditorInfo.IME_ACTION_SEND); flushUi();
        assertEquals(0, service.conversation.messages().length());
        assertEquals("我要上传文件", editor.getText().toString());
        ime.commitText("，还没说完", 1); flushUi();
        assertEquals("我要上传文件，还没说完", editor.getText().toString());
    }
    @Test public void backgroundStatusRefreshKeepsCursorAndSelectedText() {
        ime.commitText("前面的文字后面的文字", 1); editor.setSelection(3, 5);
        service.status = "正在连接…"; activity.changed(); flushUi();
        assertEquals(3, editor.getSelectionStart()); assertEquals(5, editor.getSelectionEnd());
        assertTrue(editor.hasFocus()); assertEquals(0, service.conversation.messages().length());
    }
    @Test public void typingSavesDraftWithoutNotifyingTheWholeChat() {
        AtomicInteger renders = new AtomicInteger(); service.observe(renders::incrementAndGet);
        int before = renders.get();
        for (String partial : new String[]{"你", "你好", "你好，世界"}) ime.setComposingText(partial, 1);
        assertEquals(before, renders.get());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(850));
        assertEquals("你好，世界", service.getSharedPreferences("ui", 0)
            .getString("draft." + service.conversation.id(), ""));
        assertEquals(0, service.conversation.messages().length());
    }
    @Test public void unchangedSettingsReuseConnection() throws Exception {
        service.settings("", "", null, () -> { });
        service.worker.submit(() -> { }).get(2, TimeUnit.SECONDS); flushUi();
        assertTrue(service.ready); assertFalse(service.connecting);
        assertEquals(0, (int) ReflectionHelpers.getField(service, "generation"));
    }
    @Test public void disconnectDoesNotClearCompositionAndRetriesAreBounded() {
        ime.setComposingText("还没写完", 1);
        for (int i = 0; i < 4; i++) ReflectionHelpers.callInstanceMethod(service, "connectionFailed",
            ReflectionHelpers.ClassParameter.from(String.class, "test disconnect"));
        flushUi();
        assertEquals("还没写完", editor.getText().toString()); assertTrue(editor.hasFocus());
        assertEquals(0, BaseInputConnection.getComposingSpanStart(editor.getText()));
        assertEquals(3, (int) ReflectionHelpers.getField(service, "reconnectAttempts"));
        Runnable reconnect = ReflectionHelpers.getField(service, "reconnect");
        assertFalse(service.main.hasCallbacks(reconnect));
        ime.commitText("还没写完，继续输入", 1); flushUi();
        assertEquals("还没写完，继续输入", editor.getText().toString());
    }
}
