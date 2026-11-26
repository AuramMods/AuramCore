package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.StructureTemplateLoadSample;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

@Mixin(StructureTemplateManager.class)
abstract class StructureTemplateLoadProbeMixin {
    @Unique
    private static final boolean AURAM_TRACE_TEMPLATE_LOADS = Boolean.getBoolean("auram.smoke.traceStructureTemplateLoads");

    @WrapMethod(method = "tryLoad")
    private Optional<StructureTemplate> auram$sampleTemplateLoads(ResourceLocation id,
                                                                   Operation<Optional<StructureTemplate>> original) {
        if (!AURAM_TRACE_TEMPLATE_LOADS) return original.call(id);

        var sample = new StructureTemplateLoadSample();
        sample.begin();
        var template = original.call(id);
        sample.end();
        sample.resourceId = id.toString();
        sample.manager = System.identityHashCode(this);
        sample.found = template.isPresent();
        sample.commit();
        return template;
    }
}
