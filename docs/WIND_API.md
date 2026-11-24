# Wind sources

AuramCore sends nearby clients transient source descriptions. Clients simulate a
20 Hz field with cells two blocks wide. Full collision cubes and fluids block
flow. Partial collision shapes, including closed doors and trapdoors, allow air
through. Sources must originate in an open cell.

Drive sources on the logical server thread. A continuous source is keyed by its
dimension, namespaced ID, and block position. Setting it again updates its vector.
Use a different ID for multiple sources at the same block. Sources are removed on
chunk unload and should be re-created by the source block when it loads.

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "vent");
Vec3 east = new Vec3(1, 0, 0);
Wind.setEmitter(level, id, outlet, east, 0.3f);
Wind.gust(level, outlet, new Vec3(1, 1, 0), 0.6f);
Wind.stopEmitter(level, id, outlet);
```

The API normalizes direction and accepts magnitudes from zero through one. A
continuous source with zero magnitude removes the source. Co-located sources
combine by vector addition, including cancellation, with a maximum magnitude of
one. Nearby flows blend by strength and connection along open faces.

## Optional Forge integration

Enqueue the IMC message with an offer to accept a bridge. With AuramCore absent,
the callback is never set, and the caller can continue normally.

```java
private static BiConsumer<ServerLevel, CompoundTag> wind;

@SubscribeEvent
public static void enqueue(InterModEnqueueEvent event) {
    InterModComms.sendTo("auram", "wind_api_v1",
        () -> (Consumer<BiConsumer<ServerLevel, CompoundTag>>) bridge -> wind = bridge);
}

static void update(ServerLevel level, BlockPos pos, double x, double z, float power) {
    if (wind == null) return;
    CompoundTag request = new CompoundTag();
    request.putString("operation", power > 0 ? "set" : "stop");
    request.putString("id", "vent"); // The bridge scopes this path to the calling mod.
    request.putLong("pos", pos.asLong());
    request.putDouble("x", x);
    request.putDouble("y", 0);
    request.putDouble("z", z);
    request.putFloat("magnitude", power);
    wind.accept(level, request);
}
```

Use operation `gust` for a single impulse. It uses the position, direction, and
magnitude fields and requires no emitter ID.
