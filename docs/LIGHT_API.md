# Colored light sources

AuramCore lets mods add colored light to nearby blocks and Vapor through
AuramBliss's **FloodFill** option. Enable **Fog Light Propagation** to see
that light scatter in fog and Vapor. The same source works in the raster and
compute light volumes.

Call on the thread that owns the supplied level. Use a server level for a
light shared with nearby players or a client level for a cosmetic effect such
as a particle. A client-side light stays local to that player.

```java
import art.arcane.auram.light.Light;

ResourceLocation lamp = ResourceLocation.fromNamespaceAndPath("example", "lamp");
Light.setEmitter(level, lamp, pos, 0x72B9FF, 0.20f);
Light.setEmitter(level, lamp, pos, 0xFF8833, 0.35f);
Light.pulse(level, lamp, pos, 0xFF8833, 0.65f, 20);
Light.stopEmitter(level, lamp, pos);
```

An emitter is keyed by its level, namespaced ID, and block position. Setting
it again changes its color and brightness. A pulse replaces the same emitter
and fades linearly from the given strength to zero over 1–1200 game ticks.
Use a separate ID to flash over a steady light. Remove the old position when
moving a source to another block. Zero strength or black removes the source.
Sources are cleared on chunk unload or level unload. Recreate persistent
machine lights when their chunk loads.

Colors are sRGB integers in `0xRRGGBB` format. Strength ranges from zero to
one. Try `0.10f` for a subtle glow. If several sources occupy a voxel, the
strongest source supplies its color. The renderer uses a 64-block-wide window.
Lights are visual and have no effect on vanilla block light, spawning, or
gameplay. Calls return whether the source was accepted or removed.

## Optional Forge integration

On the mod event bus, send the same style of bridge request used by Vapor and
Wind. If AuramCore is absent, the callback is never set.

```java
private static BiConsumer<Level, CompoundTag> light;

@SubscribeEvent
public static void enqueue(InterModEnqueueEvent event) {
    InterModComms.sendTo("auram", "light_api_v1",
        () -> (Consumer<BiConsumer<Level, CompoundTag>>) bridge -> light = bridge);
}

static void flash(Level level, BlockPos pos) {
    if (light == null) return;
    CompoundTag request = new CompoundTag();
    request.putString("operation", "pulse");
    request.putString("id", "lamp"); // The bridge scopes this path to the calling mod.
    request.putLong("pos", pos.asLong());
    request.putInt("rgb", 0x72B9FF);
    request.putFloat("strength", 0.4f);
    request.putInt("durationTicks", 20);
    light.accept(level, request);
}
```

Use operation `set` for a steady light and omit `durationTicks`. Operation
`stop` only needs the ID and position.
