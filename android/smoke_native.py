#!/usr/bin/env python3
"""Run the real ARM64 Android engine with no Termux environment or real credentials.

A loopback mock Responses API verifies handshake, streamed text, image input,
client-side file tool calls, persisted thread resume, and turn interruption.
Run on Android ARM64 after prepare_native.py; this is not an APK UI test.
"""
import json
import os
from pathlib import Path
import queue
import subprocess
import struct
import tempfile
import threading
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parent
ENGINE = ROOT / 'native-app/src/main/jniLibs/arm64-v8a'
requests = []
release = threading.Event()


def completed(identifier):
    return {'type': 'response.completed', 'response': {'id': identifier,
            'usage': {'input_tokens': 1, 'output_tokens': 1, 'total_tokens': 2}}}


class Mock(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        body = self.rfile.read(int(self.headers['Content-Length']))
        if self.headers.get('Content-Encoding') == 'zstd':
            from compression import zstd
            body = zstd.decompress(body)
        requests.append(json.loads(body))
        number = len(requests)
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.end_headers()
        try:
            self.event({'type': 'response.created', 'response': {'id': f'resp-{number}'}})
            if number == 1:
                self.event({'type': 'response.output_item.done', 'item': {'type': 'function_call',
                            'call_id': 'read-file', 'name': 'android_files',
                            'arguments': json.dumps({'operation': 'read', 'path': 'attachment.txt'})}})
            elif number == 2:
                item = {'type': 'message', 'id': 'native-reply', 'role': 'assistant', 'content': []}
                self.event({'type': 'response.output_item.added', 'item': item, 'output_index': 0})
                self.event({'type': 'response.output_text.delta', 'item_id': 'native-reply',
                            'output_index': 0, 'content_index': 0, 'delta': '原生文件已读取'})
                item['content'] = [{'type': 'output_text', 'text': '原生文件已读取'}]
                self.event({'type': 'response.output_item.done', 'item': item, 'output_index': 0})
            else:
                release.wait(30)
            self.event(completed(f'resp-{number}'))
        except (BrokenPipeError, ConnectionResetError):
            pass

    def event(self, value):
        self.wfile.write(('data: ' + json.dumps(value) + '\n\n').encode())
        self.wfile.flush()


class Client:
    def __init__(self, environment, home):
        self.process = subprocess.Popen([str(ENGINE / 'libcodex.so'), 'app-server', '--listen', 'stdio://'],
                                        cwd=home, env=environment, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.queue = queue.Queue()
        self.events = []
        self.errors = []
        self.serial = 0
        def read():
            for line in self.process.stdout:
                self.queue.put(json.loads(line))
        def diagnostics():
            for line in self.process.stderr:
                self.errors.append(line)
        threading.Thread(target=read, daemon=True).start()
        threading.Thread(target=diagnostics, daemon=True).start()

    def send(self, value):
        self.process.stdin.write(json.dumps(value) + '\n')
        self.process.stdin.flush()

    def until(self, predicate, timeout=35):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                event = self.queue.get(timeout=min(1, max(.01, deadline - time.monotonic())))
            except queue.Empty:
                if self.process.poll() is not None:
                    raise AssertionError('Engine exited: ' + ''.join(self.errors[-10:]))
                continue
            self.events.append(event)
            if event.get('method') == 'item/tool/call':
                assert event['params']['tool'] == 'android_files'
                assert event['params']['arguments'] == {'operation': 'read', 'path': 'attachment.txt'}
                self.send({'id': event['id'], 'result': {'success': True,
                           'contentItems': [{'type': 'inputText', 'text': 'native-file-fixture'}]}})
            if predicate(event):
                return event
        raise AssertionError('Timeout; events=' + json.dumps(self.events[-5:], ensure_ascii=False) + '\n' + ''.join(self.errors[-10:]))

    def call(self, method, params):
        self.serial += 1
        identifier = str(self.serial)
        self.send({'id': identifier, 'method': method, 'params': params})
        response = self.until(lambda e: e.get('id') == identifier and 'method' not in e)
        assert 'error' not in response, response
        return response['result']

    def initialize(self):
        self.call('initialize', {'clientInfo': {'name': 'native_smoke', 'version': '0.2.0'},
                               'capabilities': {'experimentalApi': True}})
        self.send({'method': 'initialized'})

    def close(self):
        self.process.terminate()
        try:
            self.process.wait(5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()


def main():
    server = ThreadingHTTPServer(('127.0.0.1', 0), Mock)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='codex-native-') as temporary:
        home = Path(temporary)
        config = home / '.codex'
        config.mkdir()
        workspace = home / 'work'
        workspace.mkdir()
        cache = home / 'tmp'
        cache.mkdir()
        (workspace / 'attachment.txt').write_text('native-file-fixture')
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 2, 2, 8, 2, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress((b'\x00' + b'\xff\x00\x00' * 2) * 2)) + chunk(b'IEND', b'')
        (workspace / 'image.png').write_bytes(png)
        (config / 'config.toml').write_text(f'''check_for_update_on_startup = false
cli_auth_credentials_store = "file"
model_provider = "mobile"
[model_providers.mobile]
name = "Custom"
wire_api = "responses"
requires_openai_auth = false
env_key = "CODEX_MOBILE_API_KEY"
base_url = "http://127.0.0.1:{server.server_port}/v1"
''')
        environment = {k: v for k, v in os.environ.items() if k.startswith('ANDROID_') or k in ('BOOTCLASSPATH', 'EXTERNAL_STORAGE')}
        environment.update(HOME=str(home), CODEX_HOME=str(config), TMPDIR=str(cache),
                           PATH='/system/bin', SHELL='/system/bin/sh', LANG='en_US.UTF-8',
                           LD_LIBRARY_PATH=str(ENGINE), CODEX_SELF_EXE=str(ENGINE / 'libcodex.so'),
                           CODEX_MOBILE_API_KEY='local-test-key')
        assert 'PREFIX' not in environment and 'LD_PRELOAD' not in environment
        client = Client(environment, home)
        try:
            client.initialize()
            assert client.call('account/read', {}).get('account') is None
            tool = {'type': 'function', 'name': 'android_files', 'description': 'Read native workspace files',
                    'inputSchema': {'type': 'object', 'properties': {'operation': {'type': 'string'},
                                    'path': {'type': 'string'}}, 'required': ['operation'], 'additionalProperties': False}}
            thread = client.call('thread/start', {'cwd': str(workspace), 'sandbox': 'danger-full-access',
                                 'approvalPolicy': 'untrusted', 'model': 'gpt-5.4', 'dynamicTools': [tool]})['thread']['id']
            client.call('turn/start', {'threadId': thread, 'input': [
                {'type': 'localImage', 'path': str(workspace / 'image.png')},
                {'type': 'text', 'text': 'Read attachment.txt', 'textElements': []}]})
            done = client.until(lambda e: e.get('method') == 'turn/completed')
            assert done['params']['turn']['status'] == 'completed', done
            assert any(e.get('method') == 'item/agentMessage/delta' and e['params']['delta'] == '原生文件已读取' for e in client.events)
            assert any(e.get('method') == 'item/tool/call' for e in client.events)
            assert len(requests) == 2, requests
            assert 'data:image/' in json.dumps(requests[0]), requests[0]['input'][-1]
            assert 'native-file-fixture' in json.dumps(requests[1])
            assert requests[0]['model'] == 'gpt-5.4'
        finally:
            client.close()
        client = Client(environment, home)
        try:
            client.initialize()
            result = client.call('thread/resume', {'threadId': thread, 'cwd': str(workspace),
                                 'sandbox': 'danger-full-access', 'approvalPolicy': 'untrusted'})
            assert result['thread']['id'] == thread
            turn = client.call('turn/start', {'threadId': thread,
                               'input': [{'type': 'text', 'text': 'Wait', 'textElements': []}]})['turn']['id']
            client.call('turn/interrupt', {'threadId': thread, 'turnId': turn})
            done = client.until(lambda e: e.get('method') == 'turn/completed')
            assert done['params']['turn']['status'] == 'interrupted', done
        finally:
            release.set()
            client.close()
            server.shutdown()
    print('PASS: clean Android environment, handshake, image input, native file RPC, streaming, resume, interruption')


if __name__ == '__main__':
    main()
