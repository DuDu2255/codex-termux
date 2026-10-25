# Source provenance

The Android application, terminal emulator and terminal view are based on
[termux/termux-app](https://github.com/termux/termux-app), tag `v0.118.3`,
commit `5b657c6adf4304e5198951ce815fe0205dcac29c`.
The original GPLv3 license is retained in `LICENSE.md`; the application has a
different package identifier and is not an official Termux release.

Changes: independent app identity, streamed bootstrap extraction, checked archive
paths, ARM64-only packaging, PRoot command launch, bundled Codex, offline Git and
ripgrep installation, automatic interactive Codex startup, build automation.

The runtime versions, official download URLs and checksums are pinned in
`runtime.lock.json`. Codex remains a separately executed Apache-2.0 program; its
LICENSE and NOTICE are included under `$PREFIX/opt/codex/`. This APK bundles the
published `0.163.0-termux.2` Android binaries, rather than rebuilding Rust as part
of every APK build. To change the bundled Codex version, update the lock file and
verify the Android runtime again.

Runtime sources and licenses:

- [Codex Termux](https://github.com/DioNanos/codex-termux), Apache-2.0
- [Termux package build recipes and patches](https://github.com/termux/termux-packages/tree/43b9bf012486bd913d603f7bf0d3f6fddd419a8d)
- [Termux PRoot](https://github.com/termux/proot), GPL-2.0
- [talloc](https://talloc.samba.org/), LGPL-3.0-or-later
- [libandroid-shmem](https://github.com/termux/libandroid-shmem), MIT
- Git, ripgrep and their dependencies retain their package copyright notices in
  `$PREFIX/share/doc` where provided by their packages.

`prepare_runtime.py` changes only PRoot's ELF library-name and RUNPATH strings,
so Android can install its dependencies under `nativeLibraryDir`; the byte
replacements are specified in source. No private keys, tokens, local account
configuration or existing Termux home directories are part of the runtime.
