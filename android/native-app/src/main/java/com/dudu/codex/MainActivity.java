package com.dudu.codex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Native Android views, document picker and lifecycle; no WebView or terminal. */
public final class MainActivity extends Activity implements ChatService.Observer {
    private static final int PICK = 41, EXPORT = 42;
    private static final int INK = 0xff202b32, GREEN = 0xff167d68, MUTED = 0xff65727b;
    private ChatService service;
    private boolean bound, resumed, rendering, scheduled;
    private LinearLayout root, messages, attachments;
    private ScrollView scroll;
    private TextView subtitle, title;
    private EditText compose;
    private Button send, attach, history, files, fresh;
    private String displayedChat = "", exportPath = "", exportWorkspace = "", pickerChat = "";
    private final Map<String, TextView> bubbles = new LinkedHashMap<>();
    private AlertDialog approvalDialog, loginDialog;
    private JSONObject shownQuestion;
    private String shownCode = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable update = () -> { scheduled = false; render(); };
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ChatService.LocalBinder) binder).service(); service.observe(MainActivity.this);
        }
        public void onServiceDisconnected(ComponentName name) {
            service = null; subtitle.setText("连接中断，请重新打开应用"); send.setEnabled(false);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            exportPath = saved.getString("exportPath", ""); exportWorkspace = saved.getString("exportWorkspace", "");
            pickerChat = saved.getString("pickerChat", "");
        }
        buildViews();
        Intent intent = new Intent(this, ChatService.class);
        startService(intent); bound = bindService(intent, connection, BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= 33 && !getPreferences(0).getBoolean("notificationsAsked", false)) {
            getPreferences(0).edit().putBoolean("notificationsAsked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
    }
    @Override public void onResume() { super.onResume(); resumed = true; changed(); }
    @Override public void onPause() { resumed = false; super.onPause(); }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putString("exportPath", exportPath); out.putString("exportWorkspace", exportWorkspace);
        out.putString("pickerChat", pickerChat); super.onSaveInstanceState(out);
    }
    @Override public void onDestroy() {
        main.removeCallbacks(update);
        if (service != null) service.removeObserver(this);
        if (bound) unbindService(connection);
        if (approvalDialog != null) approvalDialog.dismiss();
        if (loginDialog != null) loginDialog.dismiss();
        super.onDestroy();
    }
    public void changed() { if (!scheduled) { scheduled = true; main.postDelayed(update, 32); } }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextColor(GREEN);
        button.setTextSize(14); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0); button.setOnClickListener(v -> action.run()); return button;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private void buildViews() {
        root = column(); root.setBackgroundColor(0xfff7f8fa); setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom)); return insets;
            });
        }
        LinearLayout heading = column(); heading.setPadding(dp(20), dp(12), dp(16), dp(4));
        LinearLayout top = row();
        title = label("Codex", 24, INK); title.setTypeface(null, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(button("设置", this::settings)); heading.addView(top);
        subtitle = label("正在启动…", 12, MUTED); heading.addView(subtitle); root.addView(heading);
        LinearLayout toolbar = row(); toolbar.setPadding(dp(10), 0, dp(10), 0);
        fresh = button("＋ 新对话", () -> switchChat(null)); history = button("历史", this::history);
        files = button("文件", this::files);
        toolbar.addView(fresh); toolbar.addView(history); toolbar.addView(files); root.addView(toolbar);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        messages = column(); messages.setPadding(dp(16), dp(8), dp(16), dp(16)); scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout composer = column(); composer.setPadding(dp(12), dp(8), dp(12), dp(8)); composer.setBackgroundColor(Color.WHITE);
        attachments = column(); composer.addView(attachments);
        compose = new EditText(this); compose.setTextSize(16); compose.setTextColor(INK);
        compose.setHint("发送消息，或添加文件…"); compose.setMinLines(1); compose.setMaxLines(6);
        compose.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        compose.setBackground(background(0xfff1f4f5, 16)); compose.setPadding(dp(14), dp(12), dp(14), dp(12));
        compose.setSaveEnabled(false);
        compose.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering && service != null) service.draft(s.toString());
            }
            public void afterTextChanged(Editable e) { }
        });
        composer.addView(compose);
        LinearLayout actions = row(); attach = button("＋ 附件", this::pickFiles);
        actions.addView(attach);
        TextView note = label("文件内容会随对话发给所选服务", 11, MUTED);
        actions.addView(note, new LinearLayout.LayoutParams(0, -2, 1));
        send = button("发送", () -> {
            if (service == null) return;
            if (service.busy) service.stop();
            else service.send(compose.getText().toString(), service.draftAttachments());
        });
        send.setEnabled(false); actions.addView(send); composer.addView(actions); root.addView(composer);
    }
    private void render() {
        if (service == null || isDestroyed()) return;
        rendering = true;
        try {
            Conversation chat = service.conversation;
            boolean newChat = !displayedChat.equals(chat.id());
            boolean nearBottom = newChat || scroll.getChildAt(0).getHeight() - scroll.getHeight() - scroll.getScrollY() < dp(140);
            if (newChat) { displayedChat = chat.id(); messages.removeAllViews(); bubbles.clear(); }
            title.setText("Codex"); subtitle.setText(service.status);
            String draft = chat.data.optString("draftText");
            if (!compose.getText().toString().equals(draft)) { compose.setText(draft); compose.setSelection(draft.length()); }
            JSONArray items = chat.messages();
            if (items.length() == 0 && messages.getChildCount() == 0) {
                LinearLayout welcome = column(); welcome.setPadding(dp(16), dp(48), dp(16), dp(36));
                TextView heading = label("有什么想一起完成的？", 23, INK); heading.setTypeface(null, Typeface.BOLD);
                welcome.addView(heading); TextView detail = label("聊一个想法、分析图片，或者添加文档和代码。\n\n先在设置里登录，然后开始对话。", 16, MUTED);
                detail.setPadding(0, dp(18), 0, 0); detail.setLineSpacing(dp(5), 1); welcome.addView(detail); messages.addView(welcome);
            }
            if (items.length() > 0 && bubbles.isEmpty()) messages.removeAllViews();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); String id = item.optString("id");
                TextView bubble = bubbles.get(id); String role = item.optString("role");
                if (bubble == null) {
                    LinearLayout container = column(); container.setPadding(dp(14), dp(12), dp(14), dp(12));
                    container.setBackground(background(role.equals("user") ? 0xffe3f2ed : role.equals("error") ? 0xffffeeeb : Color.WHITE, 16));
                    TextView who = label(role.equals("user") ? "你" : role.equals("error") ? "提示" : "Codex", 12, MUTED);
                    who.setPadding(0, 0, 0, dp(6)); container.addView(who);
                    bubble = label("", 16, INK); bubble.setTextIsSelectable(true); bubble.setLineSpacing(dp(4), 1); container.addView(bubble);
                    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
                    layout.bottomMargin = dp(12); layout.leftMargin = role.equals("user") ? dp(24) : 0;
                    layout.rightMargin = role.equals("user") ? 0 : dp(12); messages.addView(container, layout); bubbles.put(id, bubble);
                }
                StringBuilder text = new StringBuilder(item.optString("text"));
                JSONArray attached = Json.array(item, "attachments");
                for (int j = 0; j < attached.length(); j++) text.append("\n📎 ").append(attached.optJSONObject(j).optString("name"));
                String value = text.length() == 0 ? "…" : text.toString();
                if (!bubble.getText().toString().equals(value)) bubble.setText(value);
            }
            attachments.removeAllViews();
            JSONArray draftFiles = service.draftAttachments();
            for (int i = 0; i < draftFiles.length(); i++) {
                int index = i; JSONObject file = draftFiles.optJSONObject(i);
                Button chip = button("📎 " + file.optString("name") + "   ×", () -> service.removeAttachment(index));
                chip.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); chip.setEnabled(!service.busy && !service.importing);
                attachments.addView(chip, new LinearLayout.LayoutParams(-1, dp(40)));
            }
            boolean idle = !service.busy && !service.importing;
            fresh.setEnabled(idle); history.setEnabled(idle); attach.setEnabled(idle && draftFiles.length() < 8);
            send.setText(service.busy ? "停止" : "发送");
            send.setEnabled(service.busy || (service.ready && !service.importing && !service.loggingIn));
            if (nearBottom) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            if (resumed) { showApproval(); showLogin(); }
        } finally { rendering = false; }
    }
    private void switchChat(String id) { if (service != null) { service.select(id); changed(); } }
    private void history() {
        if (service == null) return;
        service.history(entries -> {
            if (isDestroyed()) return;
            if (entries.isEmpty()) { toast("还没有历史对话"); return; }
            String[] titles = new String[entries.size()];
            for (int i = 0; i < titles.length; i++) titles[i] = entries.get(i).optString("title", "对话");
            new AlertDialog.Builder(this).setTitle("历史对话").setItems(titles, (d, i) -> switchChat(entries.get(i).optString("id")))
                .setNegativeButton("关闭", null).show();
        });
    }
    private void pickFiles() {
        if (service == null) return;
        pickerChat = service.conversation.id();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK); } catch (ActivityNotFoundException e) { toast("设备没有可用的文件选择器"); }
    }
    private void files() {
        if (service == null) return;
        File workspace = service.workspace();
        service.worker.execute(() -> {
            try {
                List<File> entries = SafeFiles.list(workspace);
                main.post(() -> {
                    if (isDestroyed()) return;
                    if (entries.isEmpty()) { toast("当前对话还没有文件，点“附件”添加"); return; }
                    String[] names = new String[entries.size()];
                    for (int i = 0; i < names.length; i++) names[i] = relative(workspace, entries.get(i));
                    new AlertDialog.Builder(this).setTitle("会话文件 · 点击导出").setItems(names, (d, i) -> {
                        exportPath = entries.get(i).getAbsolutePath(); exportWorkspace = workspace.getAbsolutePath();
                        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream")
                            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, entries.get(i).getName());
                        try { startActivityForResult(save, EXPORT); } catch (ActivityNotFoundException e) { toast("设备没有可用的文件选择器"); }
                    }).setNegativeButton("关闭", null).show();
                });
            } catch (Exception e) { main.post(() -> toast("读取文件失败：" + e.getMessage())); }
        });
    }
    private static String relative(File root, File file) {
        try { return root.getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString(); }
        catch (IOException e) { return file.getName(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == PICK) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            for (Uri uri : uris) try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { }
            importWhenBound(uris, pickerChat, 0);
        } else if (request == EXPORT && data.getData() != null && !exportPath.isEmpty()) {
            Uri destination = data.getData(); File workspace = new File(exportWorkspace); String path = exportPath;
            new Thread(() -> {
                try (InputStream in = new FileInputStream(SafeFiles.resolve(workspace, path)); OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                    if (out == null) throw new IOException("无法打开保存位置");
                    SafeFiles.copy(in, out, Long.MAX_VALUE); main.post(() -> toast("文件已导出"));
                } catch (Exception e) { main.post(() -> toast("导出失败：" + e.getMessage())); }
            }, "codex-export").start();
        }
    }
    private void importWhenBound(List<Uri> uris, String chat, int attempt) {
        if (isDestroyed()) return;
        if (service == null && attempt < 50) { main.postDelayed(() -> importWhenBound(uris, chat, attempt + 1), 100); return; }
        if (service == null || !service.conversation.id().equals(chat)) { toast("对话已切换，请重新选择附件"); return; }
        service.importFiles(uris, added -> { });
    }
    private EditText field(LinearLayout parent, String hint, String text, boolean secret) {
        TextView caption = label(hint, 13, MUTED); caption.setPadding(0, dp(12), 0, dp(4)); parent.addView(caption);
        EditText edit = new EditText(this); edit.setTextSize(15); edit.setSingleLine(true); edit.setText(text);
        if (secret) {
            edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); edit.setSaveEnabled(false);
        }
        parent.addView(edit); return edit;
    }
    private ScrollView dialogContent(LinearLayout layout) {
        layout.setPadding(dp(22), dp(8), dp(22), dp(16)); ScrollView wrapper = new ScrollView(this); wrapper.addView(layout); return wrapper;
    }
    private void settings() {
        if (service == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
        LinearLayout body = column();
        body.addView(label("使用 ChatGPT 账号登录，或设置 API Key。", 14, INK));
        EditText endpoint = field(body, "服务地址（留空使用 OpenAI）", prefs.getString("endpoint", ""), false);
        endpoint.setHint("https://example.com/v1");
        EditText model = field(body, "模型名称（官方服务可留空）", prefs.getString("model", ""), false);
        EditText key = field(body, "API Key（留空保留已保存的密钥）", "", true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("连接设置").setView(dialogContent(body))
            .setPositiveButton("保存", null).setNegativeButton("关闭", null).create();
        Button login = button("使用 ChatGPT 登录", () -> {
            if (service.busy || service.importing) { toast("请先结束当前操作"); return; }
            service.settings("", model.getText().toString().trim(), "", () -> { dialog.dismiss(); service.loginAfterConnect(); });
        });
        body.addView(login);
        body.addView(button("退出登录并清除密钥", () -> { service.logout(); dialog.dismiss(); }));
        body.addView(button("重新连接", () -> { service.connect(); dialog.dismiss(); }));
        TextView version = label("Codex Android 0.2.1 · 原生聊天\n附件：每条消息最多 8 个，单个不超过 50 MB", 12, MUTED);
        version.setPadding(0, dp(12), 0, 0); body.addView(version);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String secret = key.getText().toString().trim();
            service.settings(endpoint.getText().toString().trim(), model.getText().toString().trim(), secret.isEmpty() ? null : secret, dialog::dismiss);
        }));
        dialog.show();
    }
    private void showLogin() {
        if (!service.loggingIn || service.loginCode.isEmpty()) {
            if (loginDialog != null) loginDialog.dismiss(); loginDialog = null; shownCode = ""; return;
        }
        if (shownCode.equals(service.loginCode)) return;
        shownCode = service.loginCode;
        if (loginDialog != null) loginDialog.dismiss();
        LinearLayout body = column();
        TextView hint = label("在浏览器中登录并输入以下验证码。", 16, INK); body.addView(hint);
        TextView code = label(service.loginCode, 28, GREEN); code.setTextIsSelectable(true); code.setPadding(0, dp(20), 0, dp(20)); body.addView(code);
        body.addView(button("复制验证码", () -> {
            getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Codex 登录码", service.loginCode)); toast("已复制");
        }));
        loginDialog = new AlertDialog.Builder(this).setTitle("登录 ChatGPT").setView(dialogContent(body))
            .setPositiveButton("打开浏览器", null).setNegativeButton("取消登录", (d, w) -> service.cancelLogin()).create();
        loginDialog.setOnCancelListener(d -> service.cancelLogin());
        loginDialog.setOnShowListener(d -> loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Uri uri = Uri.parse(service.loginUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) { toast("登录地址无效"); return; }
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { toast("请安装浏览器后重试"); }
        }));
        loginDialog.show();
    }
    private void showApproval() {
        JSONObject request = service.questions.peek();
        if (shownQuestion == request) return;
        if (approvalDialog != null) approvalDialog.dismiss(); approvalDialog = null; shownQuestion = request;
        if (request == null) return;
        JSONObject params = Json.object(request, "params"); String method = request.optString("method");
        LinearLayout body = column();
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setView(dialogContent(body));
        if (method.equals("item/tool/requestUserInput")) {
            JSONArray questions = Json.array(params, "questions"); Map<String, EditText> answers = new LinkedHashMap<>();
            for (int i = 0; i < questions.length(); i++) {
                JSONObject q = questions.optJSONObject(i);
                EditText answer = field(body, q.optString("question"), "", q.optBoolean("isSecret"));
                answer.setSingleLine(false); answers.put(q.optString("id"), answer);
                JSONArray options = Json.array(q, "options");
                for (int j = 0; j < options.length(); j++) {
                    JSONObject option = options.optJSONObject(j); String optionLabel = option.optString("label");
                    body.addView(button(optionLabel, () -> answer.setText(optionLabel)));
                    String description = option.optString("description"); if (!description.isEmpty()) body.addView(label(description, 12, MUTED));
                }
            }
            builder.setTitle("需要你的回答").setPositiveButton("提交", (d, w) -> {
                JSONObject response = Json.obj();
                for (Map.Entry<String, EditText> entry : answers.entrySet())
                    Json.put(response, entry.getKey(), Json.obj("answers", new JSONArray().put(entry.getValue().getText().toString())));
                service.answer(request, Json.obj("answers", response));
            }).setNegativeButton("取消", (d, w) -> decline(request));
        } else {
            boolean write = method.equals("item/tool/call");
            String explanation;
            if (write) {
                JSONObject args = Json.object(params, "arguments");
                explanation = "保存到：" + args.optString("path") + "\n\n" + args.optString("content");
            } else {
                try { explanation = params.toString(2); } catch (JSONException e) { explanation = params.toString(); }
            }
            TextView detail = label(explanation, 14, INK); detail.setTextIsSelectable(true); body.addView(detail);
            builder.setTitle(write ? "允许保存这个文件？" : "允许这次操作？")
                .setPositiveButton("允许一次", (d, w) -> service.answer(request, Json.obj("decision", "accept")))
                .setNegativeButton("拒绝", (d, w) -> decline(request));
        }
        approvalDialog = builder.create(); approvalDialog.setOnCancelListener(d -> decline(request)); approvalDialog.show();
    }
    private void decline(JSONObject request) {
        if (service != null) service.answer(request, request.optString("method").equals("item/tool/requestUserInput")
            ? Json.obj("answers", Json.obj()) : Json.obj("decision", "decline"));
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
