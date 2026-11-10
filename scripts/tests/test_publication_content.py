"""Release-boundary regressions using synthetic archive and metadata inputs."""
import io
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bundle import properties
from publication_content import (
    FULL_DEPENDENCIES, NO_OP_DEPENDENCIES, validate_classes,
    validate_dependencies, validate_document_archive, validate_text,
)

NAMESPACE = properties()['INSPECTOR_PACKAGE']
PACKAGE = NAMESPACE.replace('.', '/')


def archive_bytes(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        for name, contents in entries.items():
            archive.writestr(name, contents)
    return output.getvalue()


def publication_metadata(dependencies, *, legacy_webkit_pin=False):
    xml_namespace = 'http://maven.apache.org/POM/4.0.0'
    root = ET.Element('{' + xml_namespace + '}project')
    elements = ET.SubElement(root, '{' + xml_namespace + '}dependencies')
    records = []
    for group, module in sorted(dependencies):
        dependency = ET.SubElement(elements, '{' + xml_namespace + '}dependency')
        version = '1.15.0' if group == 'androidx.webkit' else '1.0'
        for key, value in [('groupId', group), ('artifactId', module), ('version', version)]:
            ET.SubElement(dependency, '{' + xml_namespace + '}' + key).text = value
        version_metadata = {'requires': version}
        if group == 'androidx.webkit' and legacy_webkit_pin:
            version_metadata['strictly'] = version
        records.append({'group': group, 'module': module, 'version': version_metadata})
    return root, {'variants': [{
        'name': 'runtime',
        'attributes': {'org.gradle.usage': 'java-runtime'},
        'dependencies': records,
    }]}


class PublicationContentTest(unittest.TestCase):
    def test_full_archive_accepts_its_facade_and_internal_classes(self):
        data = archive_bytes({
            PACKAGE + '/WebViewInspector.class': b'facade',
            PACKAGE + '/internal/core/InspectorController.class': b'Lcom/google/android/material/dialog/MaterialAlertDialogBuilder;Ljava/nio/charset/Charset;',
            'META-INF/library_release.kotlin_module': b'metadata',
        })
        validate_classes(data, NAMESPACE, no_op=False)

    def test_foreign_runtime_class_is_rejected(self):
        data = archive_bytes({PACKAGE + '/WebViewInspector.class': b'facade', 'com/customer/app/Host.class': b'foreign'})
        with self.assertRaisesRegex(ValueError, 'Unexpected runtime class'):
            validate_classes(data, NAMESPACE, no_op=False)

    def test_foreign_reference_inside_an_approved_class_is_rejected(self):
        data = archive_bytes({PACKAGE + '/WebViewInspector.class': b'Lcom/customer/app/Host;'})
        with self.assertRaisesRegex(ValueError, 'Unexpected runtime reference'):
            validate_classes(data, NAMESPACE, no_op=False)

    def test_no_op_rejects_internal_implementation(self):
        data = archive_bytes({PACKAGE + '/WebViewInspector.class': b'facade', PACKAGE + '/internal/Observer.class': b'observer'})
        with self.assertRaisesRegex(ValueError, 'Unexpected runtime class'):
            validate_classes(data, NAMESPACE, no_op=True)

    def test_archive_without_the_facade_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'facade is missing'):
            validate_classes(archive_bytes({}), NAMESPACE, no_op=True)

    def test_platform_documentation_and_public_import_are_accepted(self):
        validate_text('import ' + NAMESPACE + '.WebViewInspector\ncom.google.android.material.dialog', NAMESPACE, 'README.md')

    def test_publisher_maven_group_is_accepted(self):
        validate_text(NAMESPACE.rsplit('.', 1)[0], NAMESPACE, 'POM groupId')

    def test_foreign_package_in_generated_documentation_is_rejected(self):
        with zipfile.ZipFile(io.BytesIO(archive_bytes({'index.html': 'import com.customer.app.Inspector'}))) as archive:
            with self.assertRaisesRegex(ValueError, 'Unexpected project identity'):
                validate_document_archive(archive, NAMESPACE, sources=False)

    def test_foreign_source_path_is_rejected(self):
        with zipfile.ZipFile(io.BytesIO(archive_bytes({'customer/Host.kt': 'class Host'}))) as archive:
            with self.assertRaisesRegex(ValueError, 'Unexpected published source'):
                validate_document_archive(archive, NAMESPACE, sources=True)

    def test_private_workspace_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Private workspace path'):
            validate_text('JDK: /Users/developer/private/jdk', NAMESPACE, 'README.md')

    def test_host_specific_notice_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Host-application extraction notice'):
            validate_text('Extracted from ExampleCompany Android implementation.', NAMESPACE, 'NOTICE')

    def test_reviewed_dependency_inventories_are_accepted(self):
        for artifact, dependencies in [('webview-inspector', FULL_DEPENDENCIES), ('webview-inspector-no-op', NO_OP_DEPENDENCIES)]:
            validate_dependencies(*publication_metadata(dependencies), artifact)

    def test_private_application_dependency_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected POM dependency'):
            validate_dependencies(*publication_metadata(FULL_DEPENDENCIES | {('org.customer', 'app-helper')}), 'webview-inspector')

    def test_no_op_rejects_the_full_dependency_inventory(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected POM dependency'):
            validate_dependencies(*publication_metadata(FULL_DEPENDENCIES), 'webview-inspector-no-op')

    def test_only_saved_legacy_publications_accept_the_original_pin(self):
        pom, metadata = publication_metadata(FULL_DEPENDENCIES, legacy_webkit_pin=True)
        validate_dependencies(pom, metadata, 'webview-inspector', legacy_webkit_pin=True)
        with self.assertRaisesRegex(ValueError, 'require as a minimum WebKit 1.15.0'):
            validate_dependencies(pom, metadata, 'webview-inspector')
        pom, metadata = publication_metadata(FULL_DEPENDENCIES)
        with self.assertRaisesRegex(ValueError, 'strictly pin WebKit 1.15.0'):
            validate_dependencies(pom, metadata, 'webview-inspector', legacy_webkit_pin=True)

    def test_webkit_minimum_must_be_present_exactly_once(self):
        pom, metadata = publication_metadata(FULL_DEPENDENCIES)
        dependencies = metadata['variants'][0]['dependencies']
        webkit = next(dependency for dependency in dependencies if dependency['group'] == 'androidx.webkit')
        dependencies.remove(webkit)
        with self.assertRaisesRegex(ValueError, 'require as a minimum WebKit 1.15.0'):
            validate_dependencies(pom, metadata, 'webview-inspector')
        dependencies.extend([webkit, webkit])
        with self.assertRaisesRegex(ValueError, 'require as a minimum WebKit 1.15.0'):
            validate_dependencies(pom, metadata, 'webview-inspector')

    def test_webkit_metadata_cannot_use_a_different_version_or_a_constraint(self):
        pom, metadata = publication_metadata(FULL_DEPENDENCIES)
        runtime = metadata['variants'][0]
        webkit = next(dependency for dependency in runtime['dependencies'] if dependency['group'] == 'androidx.webkit')
        webkit['version'] = {'requires': '1.14.0'}
        with self.assertRaisesRegex(ValueError, 'require as a minimum WebKit 1.15.0'):
            validate_dependencies(pom, metadata, 'webview-inspector')
        runtime['dependencies'].remove(webkit)
        runtime['dependencyConstraints'] = [webkit]
        with self.assertRaisesRegex(ValueError, 'direct Gradle runtime dependency'):
            validate_dependencies(pom, metadata, 'webview-inspector')


if __name__ == '__main__':
    unittest.main()
