package art.arcane.auram.mixin.minecraft;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Beardifier.class)
public interface BeardifierContentsAccess {
    @Accessor("pieceIterator") ObjectListIterator<?> auram$pieces();
    @Accessor("junctionIterator") ObjectListIterator<?> auram$junctions();
}
