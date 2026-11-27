package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.screen.AdaptScreen;
import art.arcane.adapt.client.screen.ScreenRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AdaptScreen.class, remap = false)
public interface AdaptScreenAccess {
    @Invoker("router")
    ScreenRouter auram$router();
}
