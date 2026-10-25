#!/usr/bin/env python3
"""Stream a CI command and include its failure details in public annotations."""
from collections import deque
import os
from pathlib import Path
import subprocess
import sys


def main():
    label, *command = sys.argv[1:]
    directory = Path('.build-logs')
    directory.mkdir(exist_ok=True)
    recent = deque(maxlen=100)
    with (directory / (label + '.log')).open('w') as log:
        process = subprocess.Popen(command, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, errors='replace')
        for line in process.stdout:
            print(line, end='', flush=True)
            log.write(line)
            recent.append(line)
        status = process.wait()
    if status:
        tail = ''.join(recent)[-12000:]
        escaped = tail.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::error title={label} failed::{escaped}')
        if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(summary, 'a') as out:
                out.write(f'### {label} failed (exit {status})\n\n```text\n{tail}\n```\n')
    return status


if __name__ == '__main__':
    sys.exit(main())
