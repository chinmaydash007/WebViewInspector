#!/usr/bin/env python3
"""Encrypt a locally stored Central user token into this PRIVATE repository's Actions secrets.

Requires PyNaCl in an isolated tooling environment. Never prints credential values.
"""
import argparse
import json
import os
from pathlib import Path
import stat
import subprocess
import urllib.request
from nacl.encoding import Base64Encoder
from nacl.public import PublicKey, SealedBox

parser = argparse.ArgumentParser()
parser.add_argument('credentials', type=Path, help='Owner-only JSON file with Central token username/password')
args = parser.parse_args()
info = args.credentials.stat()
if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
    raise SystemExit('Credential file must belong to you and have mode 600.')
credentials = json.loads(args.credentials.read_text())
if not all(isinstance(credentials.get(name), str) and credentials[name].strip() for name in ['username', 'password']):
    raise SystemExit('Expected nonempty token username/password fields.')
token = os.environ.get('GITHUB_TOKEN')
if not token:
    raw = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n', text=True, capture_output=True, check=True).stdout
    token = dict(line.split('=', 1) for line in raw.splitlines() if '=' in line).get('password')
if not token:
    raise SystemExit('GitHub authentication is required.')
base = 'https://api.github.com/repos/chinmaydash007/webview-inspector'
def request(path, method='GET', body=None):
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, method=method,
        headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json', 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response) if response.status != 204 else None
if request('')['private'] is not True:
    raise SystemExit('Refusing configuration: repository must remain PRIVATE.')
key = request('/actions/secrets/public-key')
box = SealedBox(PublicKey(key['key'], encoder=Base64Encoder))
for name, value in [('CENTRAL_USERNAME', credentials['username']), ('CENTRAL_PASSWORD', credentials['password'])]:
    encrypted = Base64Encoder.encode(box.encrypt(value.encode())).decode()
    request('/actions/secrets/' + name, 'PUT', {'key_id': key['key_id'], 'encrypted_value': encrypted})
    print('Configured encrypted secret:', name)
