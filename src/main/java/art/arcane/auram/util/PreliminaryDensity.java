package art.arcane.auram.util;

import art.arcane.auram.mixin.minecraft.NoiseYScaleAccess;
import art.arcane.auram.mixin.minecraft.ShiftedNoiseYScaleAccess;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

/** Certifies the unblended preliminary graph before sharing its integer heights. */
public final class PreliminaryDensity {
    private enum Axes { HORIZONTAL, VERTICAL, UNSUPPORTED }

    private final MethodHandles.Lookup lookup;
    private enum SliceMode { NONE, ARRAY, FLAT_INPUT }

    private final SliceMode sliceMode;
    private PreliminaryDensity flatInputs;
    private final IdentityHashMap<DensityFunction, Axes> known = new IdentityHashMap<>();

    private PreliminaryDensity(MethodHandles.Lookup lookup) {
        this(lookup, SliceMode.NONE);
    }

    private PreliminaryDensity(MethodHandles.Lookup lookup, SliceMode sliceMode) {
        this.lookup = lookup;
        this.sliceMode = sliceMode;
    }

    public static boolean canShare(DensityFunction function, MethodHandles.Lookup lookup) {
        try {
            return new PreliminaryDensity(lookup).axes(function) != Axes.UNSUPPORTED;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            return false;
        }
    }

    public static boolean canShareClimate(NoiseRouter router, MethodHandles.Lookup lookup) {
        try {
            var graph = new PreliminaryDensity(lookup);
            return graph.axes(router.temperature()) != Axes.UNSUPPORTED
                    && graph.axes(router.vegetation()) != Axes.UNSUPPORTED
                    && graph.axes(router.continents()) != Axes.UNSUPPORTED
                    && graph.axes(router.erosion()) != Axes.UNSUPPORTED
                    && graph.axes(router.depth()) != Axes.UNSUPPORTED
                    && graph.axes(router.ridges()) != Axes.UNSUPPORTED;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            return false;
        }
    }

    public static boolean canLimitSliceRows(NoiseRouter router, MethodHandles.Lookup lookup) {
        try {
            var graph = new PreliminaryDensity(lookup, SliceMode.ARRAY);
            // NoiseChunk maps the entire router, including functions used later
            // by biome, aquifer, and material rules. Any interpolation marker
            // it installs must respect the length of the supplied array.
            for (DensityFunction function : new DensityFunction[]{
                    router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(),
                    router.lavaNoise(), router.temperature(), router.vegetation(), router.continents(),
                    router.erosion(), router.depth(), router.ridges(), router.initialDensityWithoutJaggedness(),
                    router.finalDensity(), router.veinToggle(), router.veinRidged(), router.veinGap()}) {
                if (graph.axes(function) == Axes.UNSUPPORTED) return false;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            return false;
        }
    }

    private Axes axes(DensityFunction function) throws ReflectiveOperationException {
        if (function == null) return Axes.UNSUPPORTED;
        Axes cached = known.get(function);
        if (cached != null) return cached;
        known.put(function, Axes.UNSUPPORTED);
        Axes result = classify(function);
        known.put(function, result);
        return result;
    }

    private Axes classify(DensityFunction function) throws ReflectiveOperationException {
        if (function.getClass() == BlendedNoise.class) return Axes.VERTICAL;
        if (!VanillaDensity.isBuiltin(function)) {
            if (sliceMode != SliceMode.NONE && function.getClass().getName().equals("dev.worldgen.tectonic.worldgen.densityfunction.Invert")) {
                // Tectonic's unary transform fills its child array, then
                // transforms precisely those rows. Its child still needs checking.
                return axes((DensityFunction) function.getClass().getMethod("input").invoke(function));
            }
            return Axes.UNSUPPORTED;
        }
        if (function instanceof DensityFunctions.HolderHolder holder) return axes(holder.function().value());
        if (function instanceof DensityFunctions.Spline spline) return splineAxes(spline.spline());
        if (function instanceof DensityFunctions.MarkerOrMarked marker) {
            String type = ((Enum<?>) marker.type()).name();
            if (sliceMode == SliceMode.ARRAY && type.equals("FlatCache")) {
                // A one-cell TerrainCell's four corners use the eager Y=0
                // values. Check those inputs separately so vertical children
                // may qualify without allowing unknown or deferred fillers.
                if (flatInputs == null) flatInputs = new PreliminaryDensity(lookup, SliceMode.FLAT_INPUT);
                return flatInputs.axes(marker.wrapped()) == Axes.UNSUPPORTED ? Axes.UNSUPPORTED : Axes.HORIZONTAL;
            }
            if (sliceMode == SliceMode.FLAT_INPUT &&
                    (type.equals("Interpolated") || type.equals("CacheAllInCell"))) return Axes.UNSUPPORTED;
            Axes child = axes(marker.wrapped());
            return switch (type) {
                case "Cache2D", "FlatCache" ->
                        sliceMode == SliceMode.FLAT_INPUT || child == Axes.HORIZONTAL ? child : Axes.UNSUPPORTED;
                case "Interpolated", "CacheOnce", "CacheAllInCell" -> child;
                default -> Axes.UNSUPPORTED;
            };
        }
        String name = function.getClass().getSimpleName();
        Axes own = switch (name) {
            case "Constant", "ShiftA", "ShiftB", "EndIslandDensityFunction", "BlendOffset", "BlendAlpha",
                 "Ap2", "MulOrAdd", "Mapped", "Clamp", "RangeChoice", "BlendDensity" -> Axes.HORIZONTAL;
            case "Noise" -> axesOfScale(((NoiseYScaleAccess) function).auram$yScale());
            case "ShiftedNoise" -> axesOfScale(((ShiftedNoiseYScaleAccess) function).auram$yScale());
            case "Shift", "YClampedGradient", "WeirdScaledSampler" -> Axes.VERTICAL;
            default -> Axes.UNSUPPORTED;
        };
        if (own == Axes.UNSUPPORTED) return own;
        // Runtime records retain their source component names while Forge remaps
        // the accessors. Use the real fields for all child and noise references.
        for (Field field : function.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (DensityFunction.class.isAssignableFrom(field.getType())) {
                own = merge(own, axes((DensityFunction) read(function, field)));
            } else if (field.getType() == DensityFunction.NoiseHolder.class) {
                var noise = ((DensityFunction.NoiseHolder) read(function, field)).noise();
                if (noise != null && noise.getClass() != NormalNoise.class) return Axes.UNSUPPORTED;
            }
        }
        return own;
    }

    private Axes splineAxes(CubicSpline<?, ?> spline) throws ReflectiveOperationException {
        if (spline instanceof CubicSpline.Constant<?, ?>) return Axes.HORIZONTAL;
        if (!(spline instanceof CubicSpline.Multipoint<?, ?> points)
                || !(points.coordinate() instanceof DensityFunctions.Spline.Coordinate coordinate)) {
            return Axes.UNSUPPORTED;
        }
        Axes result = axes(coordinate.function().value());
        for (CubicSpline<?, ?> value : points.values()) result = merge(result, splineAxes(value));
        return result;
    }

    private static Axes axesOfScale(double scale) {
        return scale == 0.0 ? Axes.HORIZONTAL : Axes.VERTICAL;
    }

    private Object read(DensityFunction function, Field field) throws ReflectiveOperationException {
        var access = MethodHandles.privateLookupIn(function.getClass(), lookup).unreflectGetter(field);
        try {
            return access.invoke(function);
        } catch (Throwable failure) {
            throw new ReflectiveOperationException(failure);
        }
    }

    private static Axes merge(Axes first, Axes second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
