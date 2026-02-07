package art.arcane.auram.item;

import art.arcane.auram.Auram;
import art.arcane.auram.recipes.VirtualRecipe;
import art.arcane.auram.util.ComponentItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PlateItem extends Item implements ComponentItem  {
    public PlateItem() {
        super(new Properties());
    }

    @Override
    public @NotNull Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_CUSTOM_NAME)) {
            return Component.literal(tag.getString(NBT_CUSTOM_NAME) + " Plate");
        }
        return super.getName(stack);
    }

    @Override
    public String getRawName() {
        return "Plate";
    }

    @Override
    public ItemStack transform(String name, ResourceLocation materialKey) {
        ItemStack stack = new ItemStack(getRawItem());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(NBT_CUSTOM_NAME, name);
        tag.putString(NBT_MATERIAL_KEY, materialKey.toString());
        stack.setTag(tag);
        return stack;
    }

    @Override
    public VirtualRecipe getRecipe(ItemStack stack) {
        if(stack.getItem() != getRawItem()) {
            return null;
        }

        CompoundTag tag = stack.getTag();
        if(tag == null || !tag.contains(NBT_CUSTOM_NAME) || !tag.contains(NBT_MATERIAL_KEY)) {
            return null;
        }

        String name = tag.getString(NBT_CUSTOM_NAME);
        ResourceLocation materialKey = ResourceLocation.tryParse(tag.getString(NBT_MATERIAL_KEY));

        if(materialKey == null) {
            return null;
        }

        Item materialItem = BuiltInRegistries.ITEM.get(materialKey);
        Ingredient ing = Ingredient.of(materialItem);
        ItemStack output = transform(name, materialKey);
        List<Ingredient> ings = new ArrayList<>();

        for(int i = 0; i < 5; i++) {
            ings.add(ing);
        }

        Ingredient o = ing;
        Ingredient n = Ingredient.of(Items.IRON_NUGGET);
        Ingredient x = Ingredient.EMPTY;
        NonNullList<Ingredient> pattern = NonNullList.create();
        pattern.add(o); pattern.add(o); pattern.add(o);
        pattern.add(o); pattern.add(n); pattern.add(o);
        pattern.add(o); pattern.add(o); pattern.add(o);

        ResourceLocation id = ResourceLocation.parse("auram:plate_" + materialKey.toString().replaceAll(":", "_"));
        VirtualRecipe vr = new VirtualRecipe(
                id,
                RecipeType.CRAFTING.toString(),
                pattern,
                output,
                new ShapedRecipe(id,
                        "auram_plates",
                        CraftingBookCategory.MISC,
                        3,3,
                        pattern,
                        output,
                        false
                )
        );

        vr.isModified = true;
        vr.isCreated = true;
        return vr;
    }
}