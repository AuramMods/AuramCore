#!/usr/bin/env python3
"""Compare the tuning workload's saved block states, independent of registry IDs."""
import argparse
from collections import Counter
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import zlib


class Nbt:
    def __init__(self, data):
        self.data = io.BytesIO(data)

    def number(self, code):
        return struct.unpack('>'+code, self.data.read(struct.calcsize('>'+code)))[0]

    def string(self):
        return self.data.read(self.number('H')).decode('utf-8')

    def value(self, kind):
        if kind in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 7:
            return self.data.read(self.number('i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            item_kind, size = self.number('B'), self.number('i')
            return [self.value(item_kind) for _ in range(size)]
        if kind == 10:
            compound = {}
            while item_kind := self.number('B'):
                name = self.string()
                compound[name] = self.value(item_kind)
            return compound
        if kind in (11, 12):
            return [self.number('i' if kind == 11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(f'Unsupported NBT type: {kind}')

    def root(self):
        kind = self.number('B')
        self.string()
        return self.value(kind)


def read_chunk(world, x, z):
    path = world/'region'/f'r.{x//32}.{z//32}.mca'
    with path.open('rb') as file:
        file.seek(4*((x % 32)+(z % 32)*32))
        location = int.from_bytes(file.read(4), 'big')
        if not location:
            raise ValueError(f'Missing saved chunk {x},{z}: {path}')
        file.seek((location >> 8)*4096)
        length = int.from_bytes(file.read(4), 'big')
        compression = file.read(1)[0]
        if compression & 128:
            data = (world/'region'/f'c.{x}.{z}.mcc').read_bytes()
        else:
            data = file.read(length-1)
        compression &= 127
        data = {1: gzip.decompress, 2: zlib.decompress, 3: lambda value: value}[compression](data)
        return Nbt(data).root()


def section_states(section):
    block_states = section.get('block_states', {}) if section else {}
    palette = [json.dumps(state, sort_keys=True, separators=(',', ':'))
               for state in block_states.get('palette', [{'Name': 'minecraft:air'}])]
    if len(palette) == 1:
        return [palette[0]]*4096
    bits = max(4, (len(palette)-1).bit_length())
    per_long, mask = 64//bits, (1 << bits)-1
    data = block_states['data']
    return [palette[(data[index//per_long] >> ((index % per_long)*bits)) & mask]
            for index in range(4096)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('first', type=Path, help='First world directory (only its region subdirectory is needed)')
    parser.add_argument('second', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    digests = [hashlib.sha256(), hashlib.sha256()]
    differences, examples, differing_chunks = Counter(), [], 0
    compared = 0
    for index in range(80):
        x, z = 80+index % 10, 80+index//10
        chunks = [read_chunk(world, x, z) for world in (args.first, args.second)]
        sections = [{section['Y']: section for section in chunk['sections']} for chunk in chunks]
        chunk_differences = 0
        for y in sorted(set(sections[0]) | set(sections[1])):
            states = [section_states(section.get(y)) for section in sections]
            for digest, values in zip(digests, states):
                digest.update(f'{x},{y},{z}\0'.encode())
                digest.update(('\0'.join(values)+'\0').encode())
            for position, (first, second) in enumerate(zip(*states)):
                compared += 1
                if first == second:
                    continue
                differences[(first, second)] += 1
                chunk_differences += 1
                if len(examples) < 12:
                    examples.append({'position': [x*16+position % 16, y*16+position//256,
                                                   z*16+(position//16) % 16],
                                     'first': json.loads(first), 'second': json.loads(second)})
        differing_chunks += bool(chunk_differences)
    result = {
        'worlds': [str(args.first), str(args.second)], 'compared_blocks': compared,
        'differing_blocks': sum(differences.values()), 'differing_chunks': differing_chunks,
        'canonical_block_fingerprints': [digest.hexdigest() for digest in digests],
        'largest_differences': [{'first': json.loads(pair[0]), 'second': json.loads(pair[1]), 'count': count}
                                for pair, count in differences.most_common(20)],
        'examples': examples,
        'scope': 'Same 80 target chunks; saved palette names/properties and packed state arrays, independent of numeric global registry IDs.',
    }
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({key: value for key, value in result.items()
                      if key not in {'largest_differences', 'examples'}}, indent=2))


if __name__ == '__main__':
    main()
