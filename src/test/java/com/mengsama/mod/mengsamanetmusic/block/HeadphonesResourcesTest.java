package com.mengsama.mod.mengsamanetmusic.block;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.JsonUtil;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
class HeadphonesResourcesTest {
    @Test void shippedHeadphonesAndAllButtonAnimationsParse() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("mengsama.releaseJar")).toFile())) {
            String root = "assets/mengsamanetmusic/";
            var geo = JsonParser.parseString(new String(jar.getInputStream(jar.getJarEntry(root + "geo/pink_headphones.geo.json")).readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter.GEO_GSON.fromJson(geo, Model.class));
            assertTrue(geo.toString().contains("armorHead"));
            var anim = JsonParser.parseString(new String(jar.getInputStream(jar.getJarEntry(root + "animations/pink_walkman.animation.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            var baked = software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter.GEO_GSON.fromJson(anim, BakedAnimations.class);
            for (WalkmanControl control : WalkmanControl.values()) {
                var a = baked.getAnimation("animation.pink_walkman." + control.animation);
                assertNotNull(a);
                assertTrue(a.length() > 0);
                assertEquals(1, a.boneAnimations().length);
            }
            assertNotNull(jar.getJarEntry("data/curios/tags/item/head.json"));
            assertNotNull(jar.getJarEntry("data/mengsamanetmusic/curios/entities/headphones_player.json"));
            assertNull(jar.getJarEntry("top/theillusivec4/curios/Curios.class"), "Curios must remain an optional separate mod");
        }
    }
    @Test void commonHeadphonesTypesLoadWithNoCuriosOnTheTestRuntimeClasspath() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("top.theillusivec4.curios.api.CuriosApi"));
        assertNotNull(Class.forName("com.mengsama.mod.mengsamanetmusic.compat.HeadphonesAccess"));
        assertNotNull(Class.forName("com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem", false, getClass().getClassLoader()));
    }
}
