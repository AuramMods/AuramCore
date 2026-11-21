package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

final class BasinFluidSmoke {
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var source = level.getChunkSource();
        var west = new ChunkPos(128, -128);
        var east = new ChunkPos(west.x + 1, west.z);
        var center = new BlockPos(east.getMinBlockX(), 80, east.getMiddleBlockZ());
        level.getChunk(west.x, west.z);
        var edge = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("psychedelicraft", "mash_tub_edge"));
        var tub = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("psychedelicraft", "mash_tub"));
        var mash = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("psychedelicraft", "wheat"));
        check(edge != null && tub != null && mash != null, "Psychedelicraft tub blocks and wheat mash are installed");
        var offset = (IntegerProperty) edge.getStateDefinition().getProperty("offset");
        level.setBlock(center.west(), edge.defaultBlockState().setValue(offset, 1), Block.UPDATE_KNOWN_SHAPE);

        // Invoke just the injected eye probe so vanilla's independent eye query is outside the assertion.
        var eyeProbe = Arrays.stream(Entity.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("$psychedelicraft$basinEyes")).findFirst().orElseThrow();
        eyeProbe.setAccessible(true);
        var callback = new CallbackInfo("basinEyes", false);

        source.addRegionTicket(TicketType.FORCED, east, 0, east);
        try {
            ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
            check(source.hasChunk(east.x, east.z) && source.getChunkNow(east.x, east.z) == null, "east FULL is pending");
            PatchSmokeMod.forbiddenChunkLookups.add(east.toLong());
            try {
                var body = new ArmorStand(level, center.getX() - 0.10, center.getY() + 0.2, center.getZ() + 0.5);
                body.updateFluidHeightAndDoFluidPushing(state -> true);
                check(!body.isInFluidType(), "the pending sample and tub center contribute no fluid");
                for (double x : new double[]{center.getX() - 0.2, center.getX() + 0.2}) {
                    var eyes = new ArmorStand(level, x, center.getY(), center.getZ() + 0.5);
                    eyes.setPos(x, center.getY() + 0.5 - eyes.getEyeHeight(), center.getZ() + 0.5);
                    eyeProbe.invoke(eyes, callback);
                    check(!eyes.isEyeInFluidType(ForgeMod.WATER_TYPE.get()), "pending eye sample reports no basin water");
                }
            } finally {
                PatchSmokeMod.forbiddenChunkLookups.remove(east.toLong());
            }

            level.getChunk(east.x, east.z);
            level.setBlock(center, tub.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
            var vat = level.getBlockEntity(center);
            check(vat != null, "cross-border tub has a center block entity");
            var tank = vat.getClass().getMethod("getTank").invoke(vat);
            var replace = tank.getClass().getMethod("replace", FluidStack.class);
            for (Fluid liquid : List.of(Fluids.WATER, Fluids.LAVA, mash)) {
                replace.invoke(tank, new FluidStack(liquid, 32000));
                var expected = liquid == Fluids.LAVA ? ForgeMod.LAVA_TYPE.get() : ForgeMod.WATER_TYPE.get();
                double x = center.getX() - 0.2;
                double z = center.getZ() + 0.5;
                var body = new ArmorStand(level, x, center.getY() + 0.2, z);
                body.updateFluidHeightAndDoFluidPushing(state -> true);
                check(body.getFluidTypeHeight(expected) > 0, "loaded wall resolves the center's " + liquid);
                var eyes = new ArmorStand(level, x, center.getY(), z);
                eyes.setPos(x, center.getY() + 0.5 - eyes.getEyeHeight(), z);
                eyeProbe.invoke(eyes, callback);
                check(eyes.isEyeInFluidType(expected), "loaded wall gives eyes the center's " + liquid);
            }
        } finally {
            source.removeRegionTicket(TicketType.FORCED, east, 0, east);
        }
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
