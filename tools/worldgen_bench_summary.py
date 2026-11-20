#!/usr/bin/env python3
"""Summarize a completed worldgen-bench fixture run."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def rows(path):
    with path.open(newline='') as file:
        return list(csv.DictReader(file))


def percentile(values, fraction):
    return sorted(values)[math.ceil(fraction * len(values)) - 1]


def summarize(runtime):
    result = json.loads((runtime / 'result.json').read_text())
    if not result['passed']:
        raise ValueError(f'Fixture did not finish successfully: {runtime}')
    ticks = rows(runtime / 'surface-map-bench.csv')
    chunks = rows(runtime / 'worldgen-chunks.csv')
    elapsed = int(ticks[-1]['elapsed_ns']) / 1e9
    latency = [int(chunk['ready_ns']) / 1e6 for chunk in chunks]
    stages = runtime / 'worldgen-stage-cpu.csv'
    targets = runtime / 'worldgen-targets.csv'
    level_to_spawn = runtime / 'worldgen-level-ready-ns.txt'
    return {
        'runtime': str(runtime.resolve()),
        'artifact_sha256': result['artifact_sha256'],
        'smoke_sha256': hashlib.sha256((runtime / 'mods/auram-smoke.jar').read_bytes()).hexdigest(),
        'jvm_command': result['jvm_command'],
        'seed': next(line.split('=', 1)[1] for line in (runtime / 'server.properties').read_text().splitlines()
                     if line.startswith('level-seed=')),
        'mod_jar_count': result['mod_jar_count'],
        'spawn_preparation_s': int((runtime / 'worldgen-spawn-ns.txt').read_text()) / 1e9,
        'level_to_spawn_ready_s': int(level_to_spawn.read_text()) / 1e9 if level_to_spawn.exists() else None,
        'cold_elapsed_s': elapsed,
        'cold_full_chunks': len(chunks),
        'cold_full_chunks_per_s': len(chunks) / elapsed,
        'chunk_ready_p50_ms': percentile(latency, .50),
        'chunk_ready_p95_ms': percentile(latency, .95),
        'tick_p95_ms': percentile([int(tick['tick_ns']) / 1e6 for tick in ticks], .95),
        'stages': [{key: value if key in {'phase', 'stage'} else int(value) for key, value in row.items()}
                   for row in rows(stages)] if stages.exists() else [],
        'targets': [{key: value if key in {'phase', 'target'} else int(value) for key, value in row.items()}
                    for row in rows(targets)] if targets.exists() else [],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runtime', nargs='+', type=Path)
    args = parser.parse_args()
    print(json.dumps([summarize(runtime) for runtime in args.runtime], indent=2))


if __name__ == '__main__':
    main()
