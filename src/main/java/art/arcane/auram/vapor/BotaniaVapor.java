package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public final class BotaniaVapor {
    private static final ResourceLocation MANA_POOL = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "botania/mana_pool");
    private static final ResourceLocation ENDOFLAME = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "botania/endoflame");
    // Vapor broadcasts emitter snapshots every four ticks. Hold a transfer through one snapshot.
    private static final int TRANSFER_TICKS = 4;

    private BotaniaVapor() {}

    public static float poolFill(int mana, int capacity) {
        return capacity > 0 ? Mth.clamp((float) mana / capacity, 0f, 1f) : 0f;
    }

    public static void poolTransfer(ServerLevel level, BlockPos pos, int mana, int capacity) {
        float rate = Mth.lerp(poolFill(mana, capacity), 0.01f, 0.08f);
        emit(level, MANA_POOL, pos, pos, rate, TRANSFER_TICKS);
    }

    public static void endoflame(ServerLevel level, BlockPos anchor, BlockPos flower) {
        emit(level, ENDOFLAME, anchor, flower, 0.10f, 1);
    }

    private static void emit(ServerLevel level, ResourceLocation id, BlockPos anchor, BlockPos pos, float rate, int ticks) {
        BlockPos vent = pos;
        if (!VaporDemonstrators.open(level, vent)) {
            vent = pos.above();
            if (!VaporDemonstrators.open(level, vent)) return;
        }
        VaporSources.refresh(level, anchor, new VaporNetwork.Emitter(id, vent, rate), ticks);
    }
}
