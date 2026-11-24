# Vapor emitter API

Vapor is temporary visual density in world-space blocks. The server owns source
lifetimes and sends source updates to nearby clients. Each client transports
the density upward and outward through its loaded terrain and lets it fade.
Use an open cell for the emission point, such as the air above a furnace.

AuramBliss uses local fog to slow erosion. Clear air uses the original decay
rate. Full rain at Auram's default fog setting gives a lingering value of
0.5 in areas with precipitation, including indoors. A value of 0.5 doubles
the decay time, and 1.0 stops erosion while transport continues. The
simulation samples Bliss's fog settings, time-of-day density, rain, and
dimensional haze in world space. Lighting and camera exposure never affect
the emission rate.

Each cell holds up to 1.0 density. A transfer that meets a full cell leaves
the excess in its source cell. Continued emission can therefore fill the air
under a ceiling and build downward as the upper cells fill.
Full collision cubes and fluid-filled cells block transport. Partial collision
shapes, including doors, trapdoors, levers, and ladders, have the same capacity
and transfer rates as air.

## Typed API

Call on the logical server thread. Density is normalized from 0 to 1. Zero is
empty and one is the upper end of the simulation's visible density. Continuous
rates add density each simulation tick at 20 Hz. A radius of zero affects one
cell. A radius up to 12 adds a quadratic falloff through connected air.

```java
import art.arcane.auram.vapor.Vapor;

var id = ResourceLocation.fromNamespaceAndPath("examplemod", "boiler");
Vapor.setEmitter(level, id, ventPos, 0.08f);
Vapor.setEmitter(level, id, ventPos, 0.16f); // Change the running emitter.
Vapor.stopEmitter(level, id, ventPos);
Vapor.pulse(level, ventPos, 0.8f, 5);
```

Use a stable combination of level, namespaced ID, and position. To move an
emitter, stop its old position and set the new one. Emitters suspend with their
chunk unloaded and expire when the level closes. Stop the emitter when its
owner is removed. A zero rate also stops it. Calls return whether the request
was accepted or an existing emitter was stopped. Sources and per-tick pulses
are bounded. Source state and simulated Vapor are transient and need no save.

## Optional Forge integration

Add this on your mod event bus during `InterModEnqueueEvent`. The callback uses
only Forge, Minecraft, and Java types. The no-op value works in a pack without
AuramCore. A server tick is a suitable place to invoke the callback.

```java
private static volatile BiConsumer<ServerLevel, CompoundTag> vapor = (level, request) -> {};

@SubscribeEvent
public static void enqueueVapor(InterModEnqueueEvent event) {
    InterModComms.sendTo("auram", "vapor_api_v1",
            () -> (Consumer<BiConsumer<ServerLevel, CompoundTag>>) bridge -> vapor = bridge);
}

static void puff(ServerLevel level, BlockPos vent) {
    var request = new CompoundTag();
    request.putString("operation", "pulse");
    request.putLong("pos", vent.asLong());
    request.putFloat("density", 0.8f);
    request.putInt("radius", 5);
    vapor.accept(level, request);
}
```

Use `operation` values `pulse`, `set`, or `stop`. All take `pos`. Set and stop
also take a stable `id` path owned by your mod's namespace. Pulse and set take
`density`. Pulse takes the optional `radius` described above. Keep the emitter
handle stable and send lifecycle changes when a machine changes state.
