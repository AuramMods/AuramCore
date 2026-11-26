package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.MinimapZoom;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = FTBChunksClientConfig.class, remap = false)
abstract class MinimapZoomConfigMixin {
    @ModifyArg(method = "<clinit>", index = 2,
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=zoom"),
                    to = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target =
                            "Ldev/ftb/mods/ftbchunks/client/FTBChunksClientConfig;MINIMAP_ZOOM:Ldev/ftb/mods/ftblibrary/snbt/config/DoubleValue;")),
            at = @At(value = "INVOKE", target =
                    "Ldev/ftb/mods/ftblibrary/snbt/config/SNBTConfig;addDouble(Ljava/lang/String;DDD)Ldev/ftb/mods/ftblibrary/snbt/config/DoubleValue;"))
    private static double auram$moreZoomOut(double original) {
        return MinimapZoom.EXTRA_WIDE_ZOOM;
    }
}
