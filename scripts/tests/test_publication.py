import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import urllib.error
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import bundle
import release


GROUP = 'io.github.chinmaydash007'
LEGACY_PROPERTIES = {
    'VERSION_NAME': '0.1.0',
    'GROUP': GROUP,
    'INSPECTOR_PACKAGE': 'com.visit.webviewinspector',
}


class ReleaseTests(unittest.TestCase):
    def test_tagged_source_uses_the_tags_namespace_or_the_first_release_default(self):
        cases = (
            ('0.1.0', '', LEGACY_PROPERTIES['INSPECTOR_PACKAGE']),
            ('0.3.0', 'INSPECTOR_PACKAGE=io.github.chinmaydash007.webviewinspector\n',
             'io.github.chinmaydash007.webviewinspector'),
        )
        for version, namespace_property, namespace in cases:
            with self.subTest(version=version):
                source = f'VERSION_NAME={version}\nGROUP={GROUP}\n{namespace_property}'
                with patch.object(release.subprocess, 'check_output',
                                  side_effect=[source, 'tagged-commit\n', 'tagged-commit\n']) as git:
                    tagged_properties, commit = release.tagged_source(f'v{version}')
                self.assertEqual({
                    'VERSION_NAME': version, 'GROUP': GROUP, 'INSPECTOR_PACKAGE': namespace,
                }, tagged_properties)
                self.assertEqual('tagged-commit', commit)
                self.assertEqual(['git', 'show', f'refs/tags/v{version}:gradle.properties'], git.call_args_list[0].args[0])
                self.assertFalse(any('HEAD' in call.args[0] for call in git.call_args_list))

    def test_tagged_source_rejects_another_version(self):
        with patch.object(release.subprocess, 'check_output', side_effect=['VERSION_NAME=0.2.0\n', 'commit\n']):
            with self.assertRaisesRegex(ValueError, 'tag'):
                release.tagged_source('v0.1.0')

    def test_draft_is_found_when_release_by_tag_returns_404(self):
        missing = urllib.error.HTTPError('https://api.github.com/synthetic', 404, 'Not Found', None, None)
        other_page = [{'tag_name': 'v0.0.1', 'draft': False}] * 100
        draft = {'tag_name': 'v0.1.0', 'draft': True, 'id': 1}
        with patch.object(release, 'github', side_effect=[missing, other_page, [draft]]) as github:
            self.assertEqual(draft, release.release_for('v0.1.0', 'synthetic'))
            self.assertIn('page=2', github.call_args.args[0])

    def test_release_lookup_does_not_mask_authentication_errors(self):
        forbidden = urllib.error.HTTPError('https://api.github.com/synthetic', 403, 'Forbidden', None, None)
        with patch.object(release, 'github', side_effect=forbidden) as github:
            with self.assertRaises(urllib.error.HTTPError):
                release.release_for('v0.1.0', 'synthetic')
            self.assertEqual(1, github.call_count)

    def test_release_repository_accepts_the_original_regardless_of_visibility(self):
        for private in (True, False):
            with self.subTest(private=private):
                api_repo = {'id': release.REPOSITORY_ID, 'private': private}
                with patch.object(release, 'github', return_value=api_repo) as github:
                    release.require_release_repository('synthetic-token')
                github.assert_called_once_with('/repos/' + release.REPOSITORY, 'synthetic-token')

    def test_other_public_repository_is_rejected_before_restore_or_publish(self):
        api_repo = {'id': release.REPOSITORY_ID + 1, 'private': False}
        with patch.object(release, 'github', return_value=api_repo), tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'release'
            for operation in (release.restore, release.publish):
                with self.subTest(operation=operation.__name__):
                    with self.assertRaisesRegex(ValueError, 'original WebViewInspector'):
                        operation('v0.4.1', output, 'synthetic-token')
            self.assertFalse(output.exists())

    def test_version_tag_and_commit_must_match(self):
        with self.assertRaisesRegex(ValueError, 'tag'):
            release.validate_tag('v0.2.0', '0.1.0', 'commit')
        with patch.object(release.subprocess, 'check_output', return_value='different\n'):
            with self.assertRaisesRegex(ValueError, 'commit'):
                release.validate_tag('v0.1.0', '0.1.0', 'commit')

    def test_release_asset_is_never_replaced(self):
        asset = {'assets': [{'name': 'bundle.zip', 'url': 'https://api.github.com/asset'}]}
        with patch.object(release, 'fetch', return_value=b'original') as fetch:
            with self.assertRaisesRegex(ValueError, 'replace'):
                release.upload_asset(asset, 'bundle.zip', b'changed', 'synthetic-token')
            self.assertEqual(1, fetch.call_count)

    def test_cross_origin_redirect_strips_bearer(self):
        import urllib.request
        request = urllib.request.Request('https://api.github.com/assets/1', headers={'Authorization': 'Bearer synthetic'})
        redirected = release.StripCrossOriginAuthorization().redirect_request(request, None, 302, '', {}, 'https://release-assets.githubusercontent.com/file')
        self.assertIsNone(redirected.get_header('Authorization'))

    def test_failed_central_deployment_is_not_reported_published(self):
        with patch.object(release, 'status', return_value={'deploymentState': 'FAILED'}):
            with self.assertRaisesRegex(ValueError, 'validation failed'):
                release.wait_published('id', 'synthetic')

    def test_download_hash_mismatch_is_rejected(self):
        with patch.object(release, 'fetch', return_value=b'wrong'):
            with self.assertRaisesRegex(ValueError, 'differ'):
                release.verify_downloads({'files': {'synthetic.aar': 'bad-digest'}}, timeout=1)

    def test_saved_bundle_must_be_signed(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / 'publication-manifest.json').write_text(json.dumps({'signed': False, 'version': '0.1.0', 'source_commit': 'commit'}))
            with self.assertRaisesRegex(ValueError, 'unsigned'):
                release.validate_saved_bundle(folder, LEGACY_PROPERTIES, 'commit')

    def test_saved_bundle_selects_namespace_and_webkit_policy_from_the_tag(self):
        cases = (
            ('0.1.0', LEGACY_PROPERTIES['INSPECTOR_PACKAGE'], True),
            ('0.2.0', 'io.github.chinmaydash007.webviewinspector', True),
            ('0.3.0', 'io.github.chinmaydash007.webviewinspector', True),
            ('0.4.0', 'com.webviewinspector', False),
        )
        for version, namespace, legacy_webkit_pin in cases:
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                folder = Path(directory)
                archive_path = folder / 'bundle.zip'
                with zipfile.ZipFile(archive_path, 'w'):
                    pass
                manifest = {
                    'signed': True,
                    'version': version,
                    'source_commit': 'commit',
                    'bundle': archive_path.name,
                    'sha256': hashlib.sha256(archive_path.read_bytes()).hexdigest(),
                }
                (folder / 'publication-manifest.json').write_text(json.dumps(manifest))
                tagged_properties = {'VERSION_NAME': version, 'GROUP': GROUP, 'INSPECTOR_PACKAGE': namespace}
                with patch.object(release, 'validate_publication') as validate:
                    release.validate_saved_bundle(folder, tagged_properties, 'commit')
                    self.assertEqual(len(bundle.ARTIFACTS), validate.call_count)
                    for validation in validate.call_args_list:
                        self.assertEqual(
                            {'namespace': namespace, 'maven_group': GROUP, 'signed': True,
                             'legacy_webkit_pin': legacy_webkit_pin},
                            validation.kwargs,
                        )

    def test_retry_resumes_recorded_deployment_without_uploading(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            manifest = {'sha256': 'digest'}
            (folder / 'deployment.json').write_text(json.dumps({'deployment_id': 'existing-id', 'sha256': 'digest'}))
            for name in ['bundle.zip', 'publication-manifest.json', 'SHA256SUMS']:
                (folder / name).write_text('fixture')
            with contextlib.ExitStack() as stack:
                stack.enter_context(patch.object(release, 'require_release_repository'))
                stack.enter_context(patch.object(release, 'tagged_source', return_value=(LEGACY_PROPERTIES, 'commit')))
                stack.enter_context(patch.object(release, 'validate_saved_bundle', return_value=(manifest, folder / 'bundle.zip')))
                stack.enter_context(patch.object(release, 'release_for', return_value={'draft': False, 'id': 1, 'body': ''}))
                stack.enter_context(patch.object(release, 'upload_asset'))
                stack.enter_context(patch.object(release, 'github'))
                upload = stack.enter_context(patch.object(release, 'upload_bundle'))
                wait = stack.enter_context(patch.object(release, 'wait_published'))
                verify = stack.enter_context(patch.object(release, 'verify_downloads'))
                stack.enter_context(patch.dict(release.os.environ, {'CENTRAL_USERNAME': 'fixture', 'CENTRAL_PASSWORD': 'fixture'}))
                release.publish('v0.1.0', folder, 'synthetic')
                upload.assert_not_called()
                self.assertEqual('existing-id', wait.call_args.args[0])
                verify.assert_called_once_with(manifest)

    def test_existing_upload_intent_blocks_duplicate_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            manifest = {'sha256': 'digest'}
            (folder / 'upload-intent.json').write_text('{}')
            for name in ['bundle.zip', 'publication-manifest.json', 'SHA256SUMS']:
                (folder / name).write_text('fixture')
            with contextlib.ExitStack() as stack:
                stack.enter_context(patch.object(release, 'require_release_repository'))
                stack.enter_context(patch.object(release, 'tagged_source', return_value=(LEGACY_PROPERTIES, 'commit')))
                stack.enter_context(patch.object(release, 'validate_saved_bundle', return_value=(manifest, folder / 'bundle.zip')))
                stack.enter_context(patch.object(release, 'release_for', return_value={'draft': False}))
                stack.enter_context(patch.object(release, 'upload_asset'))
                upload = stack.enter_context(patch.object(release, 'upload_bundle'))
                stack.enter_context(patch.dict(release.os.environ, {'CENTRAL_USERNAME': 'fixture', 'CENTRAL_PASSWORD': 'fixture'}))
                with self.assertRaisesRegex(ValueError, 'already attempted'):
                    release.publish('v0.1.0', folder, 'synthetic')
                upload.assert_not_called()

    def test_publishing_a_draft_requires_explicit_opt_in(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.ExitStack() as stack:
            folder = Path(directory)
            stack.enter_context(patch.object(release, 'require_release_repository'))
            stack.enter_context(patch.object(release, 'tagged_source', return_value=(LEGACY_PROPERTIES, 'commit')))
            stack.enter_context(patch.object(release, 'validate_saved_bundle', return_value=({'sha256': 'digest'}, folder / 'bundle.zip')))
            stack.enter_context(patch.object(release, 'release_for', return_value={'draft': True}))
            change = stack.enter_context(patch.object(release, 'github'))
            upload = stack.enter_context(patch.object(release, 'upload_bundle'))
            stack.enter_context(patch.dict(release.os.environ, {'CENTRAL_USERNAME': 'fixture', 'CENTRAL_PASSWORD': 'fixture'}))
            with self.assertRaisesRegex(ValueError, 'GitHub Release'):
                release.publish('v0.1.0', folder, 'synthetic')
            change.assert_not_called()
            upload.assert_not_called()

    def test_opted_in_draft_is_published_with_intent_saved_before_upload(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.ExitStack() as stack:
            folder = Path(directory)
            for name in ['bundle.zip', 'publication-manifest.json', 'SHA256SUMS']:
                (folder / name).write_text('fixture')
            manifest = {'sha256': 'digest'}
            stack.enter_context(patch.object(release, 'require_release_repository'))
            stack.enter_context(patch.object(release, 'tagged_source', return_value=(LEGACY_PROPERTIES, 'tagged-commit')))
            validate = stack.enter_context(patch.object(release, 'validate_saved_bundle', return_value=(manifest, folder / 'bundle.zip')))
            stack.enter_context(patch.object(release, 'release_for', return_value={'draft': True, 'id': 1, 'body': ''}))
            stack.enter_context(patch.object(release, 'upload_asset'))
            change = stack.enter_context(patch.object(release, 'github', return_value={'draft': False, 'id': 1, 'body': ''}))
            def upload_after_intent(*args):
                self.assertTrue((folder / 'upload-intent.json').is_file())
                return 'new-deployment'
            upload = stack.enter_context(patch.object(release, 'upload_bundle', side_effect=upload_after_intent))
            wait = stack.enter_context(patch.object(release, 'wait_published'))
            stack.enter_context(patch.object(release, 'verify_downloads'))
            stack.enter_context(patch.dict(release.os.environ, {'CENTRAL_USERNAME': 'fixture', 'CENTRAL_PASSWORD': 'fixture'}))
            release.publish('v0.1.0', folder, 'synthetic', publish_draft=True)
            validate.assert_called_once_with(folder, LEGACY_PROPERTIES, 'tagged-commit')
            self.assertEqual({'draft': False}, change.call_args_list[0].args[3])
            upload.assert_called_once()
            self.assertEqual('new-deployment', wait.call_args.args[0])
            self.assertEqual('new-deployment', json.loads((folder / 'deployment.json').read_text())['deployment_id'])


if __name__ == '__main__':
    unittest.main()
