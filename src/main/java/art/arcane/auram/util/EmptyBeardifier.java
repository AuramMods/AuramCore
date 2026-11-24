package art.arcane.auram.util;

import art.arcane.auram.mixin.minecraft.BeardifierContentsAccess;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraftforge.fml.ModList;

public final class EmptyBeardifier {
    private static final DensityFunctions.BeardifierOrMarker EMPTY = new DensityFunctions.BeardifierOrMarker() {
        @Override public double compute(DensityFunction.FunctionContext context) { return 0.0; }
        @Override public double minValue() { return Double.NEGATIVE_INFINITY; }
        @Override public double maxValue() { return Double.POSITIVE_INFINITY; }
    };

    private EmptyBeardifier() {}

    public static DensityFunctions.BeardifierOrMarker simplify(DensityFunctions.BeardifierOrMarker candidate) {
        if (candidate.getClass() != Beardifier.class) return candidate;
        var beardifier = (Beardifier) candidate;
        var access = (BeardifierContentsAccess) beardifier;
        if (!empty(access.auram$pieces()) || !empty(access.auram$junctions())) return candidate;
        return Enhanced.empty(beardifier) ? EMPTY : candidate;
    }

    private static boolean empty(ObjectListIterator<?> list) {
        return list != null && !list.hasNext() && !list.hasPrevious();
    }

    private static final class Enhanced {
        private static final boolean PRESENT = ModList.get().isLoaded("integrated_api") || ModList.get().isLoaded("yungsapi");
        private static final MethodHandle RIGIDS = find("getEnhancedRigidIterator");
        private static final MethodHandle JUNCTIONS = find("getEnhancedJunctionIterator");

        private static MethodHandle find(String method) {
            if (!PRESENT) return null;
            try {
                return MethodHandles.publicLookup().findVirtual(Beardifier.class, method, MethodType.methodType(ObjectListIterator.class));
            } catch (NoSuchMethodException | IllegalAccessException failure) {
                return null;
            }
        }

        private static boolean empty(Beardifier beardifier) {
            if (!PRESENT) return true;
            if (RIGIDS == null || JUNCTIONS == null) return false;
            try {
                var rigids = (ObjectListIterator<?>) RIGIDS.invokeExact(beardifier);
                var junctions = (ObjectListIterator<?>) JUNCTIONS.invokeExact(beardifier);
                return EmptyBeardifier.empty(rigids) && EmptyBeardifier.empty(junctions);
            } catch (Throwable failure) {
                if (failure instanceof Error error) throw error;
                return false;
            }
        }
    }
}
