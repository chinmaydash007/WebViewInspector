#!/usr/bin/env python3
"""Run the full local validation matrix on two explicitly selected emulators."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from bundle import ARTIFACTS, ROOT, properties
from webkit_compatibility_smoke import Emulator, smoke


WEBKIT_VERSIONS = ('1.15.0', '1.16.0')


def logged(command, log, *, env=None):
    print(f'Running {log.stem}. Log: {log}', flush=True)
    with log.open('w') as output:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'{log.stem} failed. See {log}')


def resolution(version, log, *, consumer_args=()):
    logged([
        str(ROOT / 'gradlew'), '--no-daemon', '--init-script',
        str(ROOT / 'scripts/validation-webkit.init.gradle'),
        f'-PvalidationWebkitVersion={version}', *consumer_args, 'verifyWebkitResolution',
    ], log)


def archive_hashes(repository, config):
    group = repository.joinpath(*config['GROUP'].split('.'))
    version = config['VERSION_NAME']
    return {
        artifact: hashlib.sha256(
            (group / artifact / version / f'{artifact}-{version}.aar').read_bytes(),
        ).hexdigest()
        for artifact in ARTIFACTS
    }


def check_no_op(project, aapt, namespace):
    build = project / 'build/outputs'
    apk = build / 'apk/release' / f'{project.name}-release.apk'
    resources = subprocess.check_output([aapt, 'dump', 'resources', apk], text=True)
    mapping = (build / 'mapping/release/mapping.txt').read_text()
    if '/wvi_' in resources or f'{namespace}.internal.' in mapping or 'androidx.webkit.' in mapping:
        raise RuntimeError(f'Inspector implementation or WebKit leaked into {apk}')


def native_gate(device, output):
    logged([
        sys.executable, str(ROOT / 'scripts/device_tests.py'), '--serial', device.serial,
        '--api', str(device.api), '--evidence-dir', str(output / f'api-{device.api}'),
    ], output / f'api-{device.api}.log')


def run_lane(version, lane, devices, aapt, config, expected_hashes=None):
    lane.mkdir()
    namespace = config['INSPECTOR_PACKAGE']
    repository = ROOT / 'build/validation-repository'
    consumer_args = (
        '-p', 'consumer-fixture', f'-PinspectorVersion={config["VERSION_NAME"]}',
        f'-PvalidationRepository={repository}',
    )
    logged(['bash', 'scripts/verify.sh'], lane / 'standalone.log',
           env={**os.environ, 'VALIDATION_WEBKIT_VERSION': version})
    resolution(version, lane / 'library-and-sample-resolution.log')
    resolution(version, lane / 'consumer-resolution.log', consumer_args=consumer_args)
    if version == WEBKIT_VERSIONS[0]:
        resolution('1.14.0', lane / 'consumer-floor-resolution.log', consumer_args=consumer_args)
    hashes = archive_hashes(repository, config)
    (lane / 'aar-sha256.txt').write_text(''.join(f'{digest}  {name}.aar\n' for name, digest in hashes.items()))
    if expected_hashes is not None and hashes != expected_hashes:
        raise RuntimeError('The test-only WebKit override changed the staged AARs.')

    for project in (ROOT / 'sample', ROOT / 'consumer-fixture/app'):
        check_no_op(project, aapt, namespace)
    (lane / 'no-op.txt').write_text('Both release graphs and APKs passed WebKit and inspector isolation checks.\n')
    with ThreadPoolExecutor(max_workers=len(devices)) as pool:
        native_runs = [pool.submit(native_gate, device, lane) for device in devices]
        for run in native_runs:
            run.result()
    print(f'Running the minified Java consumer and sample debug smoke checks for {version}', flush=True)
    smoke(devices[1], ROOT, namespace, lane)
    return hashes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api24-serial', required=True)
    parser.add_argument('--api36-serial', required=True)
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    adb = sdk / 'platform-tools/adb'
    aapt = sdk / 'build-tools/35.0.0/aapt2'
    devices = (Emulator(adb, args.api24_serial, 24), Emulator(adb, args.api36_serial, 36))

    reports = ROOT / 'build/reports/webkit-compat'
    reports.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix='matrix-', dir=reports))
    config = properties()
    first_hashes = None
    for version in WEBKIT_VERSIONS:
        first_hashes = run_lane(version, evidence / version, devices, aapt, config, first_hashes)
    print(f'Both WebKit compatibility lanes passed. Evidence: {evidence}')


if __name__ == '__main__':
    main()
