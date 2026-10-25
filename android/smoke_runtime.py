#!/usr/bin/env python3
"""Exercise the APK's runtime on Android ARM64 without installing an APK.

Uses a fresh private directory. Never runs against the user's existing prefix.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from prepare_runtime import ROOT, safe_name


def main():
    if not Path('/system/bin/linker64').exists():
        raise SystemExit('Run this smoke test on an Android ARM64 device.')
    # Keep native programs outside the bind destination when testing inside Termux.
    with tempfile.TemporaryDirectory(prefix='codex-apk-smoke-') as temporary:
        root = Path(temporary)
        files = root / 'files'
        prefix = files / 'usr'
        home = files / 'home'
        home.mkdir(parents=True)
        native = root / 'native'
        shutil.copytree(ROOT / 'app/src/main/jniLibs/arm64-v8a', native)
        for path in native.iterdir():
            path.chmod(0o700)
        with zipfile.ZipFile(ROOT / 'app/src/main/assets/bootstrap.zip') as bundle:
            links = []
            for info in bundle.infolist():
                name = safe_name(info.filename)
                if name == 'SYMLINKS.txt':
                    links = [line.split('←') for line in bundle.read(info).decode().splitlines()]
                    continue
                target = prefix / name
                if info.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with bundle.open(info) as src, target.open('wb') as dst:
                        shutil.copyfileobj(src, dst)
                    if name.startswith(('bin/', 'libexec', 'lib/apt/', 'opt/codex/bin/')) or name.endswith('termux-bootstrap-second-stage.sh'):
                        target.chmod(0o700)
            for destination, name in links:
                target = prefix / safe_name(name)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.symlink_to(destination)
        # Exercise current source scripts even while iterating on the bundle.
        for name in ('codex-host', 'codex-guest'):
            shutil.copyfile(ROOT / 'runtime' / name, prefix / 'libexec' / name)
        shutil.copyfile(ROOT / 'runtime/codex-second-stage',
                        prefix / 'etc/termux/bootstrap/termux-bootstrap-second-stage.sh')
        env = {key: value for key, value in os.environ.items()
               if key.startswith('ANDROID_') or key in ('BOOTCLASSPATH', 'EXTERNAL_STORAGE')}
        env.update(CODEX_APP_FILES=str(files), CODEX_NATIVE_DIR=str(native),
                   HOME=str(home), PREFIX=str(prefix), TMPDIR=str(prefix / 'tmp'),
                   PATH='/system/bin', TERM='xterm-256color', LANG='en_US.UTF-8',
                   PWD=str(home), TERMUX_VERSION='0.1.0', CODEX_TERMINAL_ENTERED='1')

        def run(*args, timeout=120):
            command = ['/system/bin/sh', str(prefix / 'libexec/codex-host'), *args]
            subprocess.run(command, cwd=home, env=env, check=True, timeout=timeout)

        run(str(prefix / 'bin/bash'), '-c',
            'set -e; test "$HOME" = /data/data/com.termux/files/home; '
            'test ! -e "$HOME/.codex/auth.json"; '
            'printf "isolated-runtime\\n" > "$HOME/probe"; '
            'cat "$HOME/probe"; codex --version')
        assert (home / 'probe').read_text() == 'isolated-runtime\n'
        run(str(prefix / 'etc/termux/bootstrap/termux-bootstrap-second-stage.sh'))
        run(str(prefix / 'bin/bash'), '-c',
            'set -e; git --version; rg --version; '
            'git init -q sample; cd sample; printf "codex-test\\n" > probe; '
            'rg codex-test probe; apt-get check; codex --help > /dev/null; '
            'printf "RUNTIME_SMOKE_OK\\n"')
        # Codex helper must be executable and resolve its bundled C++ runtime.
        run(str(prefix / 'bin/bash'), '-c',
            'test -x "$PREFIX/opt/codex/bin/codex-code-mode-host"; '
            'test -x "$PREFIX/opt/codex/bin/codex.bin"')


if __name__ == '__main__':
    main()
