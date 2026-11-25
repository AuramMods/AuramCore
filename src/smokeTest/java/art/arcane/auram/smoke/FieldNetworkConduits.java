package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

final class FieldNetworkConduits {
    private static final String ENDER_IO = "com.enderio.enderio.";

    private FieldNetworkConduits() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void configure(ServerLevel level) throws ReflectiveOperationException {
        var key = Class.forName(ENDER_IO + "api.EnderIORegistries$Keys").getField("CONDUIT").get(null);
        var variant = Class.forName(ENDER_IO + "init.EIOConduits").getField("VIBRANT_ENERGY").get(null);
        Registry registry = level.registryAccess().registryOrThrow((ResourceKey) key);
        Holder<?> energy = registry.getHolderOrThrow((ResourceKey) variant);
        var bundle = Class.forName(ENDER_IO + "content.conduits.bundle.ConduitBundleBlockEntity");
        var config = Class.forName(ENDER_IO + "content.conduits.type.energy.EnergyConduitConnectionConfig");
        var configType = Class.forName(ENDER_IO + "api.conduits.connection.config.ConnectionConfig");
        var redstone = Class.forName(ENDER_IO + "api.io.RedstoneControl");
        Method add = bundle.getMethod("addConduit", Holder.class, Direction.class, Player.class);
        Method connect = bundle.getMethod("setConnectionConfig", Holder.class, Direction.class, configType);
        for (int x = 44; x <= 52; x++) place(level, new BlockPos(x, 148, 28), energy, add);
        for (int z = 25; z <= 27; z++) place(level, new BlockPos(49, 148, z), energy, add);

        var defaults = config.getField("DEFAULT").get(null);
        var withExtract = config.getMethod("withIsExtract", boolean.class);
        var withInsert = config.getMethod("withIsInsert", boolean.class);
        Object input = withInsert.invoke(withExtract.invoke(defaults, false), true);
        Object output = withInsert.invoke(withExtract.invoke(defaults, true), false);
        output = config.getMethod("withExtractRedstoneControl", redstone)
                .invoke(output, redstone.getField("ALWAYS_ACTIVE").get(null));
        connect.invoke(level.getBlockEntity(new BlockPos(44, 148, 28)), energy, Direction.WEST, output);
        connect.invoke(level.getBlockEntity(new BlockPos(49, 148, 25)), energy, Direction.NORTH, input);
        connect.invoke(level.getBlockEntity(new BlockPos(52, 148, 28)), energy, Direction.EAST, input);
    }

    private static void place(ServerLevel level, BlockPos position, Holder<?> energy, Method add)
            throws ReflectiveOperationException {
        var tile = level.getBlockEntity(position);
        if (tile == null) throw new IllegalStateException("Missing Ender IO conduit at " + position);
        add.invoke(tile, energy, null, null);
    }
}
