package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock;
import org.junit.jupiter.api.Test;
import snownee.jade.api.IWailaClientRegistration;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JadeDeviceRegistrationTest {
    @Test void includesKaraokeDevicesAndCleansStorageAfterEveryProviderHasRun() {
        List<Class<?>> blocks=new ArrayList<>(); List<Object> finalizers=new ArrayList<>();
        IWailaClientRegistration registration=(IWailaClientRegistration)Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class[]{IWailaClientRegistration.class},(proxy,method,args)->{
                    if(method.getName().equals("registerBlockComponent"))blocks.add((Class<?>)args[1]);
                    if(method.getName().equals("addTooltipCollectedCallback"))finalizers.add(args[args.length-1]);
                    return null;
                });
        new JadeMusicPlayerPlugin().registerClient(registration);
        assertTrue(blocks.contains(KaraokeDeviceBlock.class),"Speaker uses a different block class from the gramophone");
        assertEquals(3,blocks.size());assertEquals(1,finalizers.size());
    }
}
