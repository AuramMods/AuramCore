package art.arcane.auram.util;

import art.arcane.auram.Auram;
import art.arcane.auram.item.GearItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@JeiPlugin
public class AuramJeiPlugin implements IModPlugin {

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return Objects.requireNonNull(ResourceLocation.tryBuild(Auram.MODID, "jei_plugin"));
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for(RegistryObject i : Auram.COMPONENTS) {
            RegistryObject<Item> it = (RegistryObject<Item>) i;
            
            registration.registerSubtypeInterpreter(it.get(), (stack, context) -> {
                if (stack.hasTag() && stack.getTag().contains(ComponentItem.NBT_MATERIAL_KEY)) {
                    return stack.getTag().getString(ComponentItem.NBT_MATERIAL_KEY);
                }
                return "";
            });
        }
    }
}