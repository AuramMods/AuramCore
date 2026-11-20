package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SurfaceMapBenchSmoke;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dry-run structure tests need the loaded registries, not 441 spawn chunks. */
@Mixin(MinecraftServer.class)
public abstract class ServerSpawnMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void auramSmoke$recordLevelStartup(CallbackInfo ci) {
        SurfaceMapBenchSmoke.levelStarted();
    }

    @Inject(method="prepareLevels",at=@At("HEAD"),cancellable=true)
    private void auramSmoke$skipSpawn(CallbackInfo ci) {
        SurfaceMapBenchSmoke.spawnStarted();
        String mode=System.getProperty("auram.smoke","");
        if (mode.equals("book") || mode.equals("dhpriority-server") || mode.equals("ftbrtp") || mode.startsWith("movementpacket") || mode.equals("wellproximity") || mode.equals("chunkproviding") || mode.equals("zetamap")
                || mode.equals("starterbooks") || mode.equals("lithostarts") || mode.equals("aquiferparity") || mode.equals("surfacejitter") || mode.equals("surfaceterrain") || mode.startsWith("amlwater") || mode.startsWith("fluids") || mode.startsWith("biomes")) { ci.cancel(); return; }
        if(mode.equals("tuning") || mode.equals("structureprobes") || mode.equals("terrain") || mode.equals("density") || mode.equals("heightcache") || mode.equals("mappingperf") || mode.equals("wrought") || mode.equals("sparse") || mode.equals("terrainbatch")) ci.cancel();
    }
    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void auramSmoke$recordSpawnReady(CallbackInfo ci) {
        SurfaceMapBenchSmoke.spawnFinished();
    }
}
