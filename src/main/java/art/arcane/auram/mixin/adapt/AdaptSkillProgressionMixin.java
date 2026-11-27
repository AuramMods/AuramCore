package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.screen.CatalogIndex;
import art.arcane.adapt.client.screen.SkillScreen;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.auram.client.menu.SkillTreeProgression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = SkillScreen.class, remap = false)
abstract class AdaptSkillProgressionMixin {
    @WrapOperation(method = {"rebuildGroups", "refresh"}, at = @At(value = "INVOKE", target =
            "Lart/arcane/adapt/client/screen/CatalogIndex;groups(Lart/arcane/adapt/client/view/PlayerView;Ljava/lang/String;)Ljava/util/List;"))
    private List<List<AdaptationView>> auram$hideUnrevealedAdaptations(CatalogIndex catalog, PlayerView player,
                                                                     String skill, Operation<List<List<AdaptationView>>> original) {
        return SkillTreeProgression.reveal(player, original.call(catalog, player, skill));
    }
}
