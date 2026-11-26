package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ProjectEMapCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "moze_intel.projecte.emc.EMCMappingHandler", remap = false)
abstract class ProjectEMapProbeMixin {
    @WrapMethod(method = "map")
    private static void auram$measureProjectEMapping(ReloadableServerResources resources, RegistryAccess access,
                                                     ResourceManager manager, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(resources, access, manager);
        if (Boolean.getBoolean("auram.smoke.projecteMap")) ProjectEMapCapture.write(System.nanoTime() - start);
    }
}
