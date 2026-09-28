package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.InvocationTargetException;

 
@Mod.EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID)
public final class XaeroStartupCompat {
    private XaeroStartupCompat() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (ModList.get().isLoaded("xaeroworldmap")) ensure(event.getServer(), "xaero.map", false);
        if (ModList.get().isLoaded("xaerominimap")) ensure(event.getServer(), "xaero.common", true);
    }

    private static void ensure(MinecraftServer server, String prefix, boolean minimap) {
        try {
            Class<?> dataType = Class.forName(prefix + ".server.MinecraftServerData");
            var getter = dataType.getMethod("get", MinecraftServer.class);
            boolean initialized = initializeIfMissing(() -> getter.invoke(null, server), () -> {
                 
                 
                Object events;
                if (minimap) {
                    Object instance = Class.forName("xaero.minimap.XaeroMinimap").getField("instance").get(null);
                    if (instance == null) throw new IllegalStateException("Xaero minimap instance is unavailable");
                    events = Class.forName("xaero.common.IXaeroMinimap").getMethod("getCommonEvents").invoke(instance);
                } else {
                    events = Class.forName("xaero.map.WorldMap").getField("commonEvents").get(null);
                }
                if (events == null) throw new IllegalStateException("Xaero common events are unavailable");
                Class.forName(prefix + ".events.CommonEvents").getMethod("onServerStarting", MinecraftServer.class).invoke(events, server);
            });
            if (initialized) MengSamaNetMusic.LOGGER.warn("Initialized missing {} server data before player login", prefix);
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
            MengSamaNetMusic.LOGGER.warn("Could not initialize optional {} server data; leaving Xaero's normal error handling active", prefix, cause);
        }
    }

    @FunctionalInterface interface DataReader { Object get() throws ReflectiveOperationException; }
    @FunctionalInterface interface Initializer { void run() throws ReflectiveOperationException; }
    static boolean initializeIfMissing(DataReader reader, Initializer initializer) throws ReflectiveOperationException {
        if (reader.get() != null) return false;
        initializer.run();
        if (reader.get() == null) throw new IllegalStateException("Xaero startup hook left server data unset");
        return true;
    }
}
