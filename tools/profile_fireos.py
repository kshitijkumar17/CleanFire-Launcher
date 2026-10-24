#!/usr/bin/env python3
"""Repeatable physical-device benchmarks. No settings/grants; restarts only Material Home.
Captures are deliberately separate from benchmarks. Raw evidence may contain app inventory.
"""
import argparse
import csv
import json
from pathlib import Path
import re
import subprocess
import time

PKG = 'dev.karat.materialhome'
COMPONENT = PKG + '/com.rampey.materialtv.HomeActivity'

def percentile(values, fraction):
    values = sorted(values)
    return round(values[min(len(values) - 1, int((len(values) - 1) * fraction))], 3) if values else None

def summarize(raw):
    result = {}
    for name, pattern in {
        'frames': r'Total frames rendered: (\d+)',
        'janky_pct': r'Janky frames: \d+ \(([\d.]+)%\)',
        'p50_ms': r'50th percentile: (\d+)ms',
        'p95_ms': r'95th percentile: (\d+)ms',
        'deadline_misses': r'Number Frame deadline missed: (\d+)',
        'missed_vsync': r'Number Missed Vsync: (\d+)'
    }.items():
        match = re.search(pattern, raw)
        result[name] = float(match[1]) if match else None
    chunks = raw.split('---PROFILEDATA---')
    rows = []
    for chunk in chunks[1::2]:
        rows.extend(r for r in csv.DictReader(chunk.strip().splitlines()) if r.get('Flags') == '0')
    # Framestats is a bounded tail, not the whole aggregate run. Do not combine their denominators.
    result['tail_frames'] = len(rows)
    for name, begin, end in [
        ('ui', 'HandleInputStart', 'SyncQueued'),
        ('sync_queue', 'SyncQueued', 'SyncStart'),
        ('render_to_swap', 'IssueDrawCommandsStart', 'SwapBuffers'),
        ('completion', 'IntendedVsync', 'FrameCompleted')
    ]:
        durations = [(int(r[end]) - int(r[begin])) / 1e6 for r in rows if r.get(begin) and r.get(end)]
        result[name + '_p50_ms'] = percentile(durations, .5)
        result[name + '_p95_ms'] = percentile(durations, .95)
    for name in ['DequeueBufferDuration', 'QueueBufferDuration']:
        durations = [int(r[name]) / 1e6 for r in rows if r.get(name)]
        result[name + '_p50_ms'] = percentile(durations, .5)
    gaps = [(int(b['IntendedVsync']) - int(a['IntendedVsync'])) / 1e6 for a, b in zip(rows, rows[1:])]
    result['vsync_gap_p50_ms'] = percentile(gaps, .5)
    result['vsync_gap_p95_ms'] = percentile(gaps, .95)
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--runs', type=int, default=2)
    parser.add_argument('--scenarios', nargs='+', default=['idle', 'dock', 'navigation', 'grid', 'app-return'])
    parser.add_argument('--streaming-component', help='Installed streaming activity for the app-return scenario')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    base = ['adb', '-s', args.serial, 'shell']
    def shell(*items):
        return subprocess.check_output(base + list(items), text=True, timeout=45)
    def key(name, delay=.12):
        shell('input', 'keyevent', 'KEYCODE_' + name)
        time.sleep(delay)
    def stats_cpu(pid):
        fields = shell('cat', '/proc/' + pid + '/stat').rsplit(')', 1)[1].split()
        return int(fields[11]) + int(fields[12])
    hz = int(shell('getconf', 'CLK_TCK').strip())
    results = []
    for run in range(1, args.runs + 1):
        for scenario in args.scenarios:
            if scenario == 'app-return' and not args.streaming_component:
                parser.error('--streaming-component required for app-return')
            prefix = args.out / (str(run) + '-' + scenario)
            shell('am', 'force-stop', PKG)
            startup = shell('am', 'start', '-W', '-n', COMPONENT)
            prefix.with_suffix('.startup.txt').write_text(startup)
            time.sleep(4)  # same cold-start/cache/prebuild settling time in every version
            pid = shell('pidof', PKG).strip()
            shell('dumpsys', 'gfxinfo', PKG, 'reset')
            cpu0 = stats_cpu(pid)
            start = time.monotonic()
            if scenario == 'idle':
                time.sleep(15)
            elif scenario == 'dock':
                for _ in range(2):
                    for direction in ['DPAD_RIGHT', 'DPAD_LEFT']:
                        for _ in range(5): key(direction)
            elif scenario == 'navigation':
                key('DPAD_UP'); key('DPAD_CENTER')  # Home tab
                key('DPAD_UP'); key('DPAD_RIGHT'); key('DPAD_CENTER')  # Apps tab
                key('BACK')
                key('DPAD_UP'); key('DPAD_RIGHT'); key('DPAD_RIGHT'); key('DPAD_CENTER')
                time.sleep(2)  # native Settings; launcher clocks stop here
                prefix.with_suffix('.settings.txt').write_text(shell('dumpsys', 'activity', 'activities'))
                shell('am', 'start', '-W', '-n', COMPONENT)
                time.sleep(1)
            elif scenario == 'grid':
                key('MENU')
                for _ in range(2):
                    for direction in ['DPAD_DOWN', 'DPAD_UP']:
                        for _ in range(5): key(direction)
                key('BACK')
            elif scenario == 'app-return':
                for _ in range(3):
                    shell('am', 'start', '-W', '-n', args.streaming_component)
                    time.sleep(2)
                    key('HOME')  # existing Home on Fire routing; never configured by this tool
                    time.sleep(1)
                prefix.with_suffix('.return.txt').write_text(shell('dumpsys', 'activity', 'activities'))
            else:
                parser.error('Unknown scenario: ' + scenario)
            cpu1 = stats_cpu(pid)
            elapsed = time.monotonic() - start
            raw = shell('dumpsys', 'gfxinfo', PKG, 'framestats')
            prefix.with_suffix('.gfx.txt').write_text(raw)
            memory = shell('dumpsys', 'meminfo', PKG)
            prefix.with_suffix('.mem.txt').write_text(memory)
            result = dict(run=run, scenario=scenario, duration_s=round(elapsed, 2),
                          cpu_pct_one_core=round((cpu1 - cpu0) / hz / elapsed * 100, 2), **summarize(raw))
            match = re.search(r'TOTAL PSS:\s*(\d+)', memory)
            result['pss_kb'] = int(match[1]) if match else None
            match = re.search(r'TotalTime: (\d+)', startup)
            result['startup_ms'] = int(match[1]) if match else None
            results.append(result)
            (args.out / 'results.json').write_text(json.dumps(results, indent=2))
            print(json.dumps(result), flush=True)

if __name__ == '__main__':
    main()
