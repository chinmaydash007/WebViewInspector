"""Validate the independent identity and dependency boundary of public release files.

These checks run before signing/uploading and again when restoring a release bundle.
They inspect archive contents rather than trusting the top-level artifact filename.
"""
import io
import re
import zipfile


FULL_DEPENDENCIES = {
    ('org.jetbrains.kotlin', 'kotlin-stdlib'),
    ('androidx.core', 'core-ktx'),
    ('com.google.android.material', 'material'),
    ('androidx.webkit', 'webkit'),
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


def validate_text(text, namespace, location):
    """Reject foreign project identities and private workspace paths in public text."""
    publisher = namespace.rsplit('.', 1)[0]
    permitted = (publisher,) + DOCUMENTATION_IDENTITIES
    for match in QUALIFIED_IDENTITY.finditer(text):
        identity = match.group()
        if not any(identity == prefix or identity.startswith(prefix + '.') for prefix in permitted):
            raise ValueError('Unexpected project identity in ' + location + ': ' + identity)
    if re.search(r'/Users/|[A-Za-z]:\\Users\\|/home/[^/\s]+/', text):
        raise ValueError('Private workspace path in ' + location)
    if re.search(r'extracted from .+ implementation', text, re.IGNORECASE):
        raise ValueError('Host-application extraction notice in ' + location)


def validate_dependencies(pom, metadata, artifact):
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
    for dependency in dependencies:
        if dependency.findtext('m:groupId', namespaces=xml_namespace) == 'androidx.webkit':
            if dependency.findtext('m:version', namespaces=xml_namespace) != '1.15.0':
                raise ValueError('WebKit publication version must remain 1.15.0')
    for variant in metadata['variants']:
        for dependency in variant.get('dependencies', []) + variant.get('dependencyConstraints', []):
            identity = (dependency['group'], dependency['module'])
            if identity not in expected:
                raise ValueError('Unexpected Gradle dependency: ' + repr(identity))
            if identity == ('androidx.webkit', 'webkit') and dependency.get('version', {}).get('strictly') != '1.15.0':
                raise ValueError('Gradle metadata must strictly constrain WebKit to 1.15.0')


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


def validate_document_archive(archive, namespace, sources):
    """Inspect source paths, package references, and generated documentation content."""
    package = namespace.replace('.', '/')
    for name in archive.namelist():
        validate_text(name.replace('/', '.'), namespace, 'archive entry ' + name)
        if sources and name.endswith(('.kt', '.java')):
            if not (name.startswith(package + '/internal/') or name == package + '/WebViewInspector.kt'):
                raise ValueError('Unexpected published source: ' + name)
        if name.endswith(TEXT_EXTENSIONS) or name in ('NOTICE', 'LICENSE'):
            validate_text(archive.read(name).decode('utf-8'), namespace, name)
