#!/usr/bin/env python3
"""Exercise a running dev-profile Sentinel API using an owned loopback fixture.

Requires 127.0.0.1 in APPROVED_LOCAL_HOSTS. Creates and deletes only its own
monitor. Uses no public endpoints, third-party Python packages, or real data.
"""
import argparse
import json
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', default='http://127.0.0.1:8080/api')
    parser.add_argument('--frontend', help='Optional Vite/nginx URL to verify /api proxy')
    args = parser.parse_args()

    def request(path, method='GET', body=None):
        req = urllib.request.Request(
            args.api.rstrip('/') + path,
            data=json.dumps(body).encode() if body is not None else None,
            method=method,
            headers={'Content-Type': 'application/json'} if body is not None else {},
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None

    def until(callback, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = callback()
            if value:
                return value
            threading.Event().wait(0.2)  # Poll observable state, not an assumed completion time.
        raise AssertionError('Timed out waiting for scheduled state')

    recovered = threading.Event()

    class Endpoint(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200 if recovered.is_set() else 503)
            self.send_header('Content-Length', '0')
            self.end_headers()

        def log_message(self, *_args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Endpoint)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    monitor_id = None
    try:
        config = {
            'name': 'Sentinel live smoke test',
            'url': f'http://127.0.0.1:{server.server_port}/health',
            'intervalSeconds': 5, 'timeoutMs': 1000,
            'degradationThresholdMs': 200, 'enabled': True,
        }
        code, monitor = request('/monitors', 'POST', config)
        assert code == 201
        monitor_id = monitor['id']
        resource = f'/monitors/{monitor_id}'

        def active_incident():
            history = request(resource + '/incidents')[1]['content']
            return history[0] if history and history[0]['active'] else None

        incident = until(active_incident)
        checks = request(resource + '/checks')[1]['content']
        assert len(checks) >= 3
        assert all(c['httpStatus'] == 503 and c['failureReason'] == 'HTTP_ERROR' for c in checks)
        assert incident['startedAt'] == checks[-1]['checkedAt']
        print('PASS: scheduled failures persisted; incident starts at first failure', flush=True)

        recovered.set()

        def resolved_incident():
            current = request(resource + '/incidents')[1]['content'][0]
            return current if not current['active'] else None

        resolved = until(resolved_incident)
        assert resolved['recoveredAt'] and resolved['durationSeconds'] >= 10
        assert request(resource)[1]['currentStatus'] in ('UP', 'DEGRADED')
        print('PASS: scheduled recovery resolves incident', flush=True)

        config['name'] = 'Renamed smoke test'
        assert request(resource, 'PUT', config)[1]['name'] == config['name']
        assert not request(resource + '/enabled', 'PATCH', {'enabled': False})[1]['enabled']
        assert request(resource + '/enabled', 'PATCH', {'enabled': True})[1]['enabled']
        print('PASS: edit and pause/resume', flush=True)

        if args.frontend:
            with urllib.request.urlopen(args.frontend.rstrip('/') + '/api/dashboard', timeout=5) as response:
                assert json.load(response)['total'] >= 1
            with urllib.request.urlopen(args.frontend, timeout=5) as response:
                assert response.status == 200 and b'<title>Sentinel' in response.read()
            print('PASS: frontend HTML and real API proxy', flush=True)
    finally:
        try:
            if monitor_id:
                assert request(f'/monitors/{monitor_id}', 'DELETE')[0] == 204
                print('PASS: test monitor and history deleted', flush=True)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)


if __name__ == '__main__':
    main()
