"""Smoke-test the minified Java consumer and exact showtime URL copying in sample debug."""
import re
import shlex
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
            try:
                dump = self.command('shell', 'uiautomator', 'dump', remote_xml, timeout=20)
            except subprocess.CalledProcessError as error:
                if error.returncode != 137:
                    raise
                time.sleep(0.25)
                continue
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
    sample = sample_namespace + '.debug'
    test_package = namespace + '.test'
    failed_ui = output / 'smoke-failed-ui.xml'

    def find(package, description, matches):
        return device.find(package, description, matches, failed_ui)

    def verify_copied_url(expected_url, log_name):
        result = device.shell(
            'am', 'instrument', '-w', '-r', '-e', 'class', namespace + '.AppClipboardProbeTest#copiedAppUrlMatchesExactly',
            '-e', 'wviExpectedUrl', shlex.quote(expected_url), test_package + '/androidx.test.runner.AndroidJUnitRunner',
        )
        (output / log_name).write_text(result)
        if 'OK (1 test)' not in result or re.search(r'INSTRUMENTATION_STATUS_CODE: -[1234]\b|FAILURES!!!|INSTRUMENTATION_FAILED', result):
            raise RuntimeError(f'Exact URL copying failed. See {output / log_name}')

    try:
        device.start(root / 'consumer-fixture/app/build/outputs/apk/diagnostic/app-diagnostic.apk',
                     consumer, 'example.consumer.ConsumerActivity')
        device.tap(find(consumer, 'Java consumer inspector button', has_id('wvi_open')))
        device.tap(find(consumer, 'Java consumer Live WebView',
                        contains('content-desc', ', consumer.example, /start')))
        find(consumer, 'Java consumer inspector sheet', has_id('wvi_history_tab'))
        device.tap(find(consumer, 'Java consumer copy URL', has_id('wvi_copy_url')))
        verify_copied_url('https://consumer.example/start?raw=A%2Bb&dup=1&dup=2#raw', 'consumer-clipboard.log')
        device.start(root / 'sample/build/outputs/apk/debug/sample-debug.apk',
                     sample, sample_namespace + '.MainActivity')
        device.tap(find(sample, 'Movies tab', has_id('tab_movies')))
        device.tap(find(sample, '6:45 PM showtime', contains('text', '6:45 PM')))
        device.tap(find(sample, 'inspector button', has_id('wvi_open')))
        device.tap(find(sample, 'showtime WebView', contains('content-desc', '/afterframe/showtime.html')))
        device.tap(find(sample, 'History tab', has_id('wvi_history_tab')))
        showtime_url = ('https://appassets.androidplatform.net/afterframe/showtime.html'
                        '?film=midnight-local&cinema=marquee&time=18%3A45')
        device.tap(find(sample, 'showtime history', contains('content-desc', '/showtime.html')))
        device.tap(find(sample, 'copy exact showtime URL',
                        lambda node: node.get('content-desc') == f'Copy recorded URL: {showtime_url}'))
        verify_copied_url(showtime_url, 'smoke-clipboard.log')
        (output / 'smoke.txt').write_text(
            'The minified Java consumer opened Live and copied its original URL exactly. '
            'Afterframe recorded a showtime link and copied its original URL exactly.\n',
        )
    finally:
        for package in (consumer, sample):
            device.shell('am', 'force-stop', package)
        device.shell('rm', '-f', '/data/local/tmp/wvi-compat-window.xml')
