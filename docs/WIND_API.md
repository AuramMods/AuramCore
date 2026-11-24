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

## Backends and replay

The renderer uses the 20 Hz OpenGL compute solver when the context supports
OpenGL 4.3. macOS OpenGL 4.1 and other older contexts use the CPU worker.
`-Dauram.vapor.forceCpu=true` selects the CPU solver on a compute-capable
context. Both publish through Bliss's existing `auramVaporVolume` sampler.

Wind uses the same connected-air topology and recurrence in both backends.
Vapor's GPU passes compute directional offers, then give each destination a
fraction of its incoming offers based on available capacity. The CPU worker
can also use capacity freed earlier in its top-down iteration, so saturated
cells may differ locally. Rejected offers remain at their source in both
solvers. Compare steady throughput and density distributions between backends
with the same terrain and sources, allowing for those ordering differences.

For an opt-in, 40-tick diagnostics log, set
`-Dauram.fields.probe=wx,wy,wz,ix,iy,iz,ox,oy,oz` using integer world
coordinates. Supply a Wind sample point and the two neighboring cells spanning
an outlet, inside first. The log reports the local Wind vector and the net
Vapor transferred across the outlet in density units per tick. Set only
`-Dauram.fields.probe` to collect timing without an outlet. The CPU number
measures the worker step. The compute number measures render-thread submission.

The matched clear-weather room test measures net crossings over 200 settled
ticks. In the OpenGL 4.5 Mesa replay, the side vent moved 0.067416 density per
tick at 0.30 Wind and the open ceiling moved 0.063983. CPU results were
0.067404 and 0.063983. The comparison allowed 0.002 maximum component error
for Wind, 0.012 density per tick for outlet throughput, and 0.08 mean density
error across active cells. The measured Wind component error was about
0.00000001 and the mean active-cell density difference was 0.003709 in the
powered fixture. The largest per-cell difference was 0.251104 near saturated
transport. The headless run also exercised Wind rebase and gust dispatch.
