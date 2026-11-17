package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

/** A disposable live-server load scenario. Never packaged in the core jar. */
public final class TuningSmoke {
    private static Run active;
    public static void start(MinecraftServer server) {
        try {active=new Run(server);} catch(Throwable failure) {fail(server,failure);}
    }
    public static void tickStart() {
        Run run=active;if(run==null)return;
        try {run.beginTick();} catch(Throwable failure) {active=null;fail(run.server,failure);}
    }
    public static void tickEnd() {
        Run run=active;if(run==null)return;
        try {run.endTick();} catch(Throwable failure) {active=null;fail(run.server,failure);}
    }
    private static void fail(MinecraftServer server,Throwable failure) {
        LogUtils.getLogger().error("[AURAM TUNING] FAILED",failure);
        try {Files.writeString(Path.of("auram-tuning-failed.txt"),failure.toString());} catch(Exception ignored) { }
        server.halt(false);
    }

    private static final class Run {
        final MinecraftServer server;final ServerLevel level;
        final int batchSize=Integer.getInteger("auram.tuning.batchSize",4);
        final int warmupBatches=Integer.getInteger("auram.tuning.warmupBatches",5);
        final int measuredBatches=Integer.getInteger("auram.tuning.measuredBatches",20);
        final int entities=Integer.getInteger("auram.tuning.entities",128);
        final List<Job> pending=new ArrayList<>();
        final Map<Long,ChunkAccess> generated=new TreeMap<>();
        final List<Double> ticks=new ArrayList<>(),intervals=new ArrayList<>(),batches=new ArrayList<>();
        final List<ArmorStand> fixture=new ArrayList<>();
        final com.sun.management.ThreadMXBean threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        final com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> spawn;
        CompoundTag expected;
        int phase,queued,completed,tickNumber,peakPool,peakActive;
        long tickStart,lastTickStart,start,lastBatch,cpuStart,allocatedStart,mainCpuStart,peakHeap,nbtSaves;
        Recording recording;

