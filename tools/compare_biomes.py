#!/usr/bin/env python3
"""Compare biome command semantics, actual resolver work and independent JVM benchmark runs."""
import argparse
import hashlib
import json
import statistics
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reference', type=Path, action='append', required=True)
parser.add_argument('--candidate', type=Path, action='append', required=True)
parser.add_argument('--output', type=Path)
args = parser.parse_args()

def read(runtime, filename):
    return json.loads((runtime / filename).read_text())

expected = read(args.reference[0], 'biome-semantics.json')
for runtime in args.reference + args.candidate:
    actual = read(runtime, 'biome-semantics.json')
    if actual != expected:
        for index, (left, right) in enumerate(zip(expected, actual)):
            if left != right:
                raise SystemExit(f'Command {index} differs in {runtime}:\nreference={left}\nactual={right}')
        raise SystemExit(f'Command counts differ in {runtime}: {len(expected)} / {len(actual)}')

original_work = read(args.reference[0], 'biome-work.json')
candidate_work = read(args.candidate[0], 'biome-work.json')
for left, right in zip(original_work, candidate_work):
    if (left['command'], left['dimension']) != (right['command'], right['dimension']):
        raise SystemExit('Resolver-work cases do not match')
    if right['resolver_calls'] > left['resolver_calls']:
        raise SystemExit(f'Resolver work increased: {right}')

benchmarks = []
for index, row in enumerate(read(args.reference[0], 'biome-benchmark.json')):
    name = row['workload']
    reference_runs = [read(path, 'biome-benchmark.json')[index] for path in args.reference]
    candidate_runs = [read(path, 'biome-benchmark.json')[index] for path in args.candidate]
    for run in reference_runs + candidate_runs:
        if run['workload'] != name or run['checksum'] != row['checksum']:
            raise SystemExit(f'Benchmark workload/checksum differs: {name}')
    before = [run['median_ms'] for run in reference_runs]
    after = [run['median_ms'] for run in candidate_runs]
    benchmarks.append({'workload': name, 'reference_median_ms_by_jvm': before,
                       'candidate_median_ms_by_jvm': after,
                       'median_speedup': statistics.median(before) / statistics.median(after),
                       'all_candidate_medians_below_all_reference_medians': max(after) < min(before),
                       'reference_runs': reference_runs, 'candidate_runs': candidate_runs})

result = {
    'identical_command_cases_per_jvm': len(expected),
    'semantic_sha256': hashlib.sha256(json.dumps(expected, sort_keys=True).encode()).hexdigest(),
    'reference_resolver_calls': sum(r['resolver_calls'] for r in original_work),
    'candidate_resolver_calls': sum(r['resolver_calls'] for r in candidate_work),
    'cases_with_reduced_resolver_calls': sum(b['resolver_calls'] < a['resolver_calls'] for a, b in zip(original_work, candidate_work)),
    'reference_runtimes': [str(p) for p in args.reference],
    'candidate_runtimes': [str(p) for p in args.candidate],
    'benchmarks': benchmarks,
    'scope': 'Headless four-active-CPU JVM fixtures; identical dispatcher commands and decoded biome refresh payloads. Timings are fixture-local, not a hosted-server TPS estimate.'
}
if args.output:
    args.output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({k: v for k, v in result.items() if k != 'benchmarks'}, indent=2))
for row in benchmarks:
    print(json.dumps({k: v for k, v in row.items() if k not in ('reference_runs', 'candidate_runs')}))
