package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.WorldLoadCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.forge.ForgeWorldEdit", remap = false)
abstract class WorldEditStartupProbeMixin {
    @WrapMethod(method = "serverStarted")
    private void auram$timeWorldEditStartup(ServerStartedEvent event, Operation<Void> original) {
        WorldLoadCapture.mark("worldedit_startup_begin");
        try {
            original.call(event);
        } finally {
            WorldLoadCapture.mark("worldedit_startup_end");
        }
    }
}
