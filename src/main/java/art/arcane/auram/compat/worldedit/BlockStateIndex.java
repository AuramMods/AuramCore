package art.arcane.auram.compat.worldedit;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves property changes within a block type's canonical Cartesian state set. */
public final class BlockStateIndex {
    private record PropertyIndex(int stride, Map<Object, Integer> ordinals) {}

    private record Layout(Map<Object, PropertyIndex> properties, Object[] states) {
        int ordinal(Map<?, ?> values) {
            int result = 0;
            for (var entry : values.entrySet()) {
                var property = this.properties.get(entry.getKey());
                result += property.stride() * property.ordinals().get(entry.getValue());
            }
            return result;
        }
    }

    private final Map<? extends Map<?, ?>, ?> statesByValues;
    private volatile Layout layout;

    public BlockStateIndex(Map<? extends Map<?, ?>, ?> statesByValues) {
        this.statesByValues = statesByValues;
    }

    public int ordinal(Map<?, ?> values) {
        return this.layout().ordinal(values);
    }

    public Object neighbor(int ordinal, Object property, Object value) {
        var layout = this.layout();
        var axis = layout.properties().get(property);
        if (axis == null) return null;
        var replacement = axis.ordinals().get(value);
        if (replacement == null) return null;

        int oldValue = (ordinal / axis.stride()) % axis.ordinals().size();
        return layout.states()[ordinal + (replacement - oldValue) * axis.stride()];
    }

    private Layout layout() {
        var result = this.layout;
        if (result == null) {
            synchronized (this) {
                result = this.layout;
                if (result == null) {
                    result = this.build();
                    this.layout = result;
                }
            }
        }
        return result;
    }

    private Layout build() {
        var axes = new LinkedHashMap<Object, Map<Object, Integer>>();
        for (var key : this.statesByValues.keySet()) {
            for (var entry : key.entrySet()) {
                var values = axes.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
                values.computeIfAbsent(entry.getValue(), ignored -> values.size());
            }
        }

        var properties = new HashMap<Object, PropertyIndex>();
        int stride = 1;
        for (var axis : axes.entrySet()) {
            properties.put(axis.getKey(), new PropertyIndex(stride, axis.getValue()));
            stride = Math.multiplyExact(stride, axis.getValue().size());
        }
        var result = new Layout(properties, new Object[stride]);
        for (var entry : this.statesByValues.entrySet()) {
            result.states()[result.ordinal(entry.getKey())] = entry.getValue();
        }
        return result;
    }
}
