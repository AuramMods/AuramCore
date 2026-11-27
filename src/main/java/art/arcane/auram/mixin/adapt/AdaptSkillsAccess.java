package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.screen.SkillsScreen;
import art.arcane.adapt.client.view.SkillView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(value = SkillsScreen.class, remap = false)
public interface AdaptSkillsAccess {
    @Invoker("seatedSkills")
    List<SkillView> auram$visibleSkills();
}
