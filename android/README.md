# Codex Terminal APK

独立的 Android 终端应用，基于 Termux 0.118.3，内置 Codex 0.163.0-termux.2。
包名 `com.dudu.codex`，与原版 Termux 共存；不需要另装 Termux，也不需要 root。
支持 **Android 10 及以上、ARM64**。这是首个测试版本。

## 下载

打开本仓库的 **Actions → Android APK**，选择 `APK` 分支上成功的构建，
下载 `Codex-Terminal-arm64-v8a` 构件，解压后安装 APK。
构件下载需要登录 GitHub。不要安装名为 terminal-test-results 的测试报告。

## 使用

首次打开会离线解压环境并安装内置 Git、ripgrep 及其依赖，请留出约 700 MB 空间。
完成后自动进入 Codex，按照终端提示登录。访问模型服务仍需网络和可用账号。
退出 Codex 后回到 Bash，可运行 `codex` 再次进入，或照常使用 `pkg`、`git`。

保留 Termux 的会话抽屉、长按复制粘贴、缩放、软键盘、Ctrl/Alt/Tab 和方向键。
新终端会话自动进入 Codex；终端的 Ctrl-C、交互式命令及窗口大小变化由原版 PTY 处理。

- 关闭自动进入：`touch ~/.codex-terminal-no-autostart`
- 恢复自动进入：`rm ~/.codex-terminal-no-autostart`
- 共享存储授权：`termux-setup-storage`
- 重载键盘和颜色设置：`termux-reload-settings`
- 无法启动时，长按桌面图标使用 Failsafe 会话排查。

独立应用拥有独立的文件、登录和配置，不会读取现有 Termux 的 `.ssh` 或 `.codex`。
Android 文件选择器中会显示本应用的文件入口，方便手动导入项目。

## 兼容范围

界面使用完整 Termux 源码。运行环境通过 PRoot 把本应用私有目录映射到
Termux 软件要求的路径，避免改包名后 bash、apt 或动态库失效。
因此终端中看到的 `/data/data/com.termux/files` 属于此应用自己的环境。
PRoot 不需要 root，但增加命令执行开销，并可能受到部分手机系统限制。

`pkg` 使用 Termux 官方软件源，联网安装更多工具。原版 Termux:API、Widget、Boot
等插件与不同包名、签名的应用不能直接通用。嵌套 PRoot、后台常驻、浏览器回调登录
和所有软件包尚不能保证在每个 Android 系统上等效；设备码登录可作为浏览器回调的替代方式。
语音功能受所用 Codex Termux 版本本身的限制。

目前 Actions 产物为测试签名，每次全新构建的签名可能变化。正式长期分发前应配置
固定的私有发布签名；不要把签名密钥提交到仓库。

## 构建

Linux x86_64，JDK 11、Python 3.12+、Android SDK 30、Build Tools 30.0.3、NDK 22.1.7171670：

```sh
cd android
python3 prepare_runtime.py
./gradlew :terminal-emulator:testDebugUnitTest :app:assembleDebug
```

输出在 `app/build/outputs/apk/debug/`。初次构建联网下载运行时，所有下载都由
`runtime.lock.json` 固定版本并校验 SHA-256/SHA-512；不使用构建者的本地账号数据。
GitHub Actions 在向 `APK` 分支推送 Android 改动时自动构建。
Fork 仓库首次使用时，仓库所有者可能需要先在 Actions 页面启用工作流。

在 Android ARM64 的终端中，可运行以下测试，它在临时目录验证完整离线初始化、
Codex 启动、Git、ripgrep 和 apt 依赖状态，不安装应用或修改现有 Termux 环境：

```sh
python3 prepare_runtime.py
python3 smoke_runtime.py
```

该测试不代替 APK 安装、键盘、登录、后台和界面的真机验收。

## 许可证

Android 界面基于 GPLv3 的 Termux，保留 [LICENSE.md](LICENSE.md)。
Codex 是独立执行的 Apache-2.0 程序。来源、修改范围及运行时依赖见 [UPSTREAM.md](UPSTREAM.md)。
