#!/usr/bin/env python3
"""Reject packaging regressions that could reintroduce the terminal runtime."""
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    names = apk.namelist()
    expected = {'lib/arm64-v8a/libcodex.so', 'lib/arm64-v8a/libcode-mode-host.so',
                'lib/arm64-v8a/libc++_shared.so'}
    assert {name for name in names if name.startswith('lib/')} == expected
    assert not any(any(part in name.lower() for part in ('bootstrap', 'proot', 'rootfs', 'termux')) for name in names)
    for name in expected:
        assert apk.read(name)[:4] == b'\x7fELF', name
    for name in names:
        if name.endswith('.dex'):
            classes = apk.read(name)
            assert b'Lcom/termux/' not in classes
            assert b'Landroid/webkit/WebView;' not in classes
    assert 'assets/engine-notices/LICENSE' in names
    assert 'assets/engine-notices/NOTICE' in names
print('Verified: native app, 3 Android ARM64 engine libraries, no terminal/bootstrap/PRoot/WebView')
