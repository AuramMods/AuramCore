package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.JoinPacketSample;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.net.SyncQuestsMessage", remap = false)
abstract class FtbQuestSyncProbeMixin {
    @WrapMethod(method = "write")
    private void auram$measureQuestDefinitionEncode(FriendlyByteBuf buffer, Operation<Void> original) {
        long start = System.nanoTime();
        int startIndex = buffer.writerIndex();
        original.call(buffer);
        if (JoinPacketSample.ENABLED) {
            System.out.println("AURAM_FTB_QUEST_SYNC encode_ns=" + (System.nanoTime() - start)
                    + " bytes=" + (buffer.writerIndex() - startIndex));
        }
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/BaseQuestFile;readNetDataFull(Lnet/minecraft/network/FriendlyByteBuf;)V"))
    private void auram$measureQuestDefinitionDecode(@Coerce Object file, FriendlyByteBuf buffer, Operation<Void> original) {
        long start = System.nanoTime();
        int startIndex = buffer.readerIndex();
        original.call(file, buffer);
        if (JoinPacketSample.ENABLED) {
            System.out.println("AURAM_FTB_QUEST_SYNC decode_ns=" + (System.nanoTime() - start)
                    + " bytes=" + (buffer.readerIndex() - startIndex));
        }
    }
}
