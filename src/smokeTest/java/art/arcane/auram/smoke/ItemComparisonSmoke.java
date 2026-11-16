package art.arcane.auram.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.Arrays;
import java.util.function.Consumer;

/** Exercises the installed Tom's Storage update method; no synthetic identity-hit claims. */
public final class ItemComparisonSmoke {
    public static void run(MinecraftServer server) throws Exception {
        Class<?> stored = Class.forName("com.tom.storagemod.util.StoredItemStack");
        Constructor<?> copying = stored.getConstructor(ItemStack.class);
        Constructor<?> referencing = stored.getConstructor(ItemStack.class, long.class);
        Class<?> managerType = Class.forName("com.tom.storagemod.util.TerminalSyncManager");
        Method update = managerType.getMethod("update", Map.class, ServerPlayer.class, Consumer.class);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "AuramItems"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        List<String> results = new ArrayList<>();
        // Copies correspond to rebuilt storage snapshots; reference reuse is an
        // explicit upper-bound fixture, not an estimate of the live inventory.
        for (String mode : new String[]{"copied_snapshots", "reused_wrappers", "shared_stack_new_wrappers"}) {
            Object manager = managerType.getConstructor().newInstance();
            ItemStack[] stacks = new ItemStack[64];
            Object[] wrappers = new Object[64];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = new ItemStack(Items.DIAMOND_SWORD);
                stacks[i].getOrCreateTag().putInt("auram_variant", i);
                wrappers[i] = copying.newInstance(stacks[i]);
            }
            long calls = 0, hits = 0, nanos = 0;
            double[] timing = new double[7];
            for (int round = 0; round < 40 + 7 * 200; round++) {
                Map<Object, Long> snapshot = new HashMap<>();
                for (int i = 0; i < stacks.length; i++) {
                    Object key = switch (mode) {
                        case "reused_wrappers" -> wrappers[i];
                        case "shared_stack_new_wrappers" -> referencing.newInstance(stacks[i], 1L);
                        default -> copying.newInstance(stacks[i]);
                    };
                    snapshot.put(key, 64L + (round % 2));
                }
                if (round < 40) ComparisonProbe.start();
                long start = System.nanoTime();
                update.invoke(manager, snapshot, player, null);
                long elapsed = System.nanoTime() - start;
                if (round < 40) {
                    long[] counts = ComparisonProbe.stop();
                    if (round >= 10) { calls += counts[0]; hits += counts[1]; nanos += elapsed; }
                } else timing[(round - 40) / 200] += elapsed / 1e6;
            }
            if (mode.equals("copied_snapshots")) {
                check(calls > 0, "production capability comparisons were intercepted");
                check(hits == 0, "copied snapshots have no provider identity hits");
            }
            Arrays.sort(timing);
            String result = String.format(java.util.Locale.ROOT,
                    "{\"workload\":\"%s\",\"measured_updates\":30,\"entries\":64,\"provider_comparisons\":%d,\"identity_hits\":%d,\"instrumented_elapsed_ms\":%.6f,\"median_200_updates_ms\":%.6f}", mode, calls, hits, nanos/1e6, timing[3]);
            results.add(result);
            LogUtils.getLogger().info("[AURAM ITEMS] {}", result);
        }
        // Verify the installed overload contracts before considering any shortcut.
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        Object one = referencing.newInstance(first, 1L), two = referencing.newInstance(first, 2L);
        check(one.equals(two), "Object equality deliberately ignores quantity");
        check(!(boolean) stored.getMethod("equals", stored).invoke(one, two), "typed equality retains quantity");
        ItemStack other = first.copy();
        other.getOrCreateTag().putInt("different", 1);
        check(!one.equals(referencing.newInstance(other, 1L)), "different normal NBT is unequal");
        verifyMutableCapabilities();
        Files.writeString(Path.of("item-comparison-benchmark.json"), "{\"results\":["+String.join(",", results)+"]}\n");
    }
    private static void verifyMutableCapabilities() {
        Map<ItemStack, MutableCapability> attached = new IdentityHashMap<>();
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, (AttachCapabilitiesEvent<ItemStack> event) -> {
            if (event.getObject().hasTag() && event.getObject().getTag().getBoolean("auram_compare_cap")) {
                MutableCapability cap = new MutableCapability();
                attached.put(event.getObject(), cap);
                event.addCapability(ResourceLocation.tryBuild("auram_patch_smoke", "comparison"), cap);
            }
        });
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        first.getOrCreateTag().putBoolean("auram_compare_cap", true);
        check(first.areCapsCompatible(first), "same provider remains compatible");
        int selfGathers = attached.size();
        check(selfGathers == (Boolean.getBoolean("auram.smoke.identityCandidate") ? 0 : 1),
                "self comparison avoids capability gathering only in the candidate");
        ItemStack other = new ItemStack(Items.DIAMOND_SWORD);
        other.getOrCreateTag().putBoolean("auram_compare_cap", true);
        check(ItemStack.isSameItemSameTags(first, other), "distinct equivalent capabilities compare equal");
        check(attached.size() == 2, "distinct stacks initialize both providers");
        attached.get(first).value = 7;
        check(!ItemStack.isSameItemSameTags(first, other), "mutated capability remains unequal");
        attached.get(other).value = 7;
        check(ItemStack.isSameItemSameTags(first, other), "subsequent matching mutation is observed");
        first.setCount(2);
        check(ItemStack.isSameItemSameTags(first, other), "quantity semantics remain unchanged");
        LogUtils.getLogger().info("[AURAM ITEMS] Mutable capability checks passed; self comparison gathered {} providers", selfGathers);
    }
    private static final class MutableCapability implements ICapabilitySerializable<CompoundTag> {
        int value;
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) { return LazyOptional.empty(); }
        @Override public CompoundTag serializeNBT() { CompoundTag tag = new CompoundTag(); tag.putInt("value", value); return tag; }
        @Override public void deserializeNBT(CompoundTag tag) { value = tag.getInt("value"); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
