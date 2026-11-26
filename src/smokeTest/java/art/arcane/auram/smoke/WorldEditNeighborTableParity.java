package art.arcane.auram.smoke;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Objects;

final class WorldEditNeighborTableParity {
    private WorldEditNeighborTableParity() {}

    static void run() throws Exception {
        WorldLoadCapture.mark("worldedit_first_use_begin");
        var adapter = Class.forName("com.sk89q.worldedit.forge.ForgeAdapter");
        var state = Class.forName("com.sk89q.worldedit.world.block.BlockState");
        var property = Class.forName("com.sk89q.worldedit.registry.state.Property");
        var toWorldEdit = adapter.getMethod("adapt", BlockState.class);
        var toMinecraft = adapter.getMethod("adapt", state);
        var with = state.getMethod("with", property, Object.class);

        verifyRegisteredIds(state);
        var redwood = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("regions_unexplored:redwood_stairs"));
        if (redwood == null) throw new IllegalStateException("The fixture requires Regions Unexplored redwood stairs");
        var vanilla = verifyNeighbors(Blocks.OAK_STAIRS.defaultBlockState(), toWorldEdit, toMinecraft, with);
        var modded = verifyNeighbors(redwood.defaultBlockState(), toWorldEdit, toMinecraft, with);
        verifySchematic(vanilla, modded);

        WorldLoadCapture.mark("worldedit_first_use_end");
    }

    private static void verifyRegisteredIds(Class<?> stateClass) throws ReflectiveOperationException {
        var blockType = Class.forName("com.sk89q.worldedit.world.block.BlockType");
        var registry = (Iterable<?>) blockType.getField("REGISTRY").get(null);
        var states = blockType.getMethod("getAllStates");
        var ids = Class.forName("com.sk89q.worldedit.internal.block.BlockStateIdAccess");
        var getId = ids.getMethod("getBlockStateId", stateClass);
        var byId = ids.getMethod("getBlockStateById", int.class);
        int count = 0;
        for (Object block : registry) {
            for (Object state : (Iterable<?>) states.invoke(block)) {
                int id = (int) getId.invoke(null, state);
                if (byId.invoke(null, id) != state) {
                    throw new IllegalStateException("WorldEdit state ID " + id + " did not round trip for " + block);
                }
                count++;
            }
        }
        System.out.println("WorldEdit state IDs round-tripped: " + count);
    }

    private static Object verifyNeighbors(BlockState original, Method toWorldEdit, Method toMinecraft, Method with)
            throws ReflectiveOperationException {
        Object block = toWorldEdit.invoke(null, original);
        Object type = block.getClass().getMethod("getBlockType").invoke(block);
        Method getProperty = type.getClass().getMethod("getProperty", String.class);
        Object facing = getProperty.invoke(type, "facing");
        Object shape = getProperty.invoke(type, "shape");
        Object canonical = block;
        for (var direction : StairBlock.FACING.getPossibleValues()) {
            Object changedDirection = with.invoke(block, facing, directionForWorldEdit(facing, direction.getName()));
            for (var form : StairBlock.SHAPE.getPossibleValues()) {
                Object result = with.invoke(changedDirection, shape, form.getSerializedName());
                BlockState expected = original.setValue(StairBlock.FACING, direction).setValue(StairBlock.SHAPE, form);
                if (toMinecraft.invoke(null, result) != expected) {
                    throw new IllegalStateException("WorldEdit state transition differed for " + expected);
                }
                canonical = result;
            }
        }
        if (with.invoke(block, facing, new Object()) != block) {
            throw new IllegalStateException("Invalid WorldEdit property value changed the block");
        }
        return canonical;
    }

    private static Object directionForWorldEdit(Object property, String name) throws ReflectiveOperationException {
        return property.getClass().getMethod("getValueFor", String.class).invoke(property, name);
    }

    private static void verifySchematic(Object vanilla, Object modded) throws Exception {
        var vector = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        var at = vector.getMethod("at", int.class, int.class, int.class);
        Object first = at.invoke(null, 0, 0, 0);
        Object second = at.invoke(null, 1, 0, 0);
        var region = Class.forName("com.sk89q.worldedit.regions.CuboidRegion")
                .getConstructor(vector, vector).newInstance(first, second);
        var clipboardType = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
        var arrayType = Class.forName("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard");
        var clipboard = arrayType.getConstructor(Class.forName("com.sk89q.worldedit.regions.Region")).newInstance(region);
        var blockHolder = Class.forName("com.sk89q.worldedit.world.block.BlockStateHolder");
        var set = arrayType.getMethod("setBlock", vector, blockHolder);
        set.invoke(clipboard, first, vanilla);
        set.invoke(clipboard, second, modded);

        var format = Class.forName("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat")
                .getField("SPONGE_SCHEMATIC").get(null);
        var formatType = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        var output = new ByteArrayOutputStream();
        try (var writer = (AutoCloseable) formatType.getMethod("getWriter", OutputStream.class).invoke(format, output)) {
            Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter")
                    .getMethod("write", clipboardType).invoke(writer, clipboard);
        }
        try (var reader = (AutoCloseable) formatType.getMethod("getReader", InputStream.class)
                .invoke(format, new ByteArrayInputStream(output.toByteArray()))) {
            var restored = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader")
                    .getMethod("read").invoke(reader);
            var get = restored.getClass().getMethod("getBlock", vector);
            if (!Objects.equals(vanilla, get.invoke(restored, first))
                    || !Objects.equals(modded, get.invoke(restored, second))) {
                throw new IllegalStateException("WorldEdit schematic changed a block state");
            }
            var holder = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            var extent = Class.forName("com.sk89q.worldedit.extent.Extent");
            var target = arrayType.getConstructor(Class.forName("com.sk89q.worldedit.regions.Region")).newInstance(region);
            Object paste = holder.getMethod("createPaste", extent)
                    .invoke(holder.getConstructor(clipboardType).newInstance(restored), target);
            paste.getClass().getMethod("to", vector).invoke(paste, first);
            var operation = paste.getClass().getMethod("build").invoke(paste);
            Class.forName("com.sk89q.worldedit.function.operation.Operations")
                    .getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                    .invoke(null, operation);
            if (!Objects.equals(vanilla, get.invoke(target, first))
                    || !Objects.equals(modded, get.invoke(target, second))) {
                throw new IllegalStateException("WorldEdit paste changed a block state");
            }
        }
    }
}
