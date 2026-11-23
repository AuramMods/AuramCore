package art.arcane.auram.tweak;

import com.mojang.logging.LogUtils;
import io.netty.util.internal.ThreadLocalRandom;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class FasterRandom implements BitRandomSource {
    private static final String ALGORITHM = "L64X128MixRandom";
    private static final RandomGeneratorFactory<RandomGenerator> FACTORY = loadFactory();

    private long seed;
    private RandomGenerator generator;

    public FasterRandom(long seed) {
        this.seed = seed;
        this.generator = FACTORY.create(seed);
    }

    public static boolean enabled() {
        return FACTORY != null;
    }

    public static RandomSource seededSource(long seed) {
        return enabled() ? new FasterRandom(seed) : null;
    }

    public static RandomSource localSource() {
        return enabled() ? new FasterRandom(ThreadLocalRandom.current().nextLong()) : null;
    }

    public static RandomSource sharedSource() {
        return enabled() ? new Shared(RandomSupport.generateUniqueSeed()) : null;
    }

    private static RandomGeneratorFactory<RandomGenerator> loadFactory() {
        if (!TweakConfig.read("faster-random.json", Settings.class, new Settings()).enabled) return null;
        Thread thread = Thread.currentThread();
        ClassLoader context = thread.getContextClassLoader();
        for (ClassLoader loader : new ClassLoader[]{context, ClassLoader.getSystemClassLoader()}) {
            try {
                // Java 17 caches providers by simple name. Some launcher layers expose the same
                // providers twice, which breaks that initializer for the entire JVM.
                if (!hasLxmProvider(loader)) continue;
                thread.setContextClassLoader(loader);
                var factory = RandomGeneratorFactory.<RandomGenerator>of(ALGORITHM);
                factory.create(0L);
                return factory;
            } catch (IllegalArgumentException | java.util.ServiceConfigurationError unavailable) {
                LogUtils.getLogger().debug("Cannot load {} through {}", ALGORITHM, loader, unavailable);
            } finally {
                thread.setContextClassLoader(context);
            }
        }
        LogUtils.getLogger().warn("No usable {} provider found. Using vanilla random sources.", ALGORITHM);
        return null;
    }

    private static boolean hasLxmProvider(ClassLoader loader) {
        var names = new HashSet<String>();
        var duplicate = ServiceLoader.load(RandomGenerator.class, loader).stream()
                .map(ServiceLoader.Provider::type)
                .filter(type -> !type.isInterface())
                .anyMatch(type -> !names.add(type.getSimpleName()));
        return !duplicate && names.contains(ALGORITHM);
    }

    @Override
    public RandomSource fork() {
        return new FasterRandom(nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new PositionalFactory(nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = seed;
        this.generator = FACTORY.create(seed);
    }

    @Override
    public int next(int bits) {
        // The installed 5.1.0 BitRandomSource path derives bits from the last seed without advancing LXM.
        long derived = (seed * 0x5DEECE66DL + 11L) & ((1L << 48) - 1);
        return (int) (derived >>> (48 - bits));
    }

    @Override public int nextInt() { return generator.nextInt(); }
    @Override public int nextInt(int bound) { return generator.nextInt(bound); }
    @Override public long nextLong() { return generator.nextLong(); }
    @Override public boolean nextBoolean() { return generator.nextBoolean(); }
    @Override public float nextFloat() { return generator.nextFloat(); }
    @Override public double nextDouble() { return generator.nextDouble(); }
    @Override public double nextGaussian() { return generator.nextGaussian(); }

    // The Java LXM generator is mutable. Only the explicit shared factory needs synchronized access.
    public static final class Shared extends FasterRandom {
        public Shared(long seed) {
            super(seed);
        }

        @Override public RandomSource fork() { return new Shared(nextLong()); }
        @Override public synchronized void setSeed(long seed) { super.setSeed(seed); }
        @Override public synchronized int next(int bits) { return super.next(bits); }
        @Override public synchronized int nextInt() { return super.nextInt(); }
        @Override public synchronized int nextInt(int bound) { return super.nextInt(bound); }
        @Override public synchronized long nextLong() { return super.nextLong(); }
        @Override public synchronized boolean nextBoolean() { return super.nextBoolean(); }
        @Override public synchronized float nextFloat() { return super.nextFloat(); }
        @Override public synchronized double nextDouble() { return super.nextDouble(); }
        @Override public synchronized double nextGaussian() { return super.nextGaussian(); }
    }

    private record PositionalFactory(long seed) implements PositionalRandomFactory {
        @Override
        public RandomSource at(int x, int y, int z) {
            return new FasterRandom(Mth.getSeed(x, y, z) ^ seed);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            return new FasterRandom(name.hashCode() ^ seed);
        }

        @Override
        public void parityConfigString(StringBuilder output) {
            output.append("RandomGeneratorRandom$Splitter{").append(seed).append('}');
        }
    }

    private static final class Settings {
        private boolean enabled;
    }
}
