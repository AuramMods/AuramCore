package art.arcane.auram.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

/** Bind the installed FTB Essentials API while keeping FTB optional. */
final class FtbRtpBindings {
    private static final String PACKAGE = "dev.ftb.mods.ftbessentials";
    static final FtbRtpBindings INSTANCE = load();

    private final Object minDistance;
    private final Object maxDistance;
    private final Object maxTries;
    private final Method getInt;
    private final Object rtpEvent;
    private final Method eventInvoker;
    private final Method rtpCallback;
    private final Method isFalse;
    private final Constructor<?> destination;
    private final Constructor<?> currentPosition;
    private final Method teleportNow;
    private final Method runCommand;
    private final Method failed;
    final Object success;

    private FtbRtpBindings() throws ReflectiveOperationException {
        var config = Class.forName(PACKAGE + ".config.FTBEConfig");
        minDistance = config.getField("RTP_MIN_DISTANCE").get(null);
        maxDistance = config.getField("RTP_MAX_DISTANCE").get(null);
        maxTries = config.getField("RTP_MAX_TRIES").get(null);
        getInt = minDistance.getClass().getMethod("get");
        rtpEvent = Class.forName(PACKAGE + ".FTBEssentialsEvents").getField("RTP_EVENT").get(null);
        eventInvoker = Class.forName("dev.architectury.event.Event").getMethod("invoker");
        rtpCallback = Class.forName(PACKAGE + ".FTBEssentialsEvents$RTP")
                .getMethod("teleport", ServerLevel.class, ServerPlayer.class, BlockPos.class, int.class);
        isFalse = Class.forName("dev.architectury.event.EventResult").getMethod("isFalse");
        var position = Class.forName(PACKAGE + ".util.TeleportPos");
        destination = position.getConstructor(ResourceKey.class, BlockPos.class);
        currentPosition = position.getConstructor(Entity.class);
        teleportNow = Class.forName(PACKAGE + ".util.WarmupCooldownTeleporter")
                .getDeclaredMethod("teleportNow", ServerPlayer.class, Function.class);
        if (!teleportNow.trySetAccessible()) throw new IllegalAccessException("FTB teleport continuation");
        var result = Class.forName(PACKAGE + ".util.TeleportPos$TeleportResult");
        success = result.getField("SUCCESS").get(null);
        failed = result.getMethod("failed", Component.class);
        runCommand = result.getMethod("runCommand", ServerPlayer.class);
    }

    int minDistance() { return (Integer) call(getInt, minDistance); }
    int maxDistance() { return (Integer) call(getInt, maxDistance); }
    int maxTries() { return (Integer) call(getInt, maxTries); }

    boolean permits(ServerLevel level, ServerPlayer player, BlockPos pos, int attempt) {
        Object result = call(rtpCallback, call(eventInvoker, rtpEvent), level, player, pos, attempt);
        return !(Boolean) call(isFalse, result);
    }

    Object failed(Component message) {
        return call(failed, null, message);
    }

    void finish(Object teleporter, ServerPlayer player, BlockPos pos) {
        try {
            Object destinationPos = pos == null ? currentPosition.newInstance(player)
                    : destination.newInstance(player.serverLevel().dimension(), pos);
            Object result = call(teleportNow, teleporter, player, new FtbRtpSearch.ReadyPosition(destinationPos));
            call(runCommand, result, player);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not complete FTB RTP", error);
        }
    }

    private static FtbRtpBindings load() {
        try {
            return new FtbRtpBindings();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object call(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
