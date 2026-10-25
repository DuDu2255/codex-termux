# Codex Android — 原生聊天 APK

`APK` 分支现已改为原生 Android 聊天应用。没有 Termux 界面、bootstrap、PRoot、Linux 根文件系统或首次启动安装包的步骤。

## 安装和使用

要求 Android 10 或以上、ARM64 手机。打开本仓库 Actions → Android APK → 最新成功任务，下载 **Codex-Android-arm64-v8a**，解压后安装其中的 APK。

1. 在“设置”中选择“使用 ChatGPT 登录”，复制验证码并在浏览器完成登录。账号需要支持 Codex，并允许设备代码登录。
2. 也可保存自己的 API Key；官方服务地址和模型可留空。自定义服务必须支持 Responses API，并填写 HTTPS 地址（通常以 `/v1` 结尾）和模型名称。
3. 点击“＋ 附件”使用系统文件选择器选择文件或图片，然后发送消息。每条消息最多 8 个附件，单个最大 50 MB。
4. “文件”菜单可导出上传的附件及模型保存的文件；“历史”可恢复对话。流式回复期间可点击“停止”。

支持文本/代码读取、搜索、文本文件生成、图片理解、PDF 按页阅读。可以导入其他文件类型，但压缩包、Office 文档等二进制格式暂不保证可以解析。文本工具单次读写最大 1 MB。模型能力取决于所选服务；这不是离线模型。

附件先复制到当前会话的应用私有工作区。发送时，图片和模型读取的文件内容会传给所选服务。模型写入文件或请求执行其他操作时，会显示确认对话框。聊天记录和草稿保存在本机；API Key 使用 Android Keystore 加密保存，登录令牌由引擎保存在应用私有目录。应用关闭系统备份。

这是新应用 `com.dudu.codex`，可与旧 Codex Terminal 共存。旧版聊天/终端数据不会自动迁移。GitHub Actions 生成的是测试签名 APK，不同构建的签名可能不同；正式长期使用需设置稳定的私有签名。卸载本应用会删除本地会话和未导出的文件。

## 架构和范围

- Java 原生 Activity / Service / Android 系统文件选择器，无 WebView。
- APK 内置 Android ARM64 Codex 引擎，通过 JSON-lines `app-server` 协议通信。
- 引擎由 Android 安装器从 APK 的 `lib/arm64-v8a` 解压到 `nativeLibraryDir`，直接运行。无需安装 Termux，也不读取 Termux 的配置或目录。
- Android 原生文件工具负责文本、图片、PDF；只允许访问当前会话工作区。
- 不提供完整 Linux 环境，也不附带 Git、Python、Node、npm 或 pkg。
- 构建来源、校验与引擎文件名适配见 [NATIVE-ENGINE.md](NATIVE-ENGINE.md)。

## 构建

需要 Python 3.11+、JDK 17、Android SDK 35 / Build Tools 35.0.0。

```sh
cd android
python3 prepare_native.py
./gradlew :native-app:testDebugUnitTest :native-app:assembleDebug
python3 inspect_apk.py native-app/build/outputs/apk/debug/native-app-debug.apk
```

输出：`native-app/build/outputs/apk/debug/native-app-debug.apk`。

`prepare_native.py` 下载并校验锁定的引擎包。应用首次启动不下载运行环境；登录和聊天仍需联网。GitHub Actions 执行单元测试、构建、APK 内容检查、签名校验，生成 APK 和 SHA256SUMS。

实际 Android ARM64 主机可运行 `python3 smoke_native.py`，在清除 Termux 环境的子进程内验证协议和本地模拟服务，不使用真实账号或付费 API。桌面 CI 的编译通过不等于已通过手机安装、真实账号登录或文件选择器实测。
