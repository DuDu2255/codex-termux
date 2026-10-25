#!/usr/bin/env python3
"""Assemble a verified, offline ARM64 runtime. No user's Termux files are copied."""
import base64
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / '.runtime-cache'
PREFIX = 'data/data/com.termux/files/usr/'


def download(spec):
    CACHE.mkdir(exist_ok=True)
    digest = spec.get('sha256') or base64.b64decode(spec['sha512']).hex()
    algorithm = 'sha256' if 'sha256' in spec else 'sha512'
    target = CACHE / digest

    def valid(path):
        if not path.is_file():
            return False
        with path.open('rb') as source:
            return hashlib.file_digest(source, algorithm).hexdigest() == digest

    if not valid(target):
        partial = target.with_suffix('.part')
        print('Downloading', spec['url'], flush=True)
        request = urllib.request.Request(spec['url'], headers={'User-Agent': 'CodexTerminal-build/1'})
        with urllib.request.urlopen(request, timeout=120) as response, partial.open('wb') as output:
            shutil.copyfileobj(response, output)
        if not valid(partial):
            raise ValueError('Checksum mismatch: ' + spec['url'])
        partial.replace(target)
    return target


def deb_files(path):
    """Read only regular files from the data member of a Debian ar archive."""
    with path.open('rb') as source:
        if source.read(8) != b'!<arch>\n':
            raise ValueError('Invalid Debian archive')
        while header := source.read(60):
            size = int(header[48:58])
            name = header[:16].decode().strip().rstrip('/')
            payload = source.read(size)
            if size % 2:
                source.read(1)
            if name.startswith('data.tar'):
                with tarfile.open(fileobj=io.BytesIO(payload), mode='r:*') as archive:
                    return {member.name.removeprefix('./'): archive.extractfile(member).read()
                            for member in archive if member.isfile()}
    raise ValueError('Missing data.tar')


def safe_name(name):
    if name.startswith('/') or '..' in PurePosixPath(name).parts:
        raise ValueError('Unsafe archive entry: ' + name)
    return name


def prepare():
    lock = json.loads((ROOT / 'runtime.lock.json').read_text())
    specs = [lock['bootstrap'], lock['codex'], *lock['packages']]
    with ThreadPoolExecutor(max_workers=4) as workers:
        paths = list(workers.map(download, specs))
    bootstrap, codex, *debs = paths

    native = ROOT / 'app/src/main/jniLibs/arm64-v8a'
    native.mkdir(parents=True, exist_ok=True)
    host_files = {}
    for spec, path in zip(lock['packages'], debs):
        if spec['name'] in ('proot', 'libtalloc', 'libandroid-shmem'):
            host_files.update(deb_files(path))
    proot = host_files[PREFIX + 'bin/proot']
    # Android extracts native files only when named lib*.so. Shorter ELF strings
    # can be replaced in-place without changing any offsets or instructions.
    for old, new in [(b'libtalloc.so.2\0', b'libtalloc.so\0'),
                     (b'/data/data/com.termux/files/usr/lib\0', b'$ORIGIN\0')]:
        if old not in proot:
            raise ValueError('Unexpected proot ELF layout')
        proot = proot.replace(old, new.ljust(len(old), b'\0'))
    outputs = {
        'libproot.so': proot,
        'libproot-loader.so': host_files[PREFIX + 'libexec/proot/loader'],
        'libtalloc.so': host_files[PREFIX + 'lib/libtalloc.so.2.5.0'],
        'libandroid-shmem.so': host_files[PREFIX + 'lib/libandroid-shmem.so'],
    }
    for name, content in outputs.items():
        file = native / name
        file.write_bytes(content)
        file.chmod(0o700)

    assets = ROOT / 'app/src/main/assets'
    assets.mkdir(parents=True, exist_ok=True)
    output = assets / 'bootstrap.zip'
    temporary = output.with_suffix('.zip.part')
    stage = 'etc/termux/bootstrap/termux-bootstrap-second-stage.sh'
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
        with zipfile.ZipFile(bootstrap) as original:
            for info in original.infolist():
                name = safe_name(info.filename)
                if name == stage:
                    name = 'etc/termux/bootstrap/termux-bootstrap-original.sh'
                with original.open(info) as src, bundle.open(name, 'w') as dst:
                    shutil.copyfileobj(src, dst)
        with tarfile.open(codex, 'r:gz') as archive:
            for member in archive:
                if not member.isfile():
                    continue
                if not member.name.startswith('package/'):
                    raise ValueError('Unexpected npm package layout')
                name = 'opt/codex/' + safe_name(member.name[len('package/'):])
                with archive.extractfile(member) as src, bundle.open(name, 'w') as dst:
                    shutil.copyfileobj(src, dst)
        for spec, path in zip(lock['packages'], debs):
            if spec['install']:
                bundle.write(path, 'opt/codex-debs/' + spec['name'] + '.deb')
        scripts = {
            'codex-host': 'libexec/codex-host',
            'codex-guest': 'libexec/codex-guest',
            'codex': 'bin/codex',
            'codex-startup.sh': 'etc/profile.d/zz-codex.sh',
            'codex-second-stage': stage,
            'termux-open-url': 'libexec/codex-tools/termux-open-url',
            'termux-setup-storage': 'libexec/codex-tools/termux-setup-storage',
            'termux-reload-settings': 'libexec/codex-tools/termux-reload-settings',
        }
        for src, dst in scripts.items():
            bundle.write(ROOT / 'runtime' / src, dst)
    temporary.replace(output)
    print('Prepared', output, '(' + str(output.stat().st_size) + ' bytes)', flush=True)


if __name__ == '__main__':
    prepare()
