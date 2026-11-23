package art.arcane.auram.smoke;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FastAnimationSmoke {
    private FastAnimationSmoke() {
    }

    static void run(Minecraft client) throws Exception {
        recordHooks();
        var results = new LinkedHashMap<String, List<List<Float>>>();
        for (var type : List.of(EntityType.PARROT, EntityType.BEE, EntityType.WOLF, EntityType.HOGLIN,
                EntityType.SKELETON, EntityType.MAGMA_CUBE, EntityType.DROWNED, EntityType.BAT, EntityType.IRON_GOLEM,
                EntityType.TURTLE, EntityType.PIGLIN, EntityType.WARDEN)) {
            results.put(EntityType.getKey(type).toString(), poses(client, type));
        }
        Files.writeString(Path.of("auram-fastanim-smoke.json"), new Gson().toJson(results) + "\n");
    }

    private static void recordHooks() throws Exception {
        String prefix = ModList.get().isLoaded("faster_entity_animations")
                ? "net.lunade.fastanim.mixin." : "art.arcane.auram.mixin.fastanim.";
        boolean port = prefix.startsWith("art.arcane");
        Map<String, Integer> hooks = new LinkedHashMap<>();
        try (var stream = FastAnimationSmoke.class.getResourceAsStream("/auram-fastanim-targets.json");
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                var target = entry.getValue().getAsJsonObject();
                int expected = target.get("expected").getAsInt();
                Class<?> owner = Class.forName(target.get("target").getAsString(), false, Minecraft.class.getClassLoader());
                int applied = 0;
                for (var method : owner.getDeclaredMethods()) {
                    var metadata = method.getAnnotation(MixinMerged.class);
                    if (metadata != null && metadata.mixin().equals(prefix + entry.getKey())) applied++;
                }
                hooks.put(entry.getKey(), applied);
                if (port && applied < expected) {
                    throw new AssertionError("Missing FastAnim hooks on " + owner.getName() + ": " + applied + "/" + expected);
                }
            }
        }
        Files.writeString(Path.of("auram-fastanim-hooks.json"), new Gson().toJson(hooks) + "\n");
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> List<List<Float>> poses(Minecraft client, EntityType<T> type) throws Exception {
        T mob = type.create(client.level);
        if (mob == null) throw new AssertionError("Could not create " + type);

        var renderer = (LivingEntityRenderer<?, ?>) client.getEntityRenderDispatcher().getRenderer(mob);
        var model = (EntityModel<T>) renderer.getModel();
        List<ModelPart> parts = modelParts(model);
        if (parts.isEmpty()) throw new AssertionError("No pose parts for " + type);

        List<List<Float>> snapshots = new ArrayList<>();
        for (int frame = 0; frame < 18; frame++) {
            mob.tickCount = frame * 3;
            mob.setOnGround(frame % 3 != 0);
            if (mob instanceof Parrot parrot) parrot.setInSittingPose(frame % 3 == 2);
            float stride = frame * 0.7F;
            float speed = (frame % 7) * 0.13F;
            model.prepareMobModel(mob, stride, speed, 0.5F);
            model.setupAnim(mob, stride, speed, mob.tickCount + 0.5F, 40 - frame * 5.5F, 12.3F - frame * 2.0F);
            List<Float> snapshot = new ArrayList<>();
            for (ModelPart part : parts) {
                for (float value : new float[]{part.x, part.y, part.z, part.xRot, part.yRot, part.zRot}) {
                    if (!Float.isFinite(value)) throw new AssertionError("Non-finite pose for " + type);
                    snapshot.add(value);
                }
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private static List<ModelPart> modelParts(EntityModel<?> model) throws Exception {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == ModelPart.class) fields.add(field);
            }
        }
        fields.sort(Comparator.comparing(field -> field.getDeclaringClass().getName() + "#" + field.getName()));
        List<ModelPart> parts = new ArrayList<>();
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.get(model) instanceof ModelPart part) parts.add(part);
        }
        return parts;
    }
}
