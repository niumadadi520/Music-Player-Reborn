package com.mengsama.mod.mengsamanetmusic.testsupport;
import java.util.*;
public final class HeadlessEnvironment implements org.junit.jupiter.api.extension.BeforeAllCallback {
    @Override public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext context) { initialize(); }
    private static boolean initialized;
    public static synchronized void initialize() {
        if (initialized) return;
        try {
            var dist = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
            dist.setAccessible(true);
            dist.set(null, net.neoforged.api.distmarker.Dist.DEDICATED_SERVER);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), new ArrayList<>(), Map.of());
        initialized = true;
    }
}
