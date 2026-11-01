#!/usr/bin/env python3
"""Compare the declared Java static facade contract from each built release AAR."""
import re
import subprocess
import tempfile
from pathlib import Path
import zipfile

from bundle import properties

root = Path(__file__).resolve().parents[1]
namespace = properties()['INSPECTOR_PACKAGE']
signatures = []
bytecode = []
for module in ['webview-inspector', 'webview-inspector-no-op']:
    aar = root / module / 'build/outputs/aar' / (module + '-release.aar')
    with tempfile.TemporaryDirectory() as directory:
        jar = Path(directory) / 'classes.jar'
        with zipfile.ZipFile(aar) as archive:
            jar.write_bytes(archive.read('classes.jar'))
        output = subprocess.check_output(['javap', '-classpath', str(jar), namespace + '.WebViewInspector'], text=True)
        signatures.append(sorted(line.strip() for line in output.splitlines() if 'public static final' in line and '(' in line))
        bytecode.append(subprocess.check_output(['javap', '-c', '-classpath', str(jar), namespace + '.WebViewInspector'], text=True))
if len(signatures[0]) != 7 or signatures[0] != signatures[1]:
    raise SystemExit('Facade contract differs: ' + repr(signatures))
expected_loads = [signature for signature in signatures[0] if ' loadUrl(' in signature]
if len(expected_loads) != 2:
    raise SystemExit('Both synchronous loadUrl overloads must be exposed.')
print('PASS: full/no-op facades expose the same seven Java static methods, including both load helpers.')

# No-op helpers must contain only argument checks and direct WebView delegation.
load_bodies = re.findall(r'public static final void loadUrl\([^\n]+\);\n(.*?)(?=\n  (?:public|private|static)|\n})', bytecode[1], re.S)
if len(load_bodies) != 2:
    raise SystemExit('Missing no-op helper bytecode')
for body in load_bodies:
    if body.count('android/webkit/WebView.loadUrl:') != 1:
        raise SystemExit('No-op must delegate the original WebView overload exactly once')
    calls = re.findall(r'// Method ([^\n]+)', body)
    if any(not call.startswith(('kotlin/jvm/internal/Intrinsics.checkNotNullParameter:', 'android/webkit/WebView.loadUrl:')) for call in calls):
        raise SystemExit('Unexpected no-op load helper behavior: ' + repr(calls))
print('PASS: both no-op helpers contain only argument checks and single direct WebView delegation.')
