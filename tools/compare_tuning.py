#!/usr/bin/env python3
"""Compare real Forge chunk workloads under five JVM configurations, sequentially."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CAPTURED_FLAGS = [
    '-XX:+ParallelRefProcEnabled', '-XX:MaxGCPauseMillis=200',
    '-XX:+UnlockExperimentalVMOptions', '-XX:+DisableExplicitGC', '-XX:+AlwaysPreTouch',
    '-XX:G1NewSizePercent=30', '-XX:G1MaxNewSizePercent=40', '-XX:G1HeapRegionSize=8M',
    '-XX:G1ReservePercent=20', '-XX:G1HeapWastePercent=5', '-XX:G1MixedGCCountTarget=4',
    '-XX:InitiatingHeapOccupancyPercent=15', '-XX:G1MixedGCLiveThresholdPercent=90',
    '-XX:G1RSetUpdatingPauseTimePercent=5', '-XX:SurvivorRatio=32',
    '-XX:+PerfDisableSharedMem', '-XX:MaxTenuringThreshold=1',
]
CONFIGURATIONS = {
    'captured': {'xms': 'default', 'flags': CAPTURED_FLAGS},
    'g1_defaults': {'xms': 'default', 'flags': []},
    'workers_2': {'xms': 'default', 'flags': [], 'threadtweak_workers': 2},
    'workers_3': {'xms': 'default', 'flags': [], 'threadtweak_workers': 3},
    'initial_heap_4g': {'xms': '4G', 'flags': []},
}


def summarize(runs):
    metrics = {
        'full_chunks_per_second': lambda b: b['full_chunks_per_second'],
        'batch_median_seconds': lambda b: b['chunk_batch_seconds']['median'],
        'batch_p95_seconds': lambda b: b['chunk_batch_seconds']['p95'],
        'tick_median_ms': lambda b: b['tick_ms']['median'],
        'tick_p95_ms': lambda b: b['tick_ms']['p95'],
        'tick_max_ms': lambda b: b['tick_ms']['max'],
        'allocated_bytes': lambda b: b['live_java_thread_allocated_bytes_delta'],
        'peak_sampled_heap_bytes': lambda b: b['peak_sampled_heap_bytes'],
        'gc_total_pause_ms': lambda b: b['jfr']['gc_total_pause_ms'],
        'gc_p95_pause_ms': lambda b: b['jfr']['gc_pause_ms']['p95'],
        'gc_max_pause_ms': lambda b: b['jfr']['gc_pause_ms']['max'],
        'gc_pauses': lambda b: b['jfr']['gc_pause_ms']['count'],
        'process_cpu_seconds': lambda b: b['process_cpu_seconds'],
        'server_thread_cpu_seconds': lambda b: b['server_thread_cpu_seconds'],
    }
    output = {}
    for name, rows in runs.items():
        values = {metric: [read(row['benchmark']) for row in rows]
                  for metric, read in metrics.items()}
        output[name] = {
            'runs': len(rows), 'values': values,
            'median': {metric: statistics.median(series) for metric, series in values.items()},
            'background_parallelism': [row['benchmark']['background_parallelism'] for row in rows],
            'fingerprints': [row['benchmark']['generated_block_fingerprint'] for row in rows],
        }
    baseline = output['captured']['median']
    for name, result in output.items():
        result['change_from_captured_percent'] = {
            metric: 100 * (value / baseline[metric] - 1)
            for metric, value in result['median'].items() if baseline[metric]
        }
        defaults = output['g1_defaults']['median']
        result['change_from_g1_defaults_percent'] = {
            metric: 100 * (value / defaults[metric] - 1)
            for metric, value in result['median'].items() if defaults[metric]
        }
        reference = 'captured' if name in {'captured', 'g1_defaults'} else 'g1_defaults'
        result['paired_round_reference'] = reference
        result['paired_round_change_percent'] = {
            metric: [100 * (value / original - 1) if original else None
                     for value, original in zip(series, output[reference]['values'][metric])]
            for metric, series in result['values'].items()
        }
    return {
        'configurations': CONFIGURATIONS, 'summary': output,
        'all_block_fingerprints_equal': len({fp for result in output.values()
                                              for fp in result['fingerprints']}) == 1,
        'interpretation': 'Below 5% drift is inconclusive. Require repeated throughput and latency improvements before adopting settings.',
        'limits': [
            'Four-vCPU ARM Linux VM; historical hosted server used four AMD x86 CPUs and another Java 17 build.',
            'Fresh fixed-seed world and synthetic NBT entity load, not the retained heap or factories of the hosted save.',
            'Allocation delta sums live Java threads; JFR allocation weights are sampled estimates.',
            'Main tick duration includes chunk waits; startup, fingerprinting and shutdown are excluded.',
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True)
    parser.add_argument('--libraries', required=True, type=Path)
    parser.add_argument('--server-template', required=True, type=Path)
    parser.add_argument('--runtime-parent', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--resume', action='store_true', help='Reuse explicitly completed result files from this unchanged input matrix')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    common = [sys.executable, str(ROOT/'tools/performance_smoke.py'), '--java', args.java,
              '--libraries', str(args.libraries), '--server-template', str(args.server_template),
              '--mode', 'tuning', '--no-build', '--no-mixin-export', '--xmx', '12000M',
              '--java-arg=--add-modules=jdk.jfr,jdk.management', '--java-arg=-XX:+UseG1GC']
    if args.runtime_parent:
        common += ['--runtime-parent', str(args.runtime_parent)]
    names = list(CONFIGURATIONS)
    runs = {name: [] for name in names}
    for round_index in range(args.rounds):
        order = names[round_index % len(names):] + names[:round_index % len(names)]
        for name in order:
            output_file = args.output/f'cycle4-{name}-{round_index+1}.json'
            if args.resume and output_file.exists():
                runs[name].append(json.loads(output_file.read_text()))
                print(f'Reusing completed {name}, round {round_index+1}', flush=True)
                continue
            configuration = CONFIGURATIONS[name]
            command = common + ['--xms', configuration['xms']]
            command += ['--java-arg='+flag for flag in configuration['flags']]
            if 'threadtweak_workers' in configuration:
                command += ['--threadtweak-workers', str(configuration['threadtweak_workers'])]
            print(f'Running {name}, round {round_index+1}/{args.rounds}', flush=True)
            process = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True)
            (args.output/f'{name}-{round_index+1}.log').write_text(process.stdout)
            print(process.stdout, flush=True)
            if process.returncode:
                raise SystemExit(process.returncode)
            runtime = Path(next(line.removeprefix('Runtime: ') for line in process.stdout.splitlines()
                                if line.startswith('Runtime: ')))
            row = {'run': json.loads((runtime/'result.json').read_text()),
                   'benchmark': json.loads((runtime/'tuning-benchmark.json').read_text())}
            runs[name].append(row)
            output_file.write_text(json.dumps(row, indent=2)+'\n')
            print(f"{name}: {row['benchmark']['full_chunks_per_second']:.3f} FULL chunks/s; "
                  f"p95 tick {row['benchmark']['tick_ms']['p95']:.1f} ms", flush=True)
        (args.output/'cycle4-summary.json').write_text(json.dumps(summarize(runs), indent=2)+'\n')


if __name__ == '__main__':
    main()
