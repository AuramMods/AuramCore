package art.arcane.auram.smoke;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class StoneWorkRecipeParity {
    private StoneWorkRecipeParity() {}

    static void capture(IJeiRuntime runtime, int join) throws ReflectiveOperationException {
        var type = (RecipeType<?>) Class.forName("com.buuz135.industrial.plugin.jei.IndustrialRecipeTypes")
                .getField("STONE_WORK").get(null);
        var recipes = runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get().toList();
        if (recipes.isEmpty()) throw new IllegalStateException("StoneWork registered no JEI recipes");
        var wrapper = recipes.get(0).getClass();
        var getInput = wrapper.getMethod("getInput");
        var getOutput = wrapper.getMethod("getOutput");
        var getModes = wrapper.getMethod("getModes");
        var rows = new ArrayList<String>(recipes.size());
        for (var recipe : recipes) {
            var input = (ItemStack) getInput.invoke(recipe);
            var output = (ItemStack) getOutput.invoke(recipe);
            var modes = new ArrayList<String>();
            for (var mode : (List<?>) getModes.invoke(recipe)) {
                modes.add((String) mode.getClass().getMethod("getAction").invoke(mode));
            }
            rows.add(input.save(new CompoundTag()) + "\t" + String.join(",", modes) + "\t" + output.save(new CompoundTag()));
        }
        try {
            Files.write(Path.of("world-load-stonework-" + join + ".txt"), rows.stream().sorted().toList());
        } catch (IOException e) {
            throw new IllegalStateException("Could not save StoneWork recipe listings", e);
        }
    }
}
