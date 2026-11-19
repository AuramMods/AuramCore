package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual installed functions plus live selector semantics; no persistent NBT cache. */
public final class SelectorSmoke {
    public static void run(MinecraftServer server) throws Exception {
        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000101"), "AuramSelectors"));
        List<String> actionBars = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundSetActionBarTextPacket bar) actionBars.add(bar.getText().getString());
            }
        };
        player.moveTo(8,-62,8,0,0);
        var players=(art.arcane.auram.smoke.mixin.PlayerListAccess)server.getPlayerList();
        players.auramSmoke$players().add(player);
        players.auramSmoke$playersByUUID().put(player.getUUID(),player);
        server.overworld().addNewPlayer(player);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        int comparisons = semantics(player, source);
        ServerFunctionManager manager = server.getFunctions();
        // Execute the installed load tag before profiling its tick functions.
        for(CommandFunction load : manager.getTag(ResourceLocation.tryParse("minecraft:load")))
            manager.execute(load, manager.getGameLoopSender());
        populateInventory(player);
        // A realistic mix of non-player entities exercises the broad @e queries.
        for(int i=0;i<32;i++) {
            ArmorStand stand=new ArmorStand(server.overworld(),8+(i%4),-62,8+(i/4));
            stand.setNoGravity(true);
            stand.addTag("auram_selector_fixture");
            server.overworld().addFreshEntity(stand);
        }
        boolean droppedItems = Boolean.getBoolean("auram.smoke.gunsDroppedItems");
        int dropComparisons = droppedItems ? GunsDroppedItemSmoke.prepare(player, source) : 0;
        List<Map<String,Object>> functions=new ArrayList<>();
        for(String id : new String[]{"ggunz:loop","ggunz:utilities/player_tick","ggunz:utilities/scopes",
                "ggunz:reload/timer","scorched:tick","incendium:clocks/main"}) {
            Optional<CommandFunction> found=manager.get(ResourceLocation.tryParse(id));
            check(found.isPresent(),"installed function compiled: "+id);
            CommandFunction function=found.get();
            // Attribute nested command execution to concrete function IDs using
            // vanilla's tracing API, rather than guessing from archive size.
            FunctionTrace trace=new FunctionTrace(function.getId());
            SelectorProbe.start();
            manager.execute(function,source,trace);
            long tracedSaves=SelectorProbe.stop();
            double[] times=new double[25];
            long saves=0;
            for(int batch=-5;batch<25;batch++) {
                SelectorProbe.start();
                long start=System.nanoTime();
                for(int i=0;i<20;i++) manager.execute(function,source);
                double elapsed=(System.nanoTime()-start)/1e6;
                long count=SelectorProbe.stop();
                if(batch>=0) { times[batch]=elapsed;saves+=count; }
            }
            double[] sorted=times.clone();Arrays.sort(sorted);
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("function",id);result.put("measured_calls",500);result.put("entity_serializations",saves);
            result.put("median_20_calls_ms",sorted[12]);result.put("p95_20_calls_ms",sorted[23]);
            result.put("batch_ms",times);result.put("traced_serializations",tracedSaves);
            result.put("trace_by_function",trace.metrics);
            result.put("serializations_by_command", trace.serializationsByCommand);
            functions.add(result);
            LogUtils.getLogger().info("[AURAM SELECTORS] {} saves={} median={}ms",id,saves,sorted[12]);
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("semantic_comparisons",comparisons);result.put("functions",functions);
        result.put("scope","Installed datapacks, one live server player with tagged inventory and 32 armor stands; not a hosted-server speedup estimate.");
        if (droppedItems) {
            result.put("dropped_item_comparisons", dropComparisons);
            result.put("guns_dropped_items", GunsDroppedItemSmoke.exercise(player, source));
        }
        if (Boolean.getBoolean("auram.smoke.gunsOffhand")) {
            result.put("guns_offhand", GunsOffhandSmoke.run(player, source, actionBars));
        }
        Files.writeString(Path.of("selector-benchmark.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
    }

    private static int semantics(ServerPlayer player,CommandSourceStack source) throws Exception {
        String[] patterns={"{}","{SelectedItem:{}}","{SelectedItem:{id:\"minecraft:stone\"}}",
                "{SelectedItem:{Count:1b}}","{SelectedItem:{Count:2b}}","{SelectedItem:{Count:1}}",
                "{SelectedItem:{tag:{CustomModelData:666666}}}","{SelectedItem:{tag:{CustomModelData:666666.0d}}}",
                "{SelectedItem:[]}","{SelectedItem:\"wrong_type\"}","{SelectedItem:{},OnGround:0b}",
                "{SelectedItem:{id:\"minecraft:stone\"},Inventory:[]}","{SelectedItem:{},ForgeData:{missing:1}}"};
        int cases=0;
        for(int state=0;state<8;state++) {
            player.getInventory().clearContent();
            ItemStack held=state==0?ItemStack.EMPTY:new ItemStack(state%2==0?Items.STONE:Items.DIAMOND_SWORD,1+state%2);
            if(!held.isEmpty()) held.getOrCreateTag().putInt("CustomModelData",state<4?666666:7);
            player.getInventory().selected=state%2;
            player.getInventory().setItem(player.getInventory().selected,held);
            for(String pattern:patterns) for(boolean inverted:new boolean[]{false,true}) {
                CompoundTag expected=TagParser.parseTag(pattern);
                EntitySelector selector=new EntitySelectorParser(new StringReader("@s[nbt="+(inverted?"!":"")+pattern+"]")).parse();
                CompoundTag actual=player.saveWithoutId(new CompoundTag());
                ItemStack selected=player.getInventory().getSelected();
                if(!selected.isEmpty()) actual.put("SelectedItem",selected.save(new CompoundTag()));
                boolean reference=NbtUtils.compareNbt(expected,actual,true)!=inverted;
                check(!selector.findEntities(source).isEmpty()==reference,"live state "+state+" "+pattern+" inverted="+inverted);
                cases++;
            }
        }
        // Non-player serializers are free to define their own SelectedItem.
        ArmorStand custom=new ArmorStand(player.serverLevel(),0,-62,0) {
            @Override public void addAdditionalSaveData(CompoundTag tag) {
                super.addAdditionalSaveData(tag);
                tag.put("SelectedItem",new ItemStack(Items.STONE).save(new CompoundTag()));
            }
        };
        EntitySelector selector=new EntitySelectorParser(new StringReader("@s[nbt={SelectedItem:{id:\"minecraft:stone\"}}]")).parse();
        check(selector.findEntities(source.withEntity(custom)).equals(List.of(custom)),"custom nonplayer serializer fallback");
        ServerPlayer customPlayer=new ServerPlayer(player.server,player.serverLevel(),new GameProfile(UUID.randomUUID(),"CustomNBT")) {
            @Override public void addAdditionalSaveData(CompoundTag tag) {
                super.addAdditionalSaveData(tag);
                tag.put("SelectedItem",new ItemStack(Items.STONE).save(new CompoundTag()));
            }
        };
        check(selector.findEntities(source.withEntity(customPlayer)).equals(List.of(customPlayer)),"empty-hand custom player fallback");
        customPlayer.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));
        check(selector.findEntities(source.withEntity(customPlayer)).isEmpty(),"nonempty hand overrides custom SelectedItem exactly as vanilla");
        ItemStack mutable=new ItemStack(Items.STONE);
        player.getInventory().selected=0;player.getInventory().setItem(0,mutable);
        EntitySelector reused=new EntitySelectorParser(new StringReader("@s[nbt={SelectedItem:{tag:{CustomModelData:7}}}]")).parse();
        for(int i=0;i<80;i++) {
            mutable.getOrCreateTag().putInt("CustomModelData",i%2==0?7:8);
            check(!reused.findEntities(source).isEmpty()==(i%2==0),"same compiled selector observes same-stack mutation");
        }
        return cases+83;
    }

    private static void populateInventory(ServerPlayer player) {
        player.getInventory().clearContent();player.getInventory().selected=0;
        for(int slot=0;slot<36;slot++) {
            ItemStack stack=new ItemStack(Items.DIAMOND_SWORD);
            for(int n=0;n<48;n++) stack.getOrCreateTag().putString("fixture_"+n,"nested-inventory-payload-"+n);
            stack.getOrCreateTag().putInt("CustomModelData",slot==0?88889:slot);
            player.getInventory().setItem(slot,stack);
        }
    }

    private static final class FunctionTrace implements ServerFunctionManager.TraceCallbacks {
        final Map<String,Map<String,Long>> metrics=new TreeMap<>();
        final Map<String, Long> serializationsByCommand = new TreeMap<>();
        final Map<Integer,String> names=new HashMap<>();
        long start,before;String active, activeCommand;
        FunctionTrace(ResourceLocation root) { names.put(0,root.toString()); }
        public void onCommand(int depth,String command) {
            active=names.getOrDefault(depth,"unknown");start=System.nanoTime();before=SelectorProbe.value();
            activeCommand = command;
        }
        private void finish(boolean error) {
            if(active==null) return;
            Map<String,Long> stats=metrics.computeIfAbsent(active,k->new TreeMap<>());
            stats.merge("commands",1L,Long::sum);stats.merge("nanos",System.nanoTime()-start,Long::sum);
            long serializations = SelectorProbe.value() - before;
            stats.merge("entity_serializations", serializations, Long::sum);
            if (serializations > 0) serializationsByCommand.merge(active + " " + activeCommand, serializations, Long::sum);
            if(error) stats.merge("command_errors",1L,Long::sum);
            active=null;
        }
        public void onReturn(int depth,String command,int result) {
            if(active!=null) {
                Map<String,Long> stats=metrics.computeIfAbsent(active,k->new TreeMap<>());
                stats.put("result_hash",31*stats.getOrDefault("result_hash",0L)+31L*command.hashCode()+result);
                stats.put("outcome_hash", 31 * stats.getOrDefault("outcome_hash", 0L) + result);
            }
            finish(false);
        }
        public void onError(int depth,String error) { finish(true); }
        public void onCall(int depth,ResourceLocation id,int size) { names.put(depth+1,id.toString()); }
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
