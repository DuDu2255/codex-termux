# Native engine provenance

The Android app code in `native-app` is a new native Java application. The old Termux application, terminal emulator/view/shared modules, bootstrap installer, PRoot, and Linux runtime packaging have been removed from this branch.

The engine is the Android/Bionic build of Codex from the pinned npm package `@mmmbuto/codex-cli-termux@0.163.0-termux.2`. The package name describes its original CLI distribution; the APK does not execute its JavaScript/Termux launcher or depend on the Termux application. See `runtime.lock.json` for the exact URL and SHA-512. `prepare_native.py` verifies the complete archive before extraction.

Only these files are packaged:

| Package file | APK file |
| --- | --- |
| `bin/codex.bin` | `lib/arm64-v8a/libcodex.so` |
| `bin/codex-code-mode-host` | `lib/arm64-v8a/libcode-mode-host.so` |
| `bin/libc++_shared.so` | `lib/arm64-v8a/libc++_shared.so` |
| `LICENSE`, `NOTICE` | `assets/engine-notices/` |

Android extracts native executable files using `lib*.so` names. The engine expects its helper next to its executable under the name `codex-code-mode-host`. The preparation script replaces exactly three occurrences of that basename in the main ELF with the equal-length `libcode-mode-host.so`. This preserves ELF offsets and Rust string lengths; it changes helper discovery, not execution logic. The script refuses an unexpected layout. This is equivalent to changing `CODE_MODE_HOST_EXECUTABLE_NAME` in `codex-rs/install-context/src/lib.rs` for APK packaging. All three ELF binaries have 16 KB-aligned LOAD segments.

The app uses a fresh private HOME/CODEX_HOME and clears inherited environment variables. PATH and SHELL refer only to Android's `/system/bin`. LD_LIBRARY_PATH points only to the APK-installed native library directory. No environment prefix, bootstrap ZIP, Debian package or rootfs is used. The engine runs under the app UID and Android SELinux sandbox; the Linux-specific Codex sandbox is unavailable on Android. File tools validate canonical paths, and operation approvals are surfaced in the UI.

The core engine follows the licenses and notices copied from the pinned package. The repository's existing source licenses remain applicable. Authentication and transport follow the [official Codex app-server documentation](https://developers.openai.com/codex/app-server).
