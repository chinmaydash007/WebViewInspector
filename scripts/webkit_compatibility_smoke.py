"""Exercise the two minified validation apps and read back an exact copied redirect URL."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


class Emulator:
    def __init__(self, adb, serial, api):
        self.serial = serial
        self.api = api
        self.adb = adb
        if not re.fullmatch(r'emulator-\d+', serial) or self.shell('getprop', 'ro.kernel.qemu').strip() != '1':
            raise ValueError('An explicitly selected emulator is required.')
        if self.shell('getprop', 'ro.build.version.sdk').strip() != str(api):
            raise ValueError(f'{serial} is not API {api}.')
        if self.shell('getprop', 'sys.boot_completed').strip() != '1':
            raise ValueError(f'{serial} has not finished booting.')

    def command(self, *args, timeout=45):
        return subprocess.check_output([self.adb, '-s', self.serial, *args], text=True, timeout=timeout)

    def shell(self, *args):
        return self.command('shell', *args)

    def start(self, apk, package, activity):
        self.command('install', '--no-incremental', '-r', str(apk))
        self.shell('am', 'force-stop', package)
        self.shell('am', 'start', '-W', '-n', f'{package}/{activity}')

    def find(self, package, description, matches, evidence):
        remote_xml = '/data/local/tmp/wvi-compat-window.xml'
        deadline = time.monotonic() + 45
        last_xml = ''
        while time.monotonic() < deadline:
            dump = self.command('shell', 'uiautomator', 'dump', remote_xml, timeout=20)
            if remote_xml not in dump:
                continue
            last_xml = self.command('exec-out', 'cat', remote_xml)
            for node in ET.fromstring(last_xml).iter('node'):
                if node.get('package') == package and matches(node):
                    return node
            time.sleep(0.25)
        evidence.write_text(last_xml)
        raise RuntimeError(f'{package}: could not find {description}. UI: {evidence}')

    def tap(self, node):
        left, top, right, bottom = map(int, re.findall(r'\d+', node.attrib['bounds']))
        self.shell('input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def contains(attribute, text):
    return lambda node: text.casefold() in node.get(attribute, '').casefold()


def has_id(name):
    return lambda node: node.get('resource-id', '').endswith(':id/' + name)


def smoke(device, root, namespace, output):
    consumer = 'example.webviewinspector.consumer.diagnostic'
    sample_namespace = namespace + '.sample'
    sample = sample_namespace + '.diagnostic'
    test_package = namespace + '.test'
    failed_ui = output / 'smoke-failed-ui.xml'

    def find(package, description, matches):
        return device.find(package, description, matches, failed_ui)

    try:
        device.start(root / 'consumer-fixture/app/build/outputs/apk/diagnostic/app-diagnostic.apk',
                     consumer, 'example.consumer.ConsumerActivity')
        find(consumer, 'Java consumer inspector', has_id('wvi_open'))
        device.start(root / 'sample/build/outputs/apk/diagnostic/sample-diagnostic.apk',
                     sample, sample_namespace + '.MainActivity')
        device.tap(find(sample, 'XML demo', contains('text', 'Advanced with real redirects')))
        device.tap(find(sample, 'redirect link', contains('text', 'Navigate through two redirects')))
        device.tap(find(sample, 'inspector button', has_id('wvi_open')))
        device.tap(find(sample, 'History tab', has_id('wvi_history_tab')))
        device.tap(find(sample, 'grouped redirect history', contains('content-desc', '/destination, 2 redirects · Committed')))
        middle = find(sample, 'original intermediate URL',
                      lambda node: node.get('content-desc', '').endswith('/redirect-two?encoded=%2f%2F'))
        expected_url = middle.attrib['content-desc'].split(': ', 1)[1]
        device.tap(middle)
        result = device.shell(
            'am', 'instrument', '-w', '-r', '-e', 'class', namespace + '.AppClipboardProbeTest#copiedAppUrlMatchesExactly',
            '-e', 'wviExpectedUrl', expected_url, test_package + '/androidx.test.runner.AndroidJUnitRunner',
        )
        (output / 'smoke-clipboard.log').write_text(result)
        if 'OK (1 test)' not in result or re.search(r'INSTRUMENTATION_STATUS_CODE: -[1234]\b|FAILURES!!!|INSTRUMENTATION_FAILED', result):
            raise RuntimeError(f'The minified sample did not copy the exact URL. See {output / "smoke-clipboard.log"}')
        (output / 'smoke.txt').write_text(
            'The minified Java consumer opened the inspector. The minified sample grouped two redirects '
            'and copied the original intermediate URL exactly.\n',
        )
    finally:
        for package in (consumer, sample):
            device.shell('am', 'force-stop', package)
        device.shell('rm', '-f', '/data/local/tmp/wvi-compat-window.xml')
