package art.arcane.auram.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class AuramMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.equals("art.arcane.auram.mixin.relics.FluidCollisionMixin")) {
            return true;
        }
        // This mixin targets vanilla Entity, so @Pseudo cannot make it optional.
        // Read the Relics class bytes without loading its mixin class.
        try {
            MixinService.getService().getBytecodeProvider()
                    .getClassNode("it.hurts.sskirillss.relics.mixin.EntityMixin");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Relics fluid collision mixin", e);
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
