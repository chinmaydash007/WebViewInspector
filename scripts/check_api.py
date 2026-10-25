#!/usr/bin/env python3
"""Compare the declared Java static facade contract from each built release AAR."""
import re
import subprocess
import tempfile
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
signatures = []
for module in ['webview-inspector', 'webview-inspector-no-op']:
    aar = root / module / 'build/outputs/aar' / (module + '-release.aar')
    with tempfile.TemporaryDirectory() as directory:
        jar = Path(directory) / 'classes.jar'
        with zipfile.ZipFile(aar) as archive:
            jar.write_bytes(archive.read('classes.jar'))
        output = subprocess.check_output(['javap', '-classpath', str(jar), 'com.visit.webviewinspector.WebViewInspector'], text=True)
        signatures.append(sorted(line.strip() for line in output.splitlines() if 'public static final' in line and '(' in line))
if len(signatures[0]) != 5 or signatures[0] != signatures[1]:
    raise SystemExit('Facade contract differs: ' + repr(signatures))
print('PASS: full/no-op facades expose the same five Java static methods.')
