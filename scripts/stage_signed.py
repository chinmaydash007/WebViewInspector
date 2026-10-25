#!/usr/bin/env python3
"""Stage signed artifacts. Local credentials live outside Git; CI uses environment secrets."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
secure = Path.home() / '.config/webview-inspector-publishing'
for property_name, filename in [('signingInMemoryKey', 'signing-private.asc'), ('signingInMemoryKeyPassword', 'signing-password')]:
    variable = 'ORG_GRADLE_PROJECT_' + property_name
    if not environment.get(variable) and (secure / filename).exists():
        environment[variable] = (secure / filename).read_text()
if not environment.get('ORG_GRADLE_PROJECT_signingInMemoryKey'):
    raise SystemExit('Missing signing key. Configure GitHub secrets or the protected local signing directory.')
subprocess.run([str(root / 'gradlew'), '--no-daemon', 'stageRelease'], cwd=root, env=environment, check=True)
