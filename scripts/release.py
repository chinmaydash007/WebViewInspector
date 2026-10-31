#!/usr/bin/env python3
"""Private GitHub release assets -> one signed, public Maven Central deployment.

No publishing on imports, ordinary builds or restore. Publication requires the publish command,
a matching tag/commit, valid signed bundle and explicit Central credentials.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

from bundle import ARTIFACTS, ROOT, properties, validate_publication

REPOSITORY = os.environ.get('GITHUB_REPOSITORY', 'chinmaydash007/WebViewInspector')
CENTRAL = 'https://central.sonatype.com/api/v1/publisher'
MAVEN = 'https://repo.maven.apache.org/maven2/'


class StripCrossOriginAuthorization(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(request, fp, code, msg, headers, newurl)
        if redirected and urllib.parse.urlsplit(request.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            redirected.remove_header('Authorization')
        return redirected


OPENER = urllib.request.build_opener(StripCrossOriginAuthorization())


def fetch(url, data=None, method='GET', token=None, content_type=None, accept='application/json'):
    headers = {'Accept': accept, 'User-Agent': 'webview-inspector-release'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    with OPENER.open(request, timeout=120) as response:
        return response.read()


def github(path, token, method='GET', data=None):
    body = json.dumps(data).encode() if data is not None else None
    response = fetch('https://api.github.com' + path, body, method, token, 'application/json', accept='application/vnd.github+json')
    return json.loads(response) if response else None


def validate_tag(tag, version, expected_commit):
    if tag != 'v' + version or version.endswith('-SNAPSHOT'):
        raise ValueError('Release tag and VERSION_NAME must match a non-snapshot version.')
    actual = subprocess.check_output(['git', 'rev-parse', tag + '^{commit}'], cwd=ROOT, text=True).strip()
    if actual != expected_commit:
        raise ValueError('Release tag does not identify the built commit.')


def tagged_source(tag):
    """Resolve artifact identity from its immutable tag, independently of publisher HEAD."""
    if not tag.startswith('v') or any(character in tag for character in ['/', ':', '\\', '\n']):
        raise ValueError('Expected a version tag such as v0.1.0.')
    reference = 'refs/tags/' + tag
    source = subprocess.check_output(['git', 'show', reference + ':gradle.properties'], cwd=ROOT, text=True)
    tagged_properties = dict(line.split('=', 1) for line in source.splitlines() if '=' in line and not line.startswith('#'))
    version = tagged_properties['VERSION_NAME'].strip()
    commit = subprocess.check_output(['git', 'rev-parse', reference + '^{commit}'], cwd=ROOT, text=True).strip()
    validate_tag(tag, version, commit)
    return version, commit


def require_private_repository(token):
    repo = github('/repos/' + REPOSITORY, token)
    if repo.get('private') is not True:
        raise ValueError('Release requires the GitHub repository to remain PRIVATE.')


def release_for(tag, token):
    """Find published releases by tag and drafts through the authenticated release list."""
    try:
        return github('/repos/' + REPOSITORY + '/releases/tags/' + urllib.parse.quote(tag), token)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        page = 1
        while True:
            releases = github('/repos/' + REPOSITORY + '/releases?per_page=100&page=' + str(page), token)
            matching = [release for release in releases if release['tag_name'] == tag and release['draft']]
            if matching:
                return matching[0]
            if len(releases) < 100:
                raise error
            page += 1


def upload_asset(release, name, data, token):
    existing = next((asset for asset in release['assets'] if asset['name'] == name), None)
    if existing:
        previous = fetch(existing['url'], token=token, accept='application/octet-stream')
        if previous != data:
            raise ValueError('Refusing to replace released asset: ' + name)
        return
    url = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(name)
    fetch(url, data, 'POST', token, 'application/octet-stream')


def restore(tag, output, token):
    require_private_repository(token)
    release = release_for(tag, token)
    output.mkdir(parents=True, exist_ok=True)
    for asset in release['assets']:
        name = asset['name']
        if name in ['publication-manifest.json', 'deployment.json', 'upload-intent.json', 'SHA256SUMS'] or name.endswith('-central.zip'):
            if Path(name).name != name:
                raise ValueError('Unsafe release asset name')
            (output / name).write_bytes(fetch(asset['url'], token=token, accept='application/octet-stream'))


def validate_saved_bundle(output, version, commit):
    manifest = json.loads((output / 'publication-manifest.json').read_text())
    if not manifest.get('signed') or manifest['version'] != version or manifest['source_commit'] != commit:
        raise ValueError('Bundle is unsigned or does not belong to this tagged commit.')
    bundle = output / manifest['bundle']
    if bundle.name != manifest['bundle'] or hashlib.sha256(bundle.read_bytes()).hexdigest() != manifest['sha256']:
        raise ValueError('Release bundle checksum mismatch')
    import tempfile
    with tempfile.TemporaryDirectory() as temporary:
        parent = Path(temporary)
        with zipfile.ZipFile(bundle) as archive:
            for info in archive.infolist():
                destination = (parent / info.filename).resolve()
                if not destination.is_relative_to(parent.resolve()):
                    raise ValueError('Unsafe publication path')
            archive.extractall(parent)
        for artifact in ARTIFACTS:
            validate_publication(parent / 'io/github/chinmaydash007' / artifact / version, artifact, version, signed=True)
    return manifest, bundle


def status(deployment_id, credential):
    raw = fetch(CENTRAL + '/status?' + urllib.parse.urlencode({'id': deployment_id}), b'', 'POST', credential)
    return json.loads(raw)


def wait_published(deployment_id, credential, timeout=3600):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        state = status(deployment_id, credential)
        current = state['deploymentState']
        if current != last:
            print('Central deployment:', deployment_id, current, flush=True)
            last = current
        if current == 'PUBLISHED':
            return
        if current == 'FAILED':
            raise ValueError('Central validation failed; inspect the deployment in the Portal. Do not replace the released tag.')
        time.sleep(15)
    raise TimeoutError('Central is still processing. Resume the same deployment; do not upload again.')


def upload_bundle(bundle, version, credential):
    boundary = 'WebViewInspectorCentralUpload'
    body = (('--' + boundary + '\r\nContent-Disposition: form-data; name="bundle"; filename="' + bundle.name + '"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()
            + bundle.read_bytes() + ('\r\n--' + boundary + '--\r\n').encode())
    query = urllib.parse.urlencode({'name': 'webview-inspector-' + version, 'publishingType': 'AUTOMATIC'})
    return fetch(CENTRAL + '/upload?' + query, body, 'POST', credential, 'multipart/form-data; boundary=' + boundary, accept='text/plain').decode().strip()


def verify_downloads(manifest, timeout=1800):
    deadline = time.monotonic() + timeout
    pending = dict(manifest['files'])
    while pending and time.monotonic() < deadline:
        for path, digest in list(pending.items()):
            try:
                data = fetch(MAVEN + path, accept='application/octet-stream')
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    continue
                raise
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('Published bytes differ from release bundle: ' + path)
            del pending[path]
        if pending:
            time.sleep(15)
    if pending:
        raise TimeoutError('Published files are not all downloadable yet. Retry verification without re-uploading.')
    print('All public Central publication files match the signed release bundle.')


def publish(tag, output, token, *, publish_draft=False):
    require_private_repository(token)
    version, commit = tagged_source(tag)
    manifest, bundle = validate_saved_bundle(output, version, commit)
    username = os.environ.get('CENTRAL_USERNAME')
    password = os.environ.get('CENTRAL_PASSWORD')
    if not username or not password:
        raise ValueError('Central namespace/token setup is pending. No upload was attempted.')
    credential = base64.b64encode((username + ':' + password).encode()).decode()
    release = release_for(tag, token)
    if release['draft'] and not publish_draft:
        raise ValueError('Publish the private GitHub Release to trigger Central publication.')
    for name in [bundle.name, 'publication-manifest.json', 'SHA256SUMS']:
        upload_asset(release, name, (output / name).read_bytes(), token)
    deployment_file = output / 'deployment.json'
    intent = output / 'upload-intent.json'
    if intent.exists() and not deployment_file.exists():
        raise ValueError('An upload was already attempted without a saved deployment ID. Inspect Central and recover deployment.json before retrying.')
    if deployment_file.exists():
        deployment = json.loads(deployment_file.read_text())
        if deployment['sha256'] != manifest['sha256']:
            raise ValueError('Existing deployment refers to another bundle.')
    if release['draft']:
        # The dedicated workflow uses GITHUB_TOKEN, so this does not enqueue another release workflow.
        release = github('/repos/' + REPOSITORY + '/releases/' + str(release['id']), token, 'PATCH', {'draft': False})
        require_private_repository(token)
    if not deployment_file.exists():
        # Persist intent before POST. A lost response must never cause an automatic duplicate upload.
        intent.write_text(json.dumps({'sha256': manifest['sha256'], 'version': version}, indent=2) + '\n')
        upload_asset(release, 'upload-intent.json', intent.read_bytes(), token)
        deployment_id = upload_bundle(bundle, version, credential)
        deployment = {'deployment_id': deployment_id, 'sha256': manifest['sha256'], 'version': version}
        deployment_file.write_text(json.dumps(deployment, indent=2) + '\n')
        upload_asset(release, 'deployment.json', deployment_file.read_bytes(), token)
    wait_published(deployment['deployment_id'], credential)
    verify_downloads(manifest)
    note = '\n\nMaven Central: publication and exact-file download verification passed for both artifacts at `' + version + '`.'
    if note.strip() not in (release.get('body') or ''):
        github('/repos/' + REPOSITORY + '/releases/' + str(release['id']), token, 'PATCH', {'body': (release.get('body') or '') + note})
    require_private_repository(token)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['restore', 'publish'])
    parser.add_argument('--tag', required=True)
    parser.add_argument('--publish-draft', action='store_true', help='Publish the existing private draft after validation; intended for the prepared-release workflow.')
    args = parser.parse_args()
    token = os.environ.get('GITHUB_TOKEN')
    if not token:
        raise SystemExit('GITHUB_TOKEN is required; never put it on the command line.')
    output = ROOT / 'build/release'
    try:
        if args.command == 'restore':
            restore(args.tag, output, token)
        else:
            publish(args.tag, output, token, publish_draft=args.publish_draft)
    except Exception as error:
        # Do not echo response bodies, credentials or presigned asset URLs.
        raise SystemExit(type(error).__name__ + ': ' + str(error).split('https://')[0])


if __name__ == '__main__':
    main()
