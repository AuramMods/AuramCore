package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the actual installed Zeta bus/map; no replacement implementation in the fixture. */
public final class ZetaMapSmoke {
    public static void run() throws Exception {
        Class<?> type = Class.forName("org.violetmoon.zetaimplforge.event.ForgeZetaEventBus");
        Object owner = Class.forName("org.violetmoon.quark.base.Quark").getField("ZETA").get(null);
        Field remapper = type.getDeclaredField("DEFAULT_PLAY_EVENTS_REMAPPER"); remapper.setAccessible(true);
        Constructor<?> constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var forgeBus = BusBuilder.builder().build();
        Object bus = constructor.newInstance(Class.forName("org.violetmoon.zeta.event.bus.PlayEvent"),
                Class.forName("org.violetmoon.zeta.event.bus.IZetaPlayEvent"), LogManager.getLogger("auram-zeta-smoke"),
                forgeBus, Event.class, owner, remapper.get(null));
        Field field = type.getDeclaredField("convertedHandlers"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Object, Object> map = (Map<Object, Object>) field.get(bus);
        Constructor<?> key = Class.forName(type.getName() + "$Key").getDeclaredConstructors()[0]; key.setAccessible(true);
        Method handler = Listener.class.getMethod("handle", ProbeEvent.class);
        map.put(null, null); check(map.containsKey(null), "null keys and values are retained"); map.remove(null);
        Object nullableKey = key.newInstance(handler, new Listener(), null);
        map.put(nullableKey, null); check(map.containsKey(nullableKey), "null handler values retain original contract"); map.clear();

        int workers = 8, perWorker = 4000, rounds = 6;
        ExecutorService pool = Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task, "auram-zeta-stress"); thread.setDaemon(true); return thread;
        });
        try {
            for (int round = 0; round < rounds; round++) {
                Object[][] keys = new Object[workers][perWorker], values = new Object[workers][perWorker];
                for (int w = 0; w < workers; w++) for (int i = 0; i < perWorker; i++) {
                    keys[w][i] = key.newInstance(handler, new Listener(), null); values[w][i] = new Object();
                }
                concurrent(pool, workers, w -> { for (int i = 0; i < perWorker; i++) map.put(keys[w][i], values[w][i]); });
                check(map.size() == workers * perWorker, "concurrent resizing retains all handlers");
                for (int w = 0; w < workers; w++) for (int i = 0; i < perWorker; i++)
                    check(map.get(keys[w][i]) == values[w][i], "handler object identity retained");
                concurrent(pool, workers, w -> {
                    for (int i = 0; i < perWorker; i++) check(map.remove(keys[w][i]) == values[w][i], "concurrent removal returns original handler");
                });
                check(map.isEmpty(), "all handlers removed");
            }
        } finally { pool.shutdownNow(); }

        Method subscribe = type.getDeclaredMethod("subscribeMethod", Method.class, Object.class, Class.class);
        Method unsubscribe = type.getDeclaredMethod("unsubscribeMethod", Method.class, Object.class, Class.class);
        subscribe.setAccessible(true); unsubscribe.setAccessible(true);
        // Generate a typed listener for Zeta's actual mapped event without adding
        // Zeta as a mandatory compile/runtime dependency of Auram or its test mod.
        Class<?> listenerType = mappedListener();
        Object listener = listenerType.getConstructor().newInstance();
        Method mappedHandler = listenerType.getMethod("handle", Class.forName("org.violetmoon.zeta.event.play.ZServerTick$Start"));
        subscribe.invoke(bus, mappedHandler, listener, listenerType);
        forgeBus.post(new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null));
        check(listenerType.getField("calls").getInt(listener) == 1, "registered listener receives mapped event");
        unsubscribe.invoke(bus, mappedHandler, listener, listenerType);
        forgeBus.post(new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null));
        check(listenerType.getField("calls").getInt(listener) == 1 && map.isEmpty(), "unregistered listener no longer receives event");
        Map<String, Object> result = Map.of("rounds", rounds, "workers", workers, "handlers_per_round", workers * perWorker,
                "total_puts", rounds * workers * perWorker, "total_removes", rounds * workers * perWorker,
                "null_contract", true, "actual_subscribe_unsubscribe", true, "map_class", map.getClass().getName());
        Files.writeString(Path.of("zeta-map-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        System.out.println("QA_EVT {\"event\":\"zeta-handler-map\",\"status\":\"pass\",\"details\":\"Concurrent resizing, removal, null semantics and real event delivery verified\"}");
    }

    private static Class<?> mappedListener() throws IllegalAccessException {
        String name = "art/arcane/auram/smoke/GeneratedZetaListener";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "calls", "I", null, null).visitEnd();
        var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "handle", "(Lorg/violetmoon/zeta/event/play/ZServerTick$Start;)V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0); method.visitInsn(Opcodes.DUP);
        method.visitFieldInsn(Opcodes.GETFIELD, name, "calls", "I"); method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD); method.visitFieldInsn(Opcodes.PUTFIELD, name, "calls", "I");
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return MethodHandles.lookup().defineClass(writer.toByteArray());
    }

    private static void concurrent(ExecutorService pool, int workers, Work action) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers), start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int worker = i;
            futures.add(pool.submit(() -> { ready.countDown(); start.await(); action.run(worker); return null; }));
        }
        check(ready.await(10, TimeUnit.SECONDS), "stress workers started"); start.countDown();
        for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
    }

    @FunctionalInterface private interface Work { void run(int worker) throws Exception; }
    public static final class ProbeEvent extends Event { }
    public static final class Listener {
        public final AtomicInteger calls = new AtomicInteger();
        public void handle(ProbeEvent event) { calls.incrementAndGet(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
