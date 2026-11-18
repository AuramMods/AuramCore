#!/usr/bin/env python3
"""Compare loaded-fluid snapshots from the production-mapped reference/candidate fixtures."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('reference', type=Path)
parser.add_argument('candidate', type=Path)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
reference = json.loads((args.reference / 'fluid-loaded-snapshots.json').read_text())
candidate = json.loads((args.candidate / 'fluid-loaded-snapshots.json').read_text())
if reference != candidate:
    for index, (left, right) in enumerate(zip(reference, candidate)):
        if left != right:
            raise SystemExit(f'Fluid state {index} differs:\nreference={left}\ncandidate={right}')
    raise SystemExit(f'Fluid snapshot counts differ: {len(reference)} / {len(candidate)}')
digest = hashlib.sha256(json.dumps(candidate, sort_keys=True).encode()).hexdigest()
result = {'loaded_states_equal': len(candidate), 'state_sha256': digest,
          'reference': str(args.reference), 'candidate': str(args.candidate)}
if args.output:
    args.output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
