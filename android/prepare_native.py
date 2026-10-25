#!/usr/bin/env python3
"""Package only Android ELF executables; no bootstrap, shell distribution or PRoot."""
import base64
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parent


def prepare():
    spec = json.loads((ROOT / 'runtime.lock.json').read_text())['codex']
    expected = base64.b64decode(spec['sha512']).hex()
    cache = ROOT / '.runtime-cache'
    cache.mkdir(exist_ok=True)
    archive = cache / expected

    def valid(path):
        if not path.exists():
            return False
        with path.open('rb') as f:
            return hashlib.file_digest(f, 'sha512').hexdigest() == expected

    if not valid(archive):
        partial = archive.with_suffix('.part')
        with urllib.request.urlopen(spec['url'], timeout=120) as src, partial.open('wb') as dst:
            shutil.copyfileobj(src, dst)
        if not valid(partial):
            raise ValueError('Codex package integrity mismatch')
        partial.replace(archive)
    native = ROOT / 'native-app/src/main/jniLibs/arm64-v8a'
    notices = ROOT / 'native-app/src/main/assets/engine-notices'
    native.mkdir(parents=True, exist_ok=True)
    notices.mkdir(parents=True, exist_ok=True)
    mappings = {'bin/codex.bin': native / 'libcodex.so',
                'bin/codex-code-mode-host': native / 'libcode-mode-host.so',
                'bin/libc++_shared.so': native / 'libc++_shared.so',
                'LICENSE': notices / 'LICENSE', 'NOTICE': notices / 'NOTICE'}
    with tarfile.open(archive, 'r:gz') as bundle:
        for name, destination in mappings.items():
            with bundle.extractfile('package/' + name) as src, destination.open('wb') as dst:
                shutil.copyfileobj(src, dst)
            if destination.parent == native:
                destination.chmod(0o755)
    # Android installs executable assets in nativeLibraryDir only as lib*.so.
    # This equal-length replacement preserves ELF offsets and Rust string lengths.
    # It is equivalent to changing CODE_MODE_HOST_EXECUTABLE_NAME in install-context.
    main = native / 'libcodex.so'
    data = main.read_bytes()
    old, new = b'codex-code-mode-host', b'libcode-mode-host.so'
    assert len(old) == len(new)
    if data.count(old) != 3:
        raise ValueError('Unexpected Codex executable layout; audit helper discovery before updating')
    main.write_bytes(data.replace(old, new))
    print('Prepared native Android engine:', spec['version'])


if __name__ == '__main__':
    prepare()
