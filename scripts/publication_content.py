"""Validate the independent identity and dependency boundary of public release files.

These checks run before signing/uploading and again when restoring a release bundle.
They inspect archive contents rather than trusting the top-level artifact filename.
"""
import hashlib
import io
import re
import zipfile


WEBKIT = ('androidx.webkit', 'webkit')
WEBKIT_MINIMUM = '1.15.0'
FULL_DEPENDENCIES = {
    ('org.jetbrains.kotlin', 'kotlin-stdlib'),
    ('androidx.core', 'core-ktx'),
    ('com.google.android.material', 'material'),
    WEBKIT,
}
NO_OP_DEPENDENCIES = {('org.jetbrains.kotlin', 'kotlin-stdlib')}
DOCUMENTATION_IDENTITIES = (
    'org.jetbrains', 'org.gradle', 'org.intellij', 'org.xmlpull', 'org.json',
    'org.w3c', 'org.xml', 'com.google.android.material', 'com.android',
    'com.vanniktech',
)
QUALIFIED_IDENTITY = re.compile(r'(?<![A-Za-z0-9_$])(?:com|io|org)(?:\.[A-Za-z_][A-Za-z0-9_$]*){2,}')
RUNTIME_REFERENCE = re.compile(rb'(?<![a-z0-9_$])(?:com|io|org)(?:/[A-Za-z_][A-Za-z0-9_$]*){2,}')
TEXT_EXTENSIONS = ('.kt', '.java', '.md', '.html', '.xml', '.json', '.txt', '.properties')
V0_1_0_NOTICE_SHA256 = 'd23982d223bec6296ca9cdc09c5fca84ebb0c9da513456d826bed4663716200b'


def validate_text(text, namespace, maven_group, location, *, legacy_notice=False):
    """Reject foreign project identities and private workspace paths in public text."""
    permitted = (namespace,) + DOCUMENTATION_IDENTITIES
    for match in QUALIFIED_IDENTITY.finditer(text):
        identity = match.group()
        if identity != maven_group and not any(
            identity == prefix or identity.startswith(prefix + '.') for prefix in permitted
        ):
            raise ValueError('Unexpected project identity in ' + location + ': ' + identity)
    if re.search(r'/Users/|[A-Za-z]:\\Users\\|/home/[^/\s]+/', text):
        raise ValueError('Private workspace path in ' + location)
    if re.search(r'extracted from .+ implementation', text, re.IGNORECASE):
        if not (
            legacy_notice and location == 'NOTICE'
            and hashlib.sha256(text.encode('utf-8')).hexdigest() == V0_1_0_NOTICE_SHA256
        ):
            raise ValueError('Host-application extraction notice in ' + location)


def validate_dependencies(pom, metadata, artifact, *, legacy_webkit_pin=False):
    """Require the reviewed direct dependencies in both Maven and Gradle metadata."""
    xml_namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    dependencies = pom.findall('m:dependencies/m:dependency', xml_namespace)
    identities = {
        (item.findtext('m:groupId', namespaces=xml_namespace), item.findtext('m:artifactId', namespaces=xml_namespace))
        for item in dependencies
    }
    expected = NO_OP_DEPENDENCIES if artifact.endswith('no-op') else FULL_DEPENDENCIES
    if identities != expected:
        raise ValueError('Unexpected POM dependency inventory: ' + repr(sorted(identities)))
    if WEBKIT in expected:
        webkit_pom_versions = [
            dependency.findtext('m:version', namespaces=xml_namespace)
            for dependency in dependencies
            if (
                dependency.findtext('m:groupId', namespaces=xml_namespace),
                dependency.findtext('m:artifactId', namespaces=xml_namespace),
            ) == WEBKIT
        ]
        if webkit_pom_versions != [WEBKIT_MINIMUM]:
            raise ValueError('POM must declare WebKit 1.15.0 exactly once')

    webkit_metadata_versions = []
    for variant in metadata['variants']:
        for field in ('dependencies', 'dependencyConstraints'):
            for dependency in variant.get(field, []):
                identity = (dependency['group'], dependency['module'])
                if identity not in expected:
                    raise ValueError('Unexpected Gradle dependency: ' + repr(identity))
                if identity == WEBKIT:
                    usage = variant.get('attributes', {}).get('org.gradle.usage')
                    if field != 'dependencies' or usage != 'java-runtime':
                        raise ValueError('WebKit must be a direct Gradle runtime dependency')
                    webkit_metadata_versions.append(dependency.get('version'))
    if WEBKIT in expected:
        webkit_version = {'requires': WEBKIT_MINIMUM}
        if legacy_webkit_pin:
            webkit_version['strictly'] = WEBKIT_MINIMUM
        if webkit_metadata_versions != [webkit_version]:
            policy = 'strictly pin' if legacy_webkit_pin else 'require as a minimum'
            raise ValueError(f'Gradle runtime metadata must {policy} WebKit 1.15.0 exactly once')


def validate_classes(data, namespace, no_op):
    """Permit only this facade and, for the full artifact, its internal implementation."""
    package = namespace.replace('.', '/')
    facade = package + '/WebViewInspector'
    permitted_references = tuple(prefix.replace('.', '/') for prefix in (namespace,) + DOCUMENTATION_IDENTITIES)
    with zipfile.ZipFile(io.BytesIO(data)) as classes:
        for name in classes.namelist():
            if not name.endswith('.class'):
                if name.startswith('META-INF/') or name.endswith('/'):
                    continue
                raise ValueError('Unexpected runtime archive entry: ' + name)
            is_facade = name == facade + '.class' or name.startswith(facade + '$')
            is_internal = name.startswith(package + '/internal/')
            if not is_facade and not (is_internal and not no_op):
                raise ValueError('Unexpected runtime class: ' + name)
            for match in RUNTIME_REFERENCE.finditer(classes.read(name)):
                reference = match.group().decode('ascii')
                if not any(reference == prefix or reference.startswith(prefix + '/') for prefix in permitted_references):
                    raise ValueError('Unexpected runtime reference in ' + name + ': ' + reference)
        if facade + '.class' not in classes.namelist():
            raise ValueError('Published facade is missing')


def validate_document_archive(archive, namespace, maven_group, sources, *, legacy_notice=False):
    """Inspect source paths, package references, and generated documentation content."""
    package = namespace.replace('.', '/')
    for name in archive.namelist():
        validate_text(name.replace('/', '.'), namespace, maven_group, 'archive entry ' + name)
        if sources and name.endswith(('.kt', '.java')):
            if not (name.startswith(package + '/internal/') or name == package + '/WebViewInspector.kt'):
                raise ValueError('Unexpected published source: ' + name)
        if name.endswith(TEXT_EXTENSIONS) or name in ('NOTICE', 'LICENSE'):
            validate_text(archive.read(name).decode('utf-8'), namespace, maven_group, name, legacy_notice=legacy_notice)
