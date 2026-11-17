package art.arcane.auram.smoke.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dry-run structure tests need the loaded registries, not 441 spawn chunks. */
@Mixin(MinecraftServer.class)
public abstract class ServerSpawnMixin {
    @Inject(method="prepareLevels",at=@At("HEAD"),cancellable=true)
    private void auramSmoke$skipSpawn(CallbackInfo ci) {
        String mode=System.getProperty("auram.smoke","");
        if(mode.equals("structureprobes") || mode.equals("terrain") || mode.equals("density") || mode.equals("heightcache") || mode.equals("mappingperf") || mode.equals("wrought") || mode.equals("sparse") || mode.equals("terrainbatch")) ci.cancel();
    }
}
