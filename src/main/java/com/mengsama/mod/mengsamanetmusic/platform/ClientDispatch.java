package com.mengsama.mod.mengsamanetmusic.platform;

import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientDispatch {
    private ClientDispatch() {}
    public static void runWhenOn(Dist side, Supplier<Runnable> action) {
        if (FMLEnvironment.dist == side) action.get().run();
    }
}
