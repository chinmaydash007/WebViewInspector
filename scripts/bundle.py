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

from publication_content import validate_classes, validate_dependencies, validate_document_archive, validate_text

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ('webview-inspector', 'webview-inspector-no-op')


def properties(root=ROOT):
    return dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))


def validate_publication(folder, artifact, version, *, namespace, maven_group, signed=False, legacy_webkit_pin=False):
    prefix = artifact + '-' + version
    required = [prefix + suffix for suffix in ('.aar', '.pom', '.module', '-sources.jar', '-javadoc.jar')]
    for name in required:
        if not (folder / name).is_file():
            raise ValueError('Missing publication file: ' + name)
    pom = ET.parse(folder / (prefix + '.pom'))
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    expected = {'groupId': maven_group, 'artifactId': artifact, 'version': version, 'packaging': 'aar'}
    for field, value in expected.items():
        if pom.findtext('m:' + field, namespaces=ns) != value:
            raise ValueError('Incorrect POM ' + field)
    for field in ['name', 'description', 'url', 'licenses/license/name', 'developers/developer/id', 'scm/url']:
        query = '/'.join('m:' + part for part in field.split('/'))
        if not pom.findtext(query, namespaces=ns):
            raise ValueError('Missing POM metadata: ' + field)
    metadata = json.loads((folder / (prefix + '.module')).read_text())
    validate_dependencies(pom.getroot(), metadata, artifact, legacy_webkit_pin=legacy_webkit_pin)
    validate_text((folder / (prefix + '.pom')).read_text(), namespace, maven_group, 'POM')
    validate_text(json.dumps(metadata), namespace, maven_group, 'Gradle metadata')
    with zipfile.ZipFile(folder / (prefix + '.aar')) as aar:
        validate_classes(aar.read('classes.jar'), namespace, no_op=artifact.endswith('no-op'))
        if any(name.startswith('libs/') and name.endswith('.jar') for name in aar.namelist()):
            raise ValueError('Unexpected embedded dependency JAR in AAR')
        if artifact.endswith('no-op') and any(name.startswith('res/') for name in aar.namelist()):
            raise ValueError('Implementation resources in no-op AAR')
        manifest = aar.read('AndroidManifest.xml').decode()
        manifest_root = ET.fromstring(manifest)
        expected_namespace = namespace + ('.noop' if artifact.endswith('no-op') else '')
        if manifest_root.get('package') != expected_namespace:
            raise ValueError('Unexpected AAR manifest namespace')
        uses_sdk = manifest_root.find('uses-sdk')
        if uses_sdk is None or uses_sdk.get('{http://schemas.android.com/apk/res/android}minSdkVersion') != '24':
            raise ValueError('Published AAR must declare minimum SDK 24')
        if '<activity' in manifest or '<provider' in manifest or 'INTERNET' in manifest:
            raise ValueError('Test/sample manifest leaked into AAR')
        for name in aar.namelist():
            if name.endswith(('.xml', '.txt', '.pro', '.properties')):
                validate_text(aar.read(name).decode('utf-8'), namespace, maven_group, name)
    for suffix in ['-sources.jar', '-javadoc.jar']:
        with zipfile.ZipFile(folder / (prefix + suffix)) as jar:
            if 'README.md' not in jar.namelist() or 'LICENSE' not in jar.namelist():
                raise ValueError('Public usage guide or license missing from ' + suffix)
            if suffix == '-javadoc.jar' and not any(name.endswith('.html') for name in jar.namelist()):
                raise ValueError('Documentation JAR is empty')
            # The original signed release predates the project-neutral NOTICE.
            validate_document_archive(
                jar, namespace, maven_group, sources=suffix == '-sources.jar',
                legacy_notice=signed and legacy_webkit_pin and version == '0.1.0',
            )
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
        required = validate_publication(
            folder, artifact, version,
            namespace=config['INSPECTOR_PACKAGE'], maven_group=config['GROUP'], signed=args.signed,
        )
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
