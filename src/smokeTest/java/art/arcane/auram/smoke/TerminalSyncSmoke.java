package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Paired real terminal updates, byte-identical packets, mutable capabilities. */
public final class TerminalSyncSmoke {
    public static void run(MinecraftServer server) throws Exception {
        Map<ItemStack,Payload> payloads=new WeakHashMap<>();
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class,(AttachCapabilitiesEvent<ItemStack> event)-> {
            ResourceLocation id=ForgeRegistries.ITEMS.getKey(event.getObject().getItem());
            if(id!=null && id.getNamespace().equals("mekanism")) {
                Payload payload=new Payload();payloads.put(event.getObject(),payload);
                event.addCapability(ResourceLocation.tryParse("auram_patch_smoke:terminal_payload"),payload);
            }
        });
        ItemStack[] inventory=new ItemStack[128];int energized=0;
        String[] types={"energy_tablet","atomic_disassembler","configurator","electric_bow"};
        for(int i=0;i<inventory.length;i++) {
            var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("mekanism:"+types[i%types.length]));
            check(item!=null,"installed Mekanism item "+types[i%types.length]);
            ItemStack stack=new ItemStack(item);stack.getOrCreateTag().putInt("auram_variant",i);
            stack.getOrCreateTag().putByteArray("packet_payload",new byte[768]);
            var energy=stack.getCapability(ForgeCapabilities.ENERGY).resolve();
            if(energy.isPresent()) { energy.get().receiveEnergy(10000+i,false);energized++; }
            check(payloads.containsKey(stack),"real item gathered its extra mutable capability");
            inventory[i]=stack;
        }
        check(energized>=64,"capability-heavy fixture includes real powered items");
        Class<?> stored=Class.forName("com.tom.storagemod.util.StoredItemStack");
        Constructor<?> copy=stored.getConstructor(ItemStack.class);
        Class<?> terminal=Class.forName("com.tom.storagemod.util.TerminalSyncManager");
        Method update=terminal.getMethod("update",Map.class,ServerPlayer.class,Consumer.class);
        Method receive=terminal.getMethod("receiveUpdate",CompoundTag.class);
        Method asList=terminal.getMethod("getAsList");
        Field ids=terminal.getDeclaredField("idMap");ids.setAccessible(true);
        Object control=terminal.getConstructor().newInstance(),candidate=terminal.getConstructor().newInstance();
        // The production guard deliberately falls back for custom maps. This
        // behavior-identical subclass executes the original second lookup and
        // permits paired reference/candidate packets with identical object hashes.
        ids.set(control,new Object2IntOpenHashMap<Object>() {});
        Object clientA=terminal.getConstructor().newInstance(),clientB=terminal.getConstructor().newInstance();
        ServerPlayer player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"AuramTerminal"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player) {
            @Override public void send(Packet<?> packet) { }
        };
        long packets=0;int paired=0;
        TerminalPacketProbe.overflowWrites=0;
        for(int step=0;step<60;step++) {
            if(step==12) payloads.get(inventory[3]).value=71; // Same ordinary NBT, different capability contents.
            if(step==18) inventory[7].getOrCreateTag().putString("changed","observed");
            if(step==24) inventory[8].getCapability(ForgeCapabilities.ENERGY).ifPresent(e->e.extractEnergy(27,false));
            if(step==30) inventory[11].getOrCreateTag().putByteArray("overflow",new byte[130000]);
            if(step==32) inventory[11].getOrCreateTag().remove("overflow");
            Map<Object,Long> snapshot=snapshot(inventory,copy,step);
            TerminalPacketProbe.start();
            update.invoke(control,snapshot,player,(Consumer<CompoundTag>)(tag->tag.putInt("fixture_callback",17)));
            var expected=TerminalPacketProbe.stop();
            TerminalPacketProbe.start();
            update.invoke(candidate,snapshot,player,(Consumer<CompoundTag>)(tag->tag.putInt("fixture_callback",17)));
            var actual=TerminalPacketProbe.stop();
            check(expected.equals(actual),"byte-identical split/overflow/removal/capability packets at step "+step);
            for(CompoundTag packet:expected) receive.invoke(clientA,packet);
            for(CompoundTag packet:actual) receive.invoke(clientB,packet);
            check(clientState(asList.invoke(clientA),stored).equals(clientState(asList.invoke(clientB),stored)),"client state at step "+step);
            packets+=actual.size();paired++;
        }
        check(packets>paired,"packet splitting actually executed");
        check(TerminalPacketProbe.overflowWrites>=2,"both arms executed oversized-item fallback");
        double[] oldTimes=new double[25],newTimes=new double[25];long oldCalls=0,newCalls=0;
        for(int batch=-5;batch<25;batch++) {
            double oldTime=0,newTime=0;
            for(int iteration=0;iteration<10;iteration++) {
                Map<Object,Long> snapshot=snapshot(inventory,copy,1000+10*batch+iteration);
                // Gather copied providers before timing either arm; otherwise the
                // arm executed first would pay the other's one-time initialization.
                for(Object key:snapshot.keySet()) {
                    ItemStack stack=(ItemStack)stored.getMethod("getStack").invoke(key);
                    stack.areCapsCompatible(stack);
                }
                for(int order=0;order<2;order++) {
                    boolean reference=(batch+iteration+order)%2==0;
                    ComparisonProbe.start();long start=System.nanoTime();
                    update.invoke(reference?control:candidate,snapshot,player,null);
                    double ms=(System.nanoTime()-start)/1e6;
                    long[] counts=ComparisonProbe.stop();
                    if(reference) { oldTime+=ms;if(batch>=0)oldCalls+=counts[0]; }
                    else { newTime+=ms;if(batch>=0)newCalls+=counts[0]; }
                }
            }
            if(batch>=0) {oldTimes[batch]=oldTime;newTimes[batch]=newTime;}
        }
        if(Boolean.getBoolean("auram.smoke.terminalCandidate")) check(newCalls<oldCalls,"production mixin removes real capability comparisons");
        double[] a=oldTimes.clone(),b=newTimes.clone();Arrays.sort(a);Arrays.sort(b);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("inventory_entries",inventory.length);result.put("real_energy_items",energized);
        result.put("paired_packet_cases",paired);result.put("packets_compared",packets);
        result.put("overflow_fallbacks",TerminalPacketProbe.overflowWrites);
        result.put("control_provider_comparisons",oldCalls);result.put("candidate_provider_comparisons",newCalls);
        result.put("control_median_10_updates_ms",a[12]);result.put("candidate_median_10_updates_ms",b[12]);
        result.put("control_batch_ms",oldTimes);result.put("candidate_batch_ms",newTimes);
        result.put("live_terminal", exerciseLiveTerminal(server, player, terminal, receive, asList, stored, inventory));
        result.put("scope","128 real Mekanism powered items plus mutable capability payloads; alternating paired update order; 5 warmup and 25 measured batches.");
        Files.writeString(Path.of("terminal-benchmark.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        LogUtils.getLogger().info("[AURAM TERMINAL] PASS {} paired packet checks and 2 live viewers", paired);
    }

    private static Map<String, Object> exerciseLiveTerminal(MinecraftServer server, ServerPlayer first,
            Class<?> syncType, Method receive, Method asList, Class<?> stored, ItemStack[] inventory) throws Exception {
        var level = server.overworld();
        BlockPos pos = level.getSharedSpawnPos().above(16);
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("toms_storage:ts.storage_terminal")).orElseThrow();
        var state = block.defaultBlockState();
        var facing = (net.minecraft.world.level.block.state.properties.DirectionProperty)
                block.getStateDefinition().getProperty("facing");
        state = state.setValue(facing, Direction.NORTH);
        level.setBlockAndUpdate(pos, state);
        var tile = level.getBlockEntity(pos);
        check(tile instanceof net.minecraft.world.MenuProvider, "real terminal was placed");
        var update = tile.getClass().getMethod("updateServer");
        var snapshot = tile.getClass().getMethod("getStacks");
        BlockPos chestPos = pos.north();
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(chestPos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        var hopper = (HopperBlockEntity) level.getBlockEntity(chestPos.above());
        for (int slot = 0; slot < 24; slot++) chest.setItem(slot, inventory[slot].copy());
        var tagged = new ItemStack(Items.STONE, 3);
        tagged.getOrCreateTag().putInt("variant", 10);
        chest.setItem(24, tagged);
        chest.setItem(25, new ItemStack(Items.STONE, 2));
        hopper.setItem(0, tagged.copyWithCount(4));
        ServerPlayer second = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "AuramTerminal2"));
        second.connection = new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),second) {
            @Override public void send(Packet<?> packet) { }
        };
        var provider = (net.minecraft.world.MenuProvider) tile;
        AbstractContainerMenu menuA = provider.createMenu(1, first.getInventory(), first);
        AbstractContainerMenu menuB = provider.createMenu(2, second.getInventory(), second);
        first.containerMenu = menuA;
        second.containerMenu = menuB;
        Object clientA = syncType.getConstructor().newInstance();
        Object clientB = syncType.getConstructor().newInstance();
        var states = new ArrayList<Map<String, Long>>();
        for (int step = 0; step < 4; step++) {
            if (step == 1) {
                HopperBlockEntity.pushItemsTick(level, hopper.getBlockPos(), hopper.getBlockState(), hopper);
                check(chest.getItem(24).getCount() == 4, "hopper inserted a tagged stack into the chest");
            }
            if (step == 2) chest.setItem(25, ItemStack.EMPTY);
            snapshot.invoke(tile);
            update.invoke(tile);
            for (var menu : List.of(menuA, menuB)) {
                TerminalPacketProbe.start();
                menu.broadcastChanges();
                var packets = TerminalPacketProbe.stop();
                if (step != 3) check(!packets.isEmpty(), "a viewer did not receive the inventory update");
                for (CompoundTag packet : packets) receive.invoke(menu == menuA ? clientA : clientB, packet);
            }
            var observed = clientState(asList.invoke(clientA), stored);
            check(observed.equals(clientState(asList.invoke(clientB), stored)), "live viewers received different inventory states");
            check(observed.get("minecraft:stone:{variant:10}") == (step == 0 ? 3L : 4L), "tagged count changed");
            check(observed.getOrDefault("minecraft:stone:null", 0L) == (step < 2 ? 2L : 0L), "removal or stack distinction changed");
            states.add(observed);
        }
        var batches = new double[21];
        for (int batch = -5; batch < batches.length; batch++) {
            long start = System.nanoTime();
            for (int refresh = 0; refresh < 300; refresh++) {
                snapshot.invoke(tile);
                update.invoke(tile);
            }
            if (batch >= 0) batches[batch] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(batches);
        return Map.of("views", 2, "paired_states", states, "median_300_rebuilds_ms", batches[10]);
    }

    private static Map<Object,Long> snapshot(ItemStack[] stacks,Constructor<?> copy,int step) throws Exception {
        Map<Object,Long> values=new LinkedHashMap<>();
        for(int i=0;i<stacks.length;i++) {
            if(step>=7 && step<10 && i%5==0) continue;
            values.put(copy.newInstance(stacks[i]),step==5?0L:step==55?Long.MAX_VALUE-i:64L+(step%2));
        }
        if(step>=45 && step<48) values.put(copy.newInstance(new ItemStack(Items.STONE)),64L+step%2);
        return values;
    }
    private static Map<String,Long> clientState(Object list,Class<?> stored) throws Exception {
        Map<String,Long> state=new TreeMap<>();
        for(Object entry:(List<?>)list) {
            ItemStack stack=(ItemStack)stored.getMethod("getStack").invoke(entry);
            long quantity=(long)stored.getMethod("getQuantity").invoke(entry);
            state.merge(ForgeRegistries.ITEMS.getKey(stack.getItem())+":"+stack.getTag(),quantity,Long::sum);
        }
        return state;
    }
    private static final class Payload implements ICapabilitySerializable<CompoundTag> {
        int value;
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {return LazyOptional.empty();}
        @Override public CompoundTag serializeNBT() {
            CompoundTag tag=new CompoundTag();int[] data=new int[64];
            for(int i=0;i<data.length;i++) data[i]=value+i;
            tag.putInt("value",value);tag.putIntArray("payload",data);return tag;
        }
        @Override public void deserializeNBT(CompoundTag tag) {value=tag.getInt("value");}
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
