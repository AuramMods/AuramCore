#!/usr/bin/env python3
"""Repeat installed-function selector A/B runs and verify traced command results."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java', required=True)
parser.add_argument('--libraries', required=True, type=Path)
parser.add_argument('--baseline-jar', required=True, type=Path)
parser.add_argument('--pack', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--repetitions', type=int, default=3)
args = parser.parse_args()

command = [sys.executable, str(ROOT/'tools/performance_smoke.py'), '--java', args.java,
           '--libraries', str(args.libraries), '--mode', 'selectors', '--no-build']
for path in ['config/paxi/datapacks/guns1-20-0.1.2-dp.zip', 'mods/scorched-1.1.3e.jar',
             'mods/Incendium_1.20.x_v5.3.5.jar']:
    command += ['--datapack', str(args.pack/path)]
runs = {'control': [], 'candidate': []}
for pair in range(args.repetitions):
    for mode in (['control', 'candidate'] if pair % 2 == 0 else ['candidate', 'control']):
        result = subprocess.run(command + (['--baseline-jar', str(args.baseline_jar)] if mode == 'control' else []),
                                cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        print(result.stdout, flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)
        runtime = Path(next(line.removeprefix('Runtime: ') for line in result.stdout.splitlines()
                            if line.startswith('Runtime: ')))
        runs[mode].append({'run': json.loads((runtime/'result.json').read_text()),
                           'benchmark': json.loads((runtime/'selector-benchmark.json').read_text())})

summary = []
reference = runs['control'][0]['benchmark']
for index, original in enumerate(reference['functions']):
    times, counts = {}, {}
    for mode, measurements in runs.items():
        rows = [measurement['benchmark']['functions'][index] for measurement in measurements]
        for row in rows:
            if set(row['trace_by_function']) != set(original['trace_by_function']):
                raise AssertionError((mode, original['function'], 'nested function set changed'))
            if row['entity_serializations'] != rows[0]['entity_serializations']:
                raise AssertionError((mode, original['function'], 'serialization count changed between runs'))
            for name, stats in original['trace_by_function'].items():
                for key in ['commands', 'command_errors', 'result_hash']:
                    if row['trace_by_function'][name].get(key) != stats.get(key):
                        raise AssertionError((mode, original['function'], name, key))
        times[mode] = [row['median_20_calls_ms'] for row in rows]
        counts[mode] = rows[0]['entity_serializations']
    summary.append({'function': original['function'], 'process_medians_ms': times,
                    'entity_serializations_per_500_calls': counts, 'command_results_match': True,
                    'median_change_percent': 100 * (statistics.median(times['candidate']) /
                                                   statistics.median(times['control']) - 1)})

args.output.mkdir(parents=True, exist_ok=True)
for mode, measurements in runs.items():
    for index, measurement in enumerate(measurements, 1):
        (args.output/f'cycle1-{mode}-{index}.json').write_text(json.dumps(measurement, indent=2)+'\n')
(args.output/'cycle1-summary.json').write_text(json.dumps({
    'semantic_comparisons': reference['semantic_comparisons'], 'function_summary': summary,
    'scope': reference['scope'],
    'limitations': ['Replay attributes concrete installed functions; Spark did not retain their IDs.',
                    'These are local fixture timings, not hosted-server or pack-wide speedups.'],
}, indent=2)+'\n')
print(json.dumps(summary, indent=2))
