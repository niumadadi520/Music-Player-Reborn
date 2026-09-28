package com.mengsama.mod.mengsamanetmusic.compat;

import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

 
class OptionalModsAbsentTest {
    private static final String ROOT="com.mengsama.mod.mengsamanetmusic.";
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);field.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
    @Test void absentBackpacksCanRegisterAndCreateAnOrdinaryWalkman() throws Exception {
        var loader=new NoOptionalModsLoader();
        var access=Class.forName(ROOT+"compat.BackpackAccess",true,loader);
        access.getDeclaredMethods();
        access.getMethod("register").invoke(null);
        assertEquals(false,access.getMethod("available").invoke(null));
        var item=access.getMethod("createItem",Block.class,Item.Properties.class)
                .invoke(null,Blocks.STONE,new Item.Properties().stacksTo(1));
        assertEquals(ROOT+"item.MusicPlayerItem",item.getClass().getName());
        assertTrue(loader.optionalAttempts.isEmpty(),loader.optionalAttempts.toString());
    }
    @Test void absentIntegrationEntryPointsCanBeLinkedWithoutTheirApis() throws Exception {
        var loader=new NoOptionalModsLoader();
        for(String name:List.of("MengSamaNetMusic","init.ModItems","compat.BackpackAccess",
                "compat.BackpackClientEvents","compat.MaidMusicAccess","compat.TouhouLittleMaidCompat",
                "compat.HeadphonesAccess","client.ClientModEvents","gui.MusicPlayerMenu")) {
            var type=Class.forName(ROOT+name,false,loader);
            type.getDeclaredMethods();type.getDeclaredConstructors();
        }
        assertTrue(loader.optionalAttempts.isEmpty(),loader.optionalAttempts.toString());
    }
    @Test void installedApiBridgeStillCreatesABackpackUpgrade() throws Exception {
        var bridge=Class.forName(ROOT+"compat.BackpackAccess$Loaded");
        var factory=bridge.getDeclaredMethod("createItem",Block.class,Item.Properties.class);
        factory.setAccessible(true);
        var item=factory.invoke(null,Blocks.STONE,new Item.Properties().stacksTo(1));
        assertEquals(ROOT+"compat.backpack.WalkmanUpgradeItem",item.getClass().getName());
        assertTrue(Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem").isInstance(item));
    }
    private static final class NoOptionalModsLoader extends ClassLoader {
        final Set<String> optionalAttempts=new TreeSet<>();
        NoOptionalModsLoader(){super(OptionalModsAbsentTest.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(name.startsWith("net.p3pp3rf1y.") || name.startsWith("com.github.tartaricacid.touhoulittlemaid.")
                    || name.startsWith("top.theillusivec4.curios.") || name.startsWith("snownee.jade.")
                    || name.startsWith("me.shedaniel.clothconfig2.") || name.startsWith("de.maxhenkel.voicechat.")) {
                optionalAttempts.add(name);throw new ClassNotFoundException("Optional mod intentionally absent: "+name);
            }
            if(!name.startsWith(ROOT))return super.loadClass(name,resolve);
            var type=findLoadedClass(name);
            if(type==null)try(var in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                if(in==null)throw new ClassNotFoundException(name);
                byte[] bytes=in.readAllBytes();type=defineClass(name,bytes,0,bytes.length);
            }catch(java.io.IOException error){throw new ClassNotFoundException(name,error);}
            if(resolve)resolveClass(type);return type;
        }
    }
}
