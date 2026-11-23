package art.arcane.auram.mixin.tweak;

import art.arcane.auram.tweak.ThreadScheduling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.Services;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.WorldStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerSchedulingMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void auram$setIntegratedPriority(Thread thread, Minecraft client, LevelStorageSource.LevelStorageAccess storage,
                                            PackRepository packs, WorldStem world, Services services,
                                            ChunkProgressListenerFactory progress, CallbackInfo callback) {
        ThreadScheduling.SETTINGS.configureIntegratedServer(thread);
    }
}
