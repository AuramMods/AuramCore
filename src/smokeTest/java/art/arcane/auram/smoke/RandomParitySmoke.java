package art.arcane.auram.smoke;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class RandomParitySmoke {
    private RandomParitySmoke() {
    }

    static Map<String, List<String>> capture() {
        Map<String, List<String>> samples = new LinkedHashMap<>();
        for (long seed : new long[]{0, -1, 104729, Long.MIN_VALUE, Long.MAX_VALUE}) {
            String label = Long.toString(seed);
            RandomSource random = RandomSource.create(seed);
            samples.put("mixed-" + label, sequence(random));
            samples.put("fork-" + label, sequence(random.fork()));
            var positional = random.forkPositional();
            samples.put("position-" + label, sequence(positional.at(-9123, 71, 8731)));
            samples.put("string-" + label, sequence(positional.fromHashOf("auram:random_parity")));
            random.setSeed(seed);
            samples.put("reseed-" + label, sequence(random));
            RandomSource local = RandomSource.createNewThreadLocalInstance();
            local.setSeed(seed);
            samples.put("local-" + label, sequence(local));
            RandomSource shared = RandomSource.createThreadSafe();
            shared.setSeed(seed);
            samples.put("shared-" + label, sequence(shared));
            if (RandomSource.create(seed) instanceof BitRandomSource bits) {
                samples.put("bits-" + label, List.of(Integer.toString(bits.next(8)), Integer.toString(bits.next(8)),
                        Integer.toString(bits.next(24)), Long.toString(bits.nextLong())));
            }
            samples.put("slime-" + label, sequence(WorldgenRandom.seedSlimeChunk(-1_432_000, 817_321, seed, 987234911L)));
        }
        return samples;
    }

    static int verifyShared() throws Exception {
        RandomSource shared = RandomSource.createThreadSafe();
        shared.setSeed(104729L);
        int perWorker = 2_048;
        var results = new long[4 * perWorker];
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int start = worker * perWorker;
                workers.add(() -> {
                    for (int i = start; i < start + perWorker; i++) results[i] = shared.nextLong();
                    return null;
                });
            }
            for (var future : executor.invokeAll(workers, 10, TimeUnit.SECONDS)) future.get();
        } finally {
            executor.shutdownNow();
        }
        RandomSource reference = RandomSource.create(104729L);
        long[] expected = new long[results.length];
        Arrays.setAll(expected, i -> reference.nextLong());
        Arrays.sort(expected);
        Arrays.sort(results);
        if (!Arrays.equals(expected, results)) throw new AssertionError("Concurrent draws changed the seeded LXM stream");
        return results.length;
    }

    private static List<String> sequence(RandomSource random) {
        List<String> values = new ArrayList<>();
        for (int bound : new int[]{1, 2, 3, 31, 1024, Integer.MAX_VALUE}) {
            values.add(Integer.toString(random.nextInt()));
            values.add(Integer.toString(random.nextInt(bound)));
            values.add(Long.toString(random.nextLong()));
            values.add(Boolean.toString(random.nextBoolean()));
            values.add(Float.toHexString(random.nextFloat()));
            values.add(Double.toHexString(random.nextDouble()));
            values.add(Double.toHexString(random.nextGaussian()));
        }
        return values;
    }
}
