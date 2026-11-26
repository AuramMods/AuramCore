package art.arcane.auram.mixin.apotheosis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.client.AdventureModuleClient", remap = false)
abstract class BossSpawnBeamMixin {
    @Inject(method = "render(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private static void auram$hideBossSpawnBeam(CallbackInfo ci) {
        ci.cancel();
    }
}