        Run(MinecraftServer server) {
            this.server=server;level=server.overworld();
            threads.setThreadAllocatedMemoryEnabled(true);threads.setThreadCpuTimeEnabled(true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
            level.setDayTime(6000);
            ChunkPos origin=new ChunkPos(0,0);
            level.getChunkSource().addRegionTicket(TicketType.FORCED,origin,2,origin);
            spawn=level.getChunkSource().getChunkFuture(0,0,ChunkStatus.FULL,true);
            LogUtils.getLogger().info("[AURAM TUNING] Initializing live load; processors={} heap={}",Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory());
        }
        void beginTick() throws Exception {
            tickStart=System.nanoTime();tickNumber++;
            if(phase==0) {
                if(!spawn.isDone())return;
                spawn.join().left().orElseThrow();
                level.setChunkForced(0,0,true);
                initializeEntities();phase=1;
            }
            if(expected!=null) {
                SelectorProbe.start();
                int found=0;
                // Keep a fixed serialization load independent of player-driven
                // entity-section visibility in this playerless server fixture.
                try {for(ArmorStand stand:fixture)
                    if(NbtUtils.compareNbt(expected,stand.saveWithoutId(new CompoundTag()),true))found++;}
                finally {long saves=SelectorProbe.stop();if(phase==2)nbtSaves+=saves;}
                if(found!=entities)throw new AssertionError("NBT load changed: "+found+"/"+entities);
            }
            if(phase==2) {
                if(lastTickStart!=0)intervals.add((tickStart-lastTickStart)/1e6);
                lastTickStart=tickStart;
            }
            int total=(phase==1?warmupBatches:measuredBatches)*batchSize;
            while(pending.size()<batchSize && queued<total) {
                int x=(phase==1?20:80)+queued%10,z=(phase==1?20:80)+queued/10;
                pending.add(new Job(new ChunkPos(x,z),level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true)));
                queued++;
            }
        }
        void initializeEntities() throws Exception {
            for(int i=0;i<entities;i++) {
                int x=i%16,z=i/16;
                ArmorStand stand=new ArmorStand(level,x+0.5,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+3,z+0.5);
                stand.setNoGravity(true);stand.setInvulnerable(true);stand.addTag("auram_tuning");
                ItemStack item=new ItemStack(Items.DIAMOND_SWORD);
                item.getOrCreateTag().putString("fixture_payload","x".repeat(256));
                item.getOrCreateTag().putInt("CustomModelData",i);
                stand.setItemSlot(EquipmentSlot.MAINHAND,item);
                fixture.add(stand);
            }
            expected=TagParser.parseTag("{HandItems:[{id:\"minecraft:diamond_sword\"}]}");
        }
        void endTick() throws Exception {
            if(phase==0)return;
            if(phase==2) {
                ticks.add((System.nanoTime()-tickStart)/1e6);
                peakHeap=Math.max(peakHeap,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                Object executor=Util.backgroundExecutor();
                if(executor instanceof ForkJoinPool pool) {peakPool=Math.max(peakPool,pool.getPoolSize());peakActive=Math.max(peakActive,pool.getActiveThreadCount());}
            }
            for(Iterator<Job> iterator=pending.iterator();iterator.hasNext();) {
                Job job=iterator.next();if(!job.future.isDone())continue;
                ChunkAccess chunk=job.future.join().left().orElseThrow();
                iterator.remove();completed++;
                if(phase==2) {
                    generated.put(job.position.toLong(),chunk);
                    if(completed%batchSize==0) {
                        long now=System.nanoTime();batches.add((now-lastBatch)/1e9);lastBatch=now;
                        LogUtils.getLogger().info("[AURAM TUNING] Measured {}/{} FULL chunks",completed,measuredBatches*batchSize);
                    }
                }
            }
            if(phase==1 && completed==warmupBatches*batchSize) {
                queued=0;completed=0;phase=2;
                recording=new Recording();
                recording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO);
                recording.enable("jdk.ObjectAllocationSample").with("throttle","100/s").withStackTrace();
                recording.setMaxSize(64L*1024*1024);recording.start();
                cpuStart=os.getProcessCpuTime();allocatedStart=allocatedBytes();
                mainCpuStart=threads.getCurrentThreadCpuTime();start=lastBatch=System.nanoTime();
                Files.writeString(Path.of("auram-tuning-started.txt"),"Measured workload started\n");
                LogUtils.getLogger().info("[AURAM TUNING] Warmup complete; starting {} batches",measuredBatches);
            } else if(phase==2 && completed==measuredBatches*batchSize) finish();
            if(tickNumber>36000)throw new AssertionError("Live benchmark exceeded 30 minutes");
        }
        long allocatedBytes() {
            long sum=0;for(long value:threads.getThreadAllocatedBytes(threads.getAllThreadIds()))if(value>0)sum+=value;return sum;
        }
        void finish() throws Exception {
            long finish=System.nanoTime();double seconds=(finish-start)/1e9;
            long allocated=allocatedBytes()-allocatedStart,cpu=os.getProcessCpuTime()-cpuStart,mainCpu=threads.getCurrentThreadCpuTime()-mainCpuStart;
            recording.stop();recording.dump(Path.of("tuning.jfr"));recording.close();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("measured_full_chunks",completed);result.put("elapsed_seconds",seconds);result.put("full_chunks_per_second",completed/seconds);
            result.put("warmup_batches",warmupBatches);result.put("measured_batches",measuredBatches);result.put("chunks_per_batch",batchSize);
            result.put("tick_ms",statistics(ticks));result.put("tick_interval_ms",statistics(intervals));result.put("chunk_batch_seconds",statistics(batches));
            result.put("process_cpu_seconds",cpu/1e9);result.put("server_thread_cpu_seconds",mainCpu/1e9);
            result.put("live_java_thread_allocated_bytes_delta",allocated);result.put("peak_sampled_heap_bytes",peakHeap);
            result.put("heap_end",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().toString());
            result.put("nbt_entity_serializations",nbtSaves);result.put("nbt_entities",entities);
            result.put("available_processors",Runtime.getRuntime().availableProcessors());result.put("max_heap_bytes",Runtime.getRuntime().maxMemory());
            result.put("java_version",System.getProperty("java.runtime.version"));result.put("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));
            result.put("jvm_arguments",ManagementFactory.getRuntimeMXBean().getInputArguments());
            Object executor=Util.backgroundExecutor();result.put("background_executor",executor.getClass().getName());
            if(executor instanceof ForkJoinPool pool)result.put("background_parallelism",pool.getParallelism());
            if(executor instanceof ThreadPoolExecutor pool)result.put("background_parallelism",pool.getMaximumPoolSize());
            result.put("background_peak_pool_size",peakPool);result.put("background_peak_active",peakActive);
            result.put("core_version",ModList.get().getModContainerById("auram").orElseThrow().getModInfo().getVersion().toString());
            result.put("loaded_mod_count",ModList.get().getMods().size());
            result.put("jfr",summarizeRecording());
            // Fingerprinting is outside the timed/allocation measurement window.
            MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer integer=ByteBuffer.allocate(4);
            for(var entry:generated.entrySet()) {
                digest.update(Long.toString(entry.getKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(var section:entry.getValue().getSections())for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    integer.clear();integer.putInt(Block.getId(section.getBlockState(x,y,z)));digest.update(integer.array());
                }
            }
            result.put("generated_block_fingerprint",HexFormat.of().formatHex(digest.digest()));
            result.put("scope","Fresh playerless disposable server, fixed seed 104729, contiguous synchronous FULL chunk requests, forced origin and fixed capability-heavy entity NBT serialization load; startup/fingerprint/shutdown excluded from workload metrics. Entity objects are serialized directly rather than selected through player-dependent world tracking.");
            Files.writeString(Path.of("tuning-benchmark.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
            Files.writeString(Path.of("auram-tuning-pass.txt"),"PASS live FULL chunk workload, tick/allocation/GC metrics and block fingerprint\n");
            LogUtils.getLogger().info("[AURAM TUNING] PASS {} chunks/s, {} ticks",completed/seconds,ticks.size());
            active=null;server.halt(false);
        }
        Map<String,Object> summarizeRecording() throws Exception {
            List<Double> pauses=new ArrayList<>();Map<String,Long> allocations=new HashMap<>(),owners=new HashMap<>();
            Set<Long> allocationThreads=new HashSet<>();
            try(RecordingFile file=new RecordingFile(Path.of("tuning.jfr"))) {
                while(file.hasMoreEvents()) {
                    var event=file.readEvent();String type=event.getEventType().getName();
                    if(type.equals("jdk.GCPhasePause"))pauses.add(event.getDuration().toNanos()/1e6);
                    if(type.equals("jdk.ObjectAllocationSample")) {
                        // The first sample's weight may include allocations made
                        // before recording started. Exclude it for each thread.
                        var thread=event.getThread();
                        if(thread==null || allocationThreads.add(thread.getJavaThreadId()))continue;
                        long weight=event.getLong("weight");allocations.merge(event.getClass("objectClass").getName(),weight,Long::sum);
                        var trace=event.getStackTrace();
                        if(trace!=null)for(var frame:trace.getFrames()) {
                            var method=frame.getMethod();String name=method.getType().getName();
                            if(!name.startsWith("java.") && !name.startsWith("jdk.")) {owners.merge(name+"."+method.getName(),weight,Long::sum);break;}
                        }
                    }
                }
            }
            return Map.of("gc_pause_ms",statistics(pauses),"gc_total_pause_ms",pauses.stream().mapToDouble(Double::doubleValue).sum(),
                    "sampled_allocation_classes",largest(allocations),"sampled_allocation_owners",largest(owners),
                    "allocation_sample_note","First event per thread excluded; sampled weights are estimates, not exact allocated bytes.");
        }
    }
    private static List<Map<String,Object>> largest(Map<String,Long> values) {
        return values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(15)
                .map(entry->Map.<String,Object>of("name",entry.getKey(),"sampled_weight_bytes",entry.getValue())).toList();
    }
    private static Map<String,Object> statistics(List<Double> values) {
        if(values.isEmpty())return Map.of("count",0,"median",0,"p95",0,"p99",0,"max",0);
        double[] sorted=values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return Map.of("count",sorted.length,"median",sorted[(sorted.length-1)/2],"p95",sorted[(int)Math.ceil(sorted.length*.95)-1],
                "p99",sorted[(int)Math.ceil(sorted.length*.99)-1],"max",sorted[sorted.length-1]);
    }
    private record Job(ChunkPos position,CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> future) { }
}
