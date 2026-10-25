#!/usr/bin/env python3
"""Install a pinned official Chromium provider only on an explicitly selected API 36 x64 emulator.

The system image bundles WebView 133, which cannot exercise navigation-listener events.
The APK is test infrastructure, never a library/sample dependency or published release asset.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ARCHIVE = 'https://commondatastorage.googleapis.com/chromium-browser-snapshots/AndroidDesktop_x64/1726604/chrome-android-desktop.zip'
MEMBER = 'chrome-android-desktop/apks/SystemWebView.apk'
APK_SHA256 = '150031825e119fb2cbc7d20734d23ec4f30ae0838b09974ac211709404f68a68'


class RemoteArchive(io.RawIOBase):
    """Seek over HTTPS ranges so CI downloads the provider instead of every browser APK."""
    def __init__(self, url):
        self.url = url
        self.position = 0
        with urllib.request.urlopen(urllib.request.Request(url, headers={'Range': 'bytes=0-0'}), timeout=60) as response:
            if response.status != 206:
                raise ValueError('Official archive server must support HTTP ranges.')
            self.size = int(response.headers['Content-Range'].split('/')[-1])

    def seek(self, offset, whence=0):
        self.position = offset if whence == 0 else self.position + offset if whence == 1 else self.size + offset
        return self.position

    def tell(self):
        return self.position

    def seekable(self):
        return True

    def read(self, size=-1):
        size = self.size - self.position if size < 0 else size
        if size <= 0:
            return b''
        end = min(self.size - 1, self.position + size - 1)
        request = urllib.request.Request(self.url, headers={'Range': f'bytes={self.position}-{end}'})
        with urllib.request.urlopen(request, timeout=120) as response:
            if response.status != 206:
                raise ValueError('Unexpected archive range response.')
            content = response.read()
        self.position += len(content)
        return content


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        raise SystemExit('Only an explicitly selected emulator may receive this test provider.')
    sdk = os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))
    adb = str(Path(sdk) / 'platform-tools/adb') if sdk else 'adb'
    prefix = [adb, '-s', args.serial]
    def shell(*parts):
        return subprocess.check_output(prefix + ['shell', *parts], text=True).strip()
    if shell('getprop', 'ro.kernel.qemu') != '1' or shell('getprop', 'ro.debuggable') != '1':
        raise SystemExit('A debuggable emulator is required; never modify a physical device.')
    if shell('getprop', 'ro.build.version.sdk') != '36' or shell('getprop', 'ro.product.cpu.abi') != 'x86_64':
        raise SystemExit('Pinned test provider is restricted to API 36 x86_64.')
    target = Path('build/ci-webview/SystemWebView.apk')
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(RemoteArchive(ARCHIVE)) as archive, archive.open(MEMBER) as source, target.open('wb') as output:
        shutil.copyfileobj(source, output, length=8 * 1024 * 1024)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != APK_SHA256:
        target.unlink()
        raise SystemExit('Official provider checksum differs from the reviewed pin; installation aborted.')
    subprocess.run(prefix + ['install', '--no-incremental', '-r', '-t', str(target)], check=True)
    result = shell('cmd', 'webviewupdate', 'set-webview-implementation', 'com.android.webview')
    if 'Success' not in result:
        raise SystemExit('Could not select the pinned test provider: ' + result)
    print('Selected official Chromium 158.0.8124.0 test provider with verified APK checksum.')
    print(shell('dumpsys', 'webviewupdate'))


if __name__ == '__main__':
    main()
