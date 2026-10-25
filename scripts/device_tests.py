#!/usr/bin/env python3
"""Run native compatibility suites on an explicitly selected emulator; restore its settings."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.visit.webviewinspector.test'
RUNNER = PACKAGE + '/androidx.test.runner.AndroidJUnitRunner'
PREFIX = 'com.visit.webviewinspector.'
NORMAL = [PREFIX + name for name in ['WebViewInspectorTest', 'NavigationHistoryTest', 'BottomSheetGestureTest', 'FocusedInspectorTest']]
FALLBACK = [PREFIX + 'FocusedInspectorTest'] + [PREFIX + 'NavigationHistoryTest#' + name for name in ['historyIsolationClearUnavailableSelectionAndPauseResume', 'unsupportedListenerFallbackUsesTheSameNativeHistoryAndCopyActions']]
NARROW = [PREFIX + 'WebViewInspectorTest#' + name for name in ['composeWithPlatformThemeSupportsSelectionExactCopyAndBack', 'xmlDuplicateIdsSelectionUnavailableAndCurrentUrl', 'settingsToggleTouchPassthroughRotationAndCleanup', 'dragClampsAndPositionSurvivesActivityRecreation']]
NARROW += [PREFIX + 'BottomSheetGestureTest', PREFIX + 'NavigationHistoryTest#expandedHistoryRetainsFocusAndScrollWhileNewNavigationsArrive', PREFIX + 'FocusedInspectorTest']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--api', type=int, choices=[24, 36], required=True)
    parser.add_argument('--skip-normal', action='store_true', help='Continue a previously completed normal suite with only the presentation matrix.')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        raise SystemExit('This script only operates an explicitly selected emulator.')
    adb = str(Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))) / 'platform-tools/adb')
    if not Path(adb).is_file():
        adb = 'adb'
    def command(*parts):
        return subprocess.check_output([adb, '-s', args.serial, *parts], text=True).strip()
    def shell(*parts):
        return command('shell', *parts)
    if int(shell('getprop', 'ro.build.version.sdk')) != args.api:
        raise SystemExit('Selected emulator API differs from the requested API.')
    output = ROOT / 'build/reports/devices' / ('api-' + str(args.api))
    output.mkdir(parents=True, exist_ok=True)
    settings = {key: shell('settings', 'get', 'global', key) for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']}
    settings.update({'font_scale': shell('settings', 'get', 'system', 'font_scale'), 'density': shell('wm', 'density')})
    if args.api >= 29:
        settings['night'] = shell('cmd', 'uimode', 'night').split()[-1]
    (output / 'original-settings.json').write_text(json.dumps(settings, indent=2))
    (output / 'provider.txt').write_text(shell('dumpsys', 'webviewupdate'))
    apk = ROOT / 'webview-inspector/build/outputs/apk/androidTest/debug/webview-inspector-debug-androidTest.apk'
    command('install', '--no-incremental', '-r', str(apk))
    def run(name, classes, count):
        print('Running', name, 'on', args.serial, flush=True)
        shell('rm', '-f', '/sdcard/Android/data/' + PACKAGE + '/files/*.png')
        result = shell('am', 'instrument', '-w', '-r', '-e', 'class', ','.join(classes), RUNNER)
        (output / (name + '.log')).write_text(result + '\n')
        if 'OK (' + str(count) + ' test' not in result or re.search(r'INSTRUMENTATION_STATUS_CODE: -[1234]\b|FAILURES!!!|INSTRUMENTATION_FAILED', result):
            raise RuntimeError(name + ' failed or skipped a required case. Inspect its retained log.')
        destination = output / (name + '-screenshots')
        destination.mkdir(exist_ok=True)
        subprocess.run([adb, '-s', args.serial, 'pull', '/sdcard/Android/data/' + PACKAGE + '/files/.', str(destination)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
            shell('settings', 'put', 'global', key, '0')
        if args.api == 36:
            run('navigation-prerequisite', [PREFIX + 'ReleasePrerequisitesTest'], 1)
        if not args.skip_normal:
            run('normal', NORMAL if args.api == 36 else FALLBACK, 21 if args.api == 36 else 8)
        if args.api == 36:
            width = int(re.findall(r'(\d+)x\d+', shell('wm', 'size'))[-1])
            shell('wm', 'density', str(round(width * 160 / 320)))
            shell('settings', 'put', 'system', 'font_scale', '1.6')
            shell('cmd', 'uimode', 'night', 'no')
            run('narrow-large-font-light', NARROW, 13)
            shell('cmd', 'uimode', 'night', 'yes')
            run('narrow-large-font-dark', [PREFIX + 'FocusedInspectorTest'], 6)
    finally:
        for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
            shell('settings', 'delete' if settings[key] == 'null' else 'put', 'global', key, *([] if settings[key] == 'null' else [settings[key]]))
        shell('settings', 'delete' if settings['font_scale'] == 'null' else 'put', 'system', 'font_scale', *([] if settings['font_scale'] == 'null' else [settings['font_scale']]))
        override = re.search(r'Override density: (\d+)', settings['density'])
        shell('wm', 'density', override.group(1) if override else 'reset')
        if 'night' in settings:
            shell('cmd', 'uimode', 'night', settings['night'])
        (output / 'restored.txt').write_text('Original density, font, night mode and animation settings restored.\n')


if __name__ == '__main__':
    main()
