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

## Built-in emitters

| Source | Density |
| --- | --- |
| Furnace, blast furnace, or smoker while cooking | 0.14 per tick |
| Modded furnace blocks using the vanilla burn ticker, Ender IO Stirling generator and primitive alloy smelter, Thermal Stirling dynamo, Extra Utilities furnace-fuel generators, and Titanium generators actively burning Forge furnace fuel | 0.12 per burning tick |
| Machine accepting energy through an observed RF route or shared Ender IO, Thermal, or Titanium energy storage | 0.05 per tick at half the logarithmic scale (about 10,000 RF/t), rising to 0.15 at 100,000,000 RF/t |
| Fire | 0.32 per tick |
| Lit campfire or soul campfire | 0.65 per tick |
| Exposed magma block | 0.25 per sampled puff in the air above it |
| Regular or soul torch, including wall torches | 0.03 per sampled puff |
| TNT or large ghast fireball explosion | 1.0 at the center, with a 7-block falloff |
| Living entity death | 1.0 per open floor cell, with 1–4 attempts based on maximum HP. For example, 5, 20, 40, and 100 HP get 1, 2, 3, and 4 attempts respectively. |
| Burning entity | 0.15 per tick |
| Tinkers' seared heater | 0.12 per tick from its front while burning fuel |
| Tinkers' smeltery or foundry | 0.12 per tick per open interior floor cell while the tank holds fluid |
| Tinkers' drain faucet | 0.08 per tick while it transfers fluid |
| Tinkers' casting table, basin, or channel | 0.08 per tick while casting fills or cools, or while the channel holds fluid |
| Tinkers' melter or alloyer | 0.08 per tick above it while fueled or holding hot fluid |
| Ender IO SAG Mill, Alloy Smelter, Slice'N'Splice, Painting Machine, Soul Engine, Farming Station, Vat, Fluid Drain, Vacuum Chest, and XP Vacuum | 0.08 per operating tick |
| Ender IO Crafter, Impulse Hopper, and Niard | One 0.08 puff per successful operation |

The client tries 32 random block positions per tick within eight blocks
horizontally and four blocks vertically of the camera. It also samples nearby
burning entities from their synced fire state.

RF intake uses a four-tick average of committed transfers at the receiving
block. Add additional transport blocks to `auram:rf_transport` to exclude
them from intake Vapor. Forge fuel queries report the burn value of an item.
The Titanium integration looks for that query during actual fuel consumption
and uses the generator's burn callback for the lifetime of the fuel.

Integrations can call `RfFlow.recordAcceptedInput(machine, accepted)` after a
committed receive or `MachineVapor.burning(machine)` on each burning server tick.

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
