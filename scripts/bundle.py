#!/usr/bin/env python3
"""Validate both Maven publications and assemble one reproducible Central upload bundle."""
import argparse
import hashlib
import io
import json
import shutil
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ('webview-inspector', 'webview-inspector-no-op')


def properties(root=ROOT):
    return dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))


def validate_publication(folder, artifact, version, signed=False):
    prefix = artifact + '-' + version
    required = [prefix + suffix for suffix in ('.aar', '.pom', '.module', '-sources.jar', '-javadoc.jar')]
    for name in required:
        if not (folder / name).is_file():
            raise ValueError('Missing publication file: ' + name)
    pom = ET.parse(folder / (prefix + '.pom'))
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    expected = {'groupId': 'io.github.chinmaydash007', 'artifactId': artifact, 'version': version, 'packaging': 'aar'}
    for field, value in expected.items():
        if pom.findtext('m:' + field, namespaces=ns) != value:
            raise ValueError('Incorrect POM ' + field)
    for field in ['name', 'description', 'url', 'licenses/license/name', 'developers/developer/id', 'scm/url']:
        query = '/'.join('m:' + part for part in field.split('/'))
        if not pom.findtext(query, namespaces=ns):
            raise ValueError('Missing POM metadata: ' + field)
    metadata = (folder / (prefix + '.pom')).read_text() + (folder / (prefix + '.module')).read_text()
    for forbidden in ['com.getvisitapp', 'com.dependencies.buildsrc', 'androidx.compose', 'androidx.test', 'espresso', 'sample', 'test-host']:
        if forbidden in metadata:
            raise ValueError('Unshipped dependency in publication metadata: ' + forbidden)
    if artifact.endswith('no-op'):
        for forbidden in ['androidx.webkit', 'com.google.android.material', 'webview-inspector</artifactId>']:
            if forbidden in metadata:
                raise ValueError('Implementation dependency in no-op publication: ' + forbidden)
    with zipfile.ZipFile(folder / (prefix + '.aar')) as aar:
        classes = zipfile.ZipFile(io.BytesIO(aar.read('classes.jar')))
        names = classes.namelist()
        if artifact.endswith('no-op') and any('/internal/' in name for name in names):
            raise ValueError('Implementation class in no-op AAR')
        if any('InspectorHost' in name or 'sample/' in name or '/test/' in name for name in names):
            raise ValueError('Test/sample class leaked into AAR')
        manifest = aar.read('AndroidManifest.xml').decode()
        if '<activity' in manifest or '<provider' in manifest or 'INTERNET' in manifest:
            raise ValueError('Test/sample manifest leaked into AAR')
    for suffix in ['-sources.jar', '-javadoc.jar']:
        with zipfile.ZipFile(folder / (prefix + suffix)) as jar:
            if 'README.md' not in jar.namelist() or 'LICENSE' not in jar.namelist():
                raise ValueError('Public usage guide or license missing from ' + suffix)
            if suffix == '-javadoc.jar' and not any(name.endswith('.html') for name in jar.namelist()):
                raise ValueError('Documentation JAR is empty')
    if signed:
        for name in required:
            signature = folder / (name + '.asc')
            if not signature.is_file():
                raise ValueError('Unsigned publication: ' + name)
            if shutil.which('gpg'):
                subprocess.run(['gpg', '--batch', '--verify', str(signature), str(folder / name)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            else:
                from pgpy import PGPKey, PGPSignature
                public, _ = PGPKey.from_file(str(ROOT / 'signing-public.asc'))
                if not public.verify((folder / name).read_bytes(), PGPSignature.from_file(str(signature))):
                    raise ValueError('Invalid signature: ' + name)
    return required


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--signed', action='store_true')
    args = parser.parse_args()
    config = properties()
    version = config['VERSION_NAME']
    staging = ROOT / 'build/validation-repository' / config['GROUP'].replace('.', '/')
    output = ROOT / 'build/release'
    output.mkdir(parents=True, exist_ok=True)
    files = {}
    for artifact in ARTIFACTS:
        folder = staging / artifact / version
        required = validate_publication(folder, artifact, version, signed=args.signed)
        for name in required:
            file = folder / name
            relative = '/'.join([config['GROUP'].replace('.', '/'), artifact, version, name])
            data = file.read_bytes()
            files[relative] = data
            for algorithm in ['md5', 'sha1', 'sha256', 'sha512']:
                files[relative + '.' + algorithm] = hashlib.new(algorithm, data).hexdigest().encode()
            if args.signed:
                files[relative + '.asc'] = (folder / (name + '.asc')).read_bytes()
    bundle = output / ('webview-inspector-' + version + '-central.zip')
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    commit_result = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True, capture_output=True)
    commit = commit_result.stdout.strip() if commit_result.returncode == 0 else ''
    digest = hashlib.sha256(bundle.read_bytes()).hexdigest()
    manifest = {'version': version, 'source_commit': commit, 'signed': args.signed, 'bundle': bundle.name, 'sha256': digest,
                'files': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items()) if not name.endswith(('.md5', '.sha1', '.sha256', '.sha512', '.asc'))}}
    (output / 'publication-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(digest + '  ' + bundle.name + '\n')
    print('Validated both publications. Bundle:', bundle)
    print('Signed:', args.signed)


if __name__ == '__main__':
    main()
