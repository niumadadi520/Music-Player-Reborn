package com.mengsama.mod.mengsamanetmusic.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.JsonUtil;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PlacedMusicModelTest {
    private byte[] resource(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry("assets/mengsamanetmusic/" + path);
        assertNotNull(entry, "Missing packaged resource: " + path);
        try (var input = jar.getInputStream(entry)) { return input.readAllBytes(); }
    }
    private JsonObject json(JarFile jar, String path) throws Exception {
        return JsonParser.parseString(new String(resource(jar, path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test void shippedModelsAndAnimationsLoadWithActualGeckoLib4Parser() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("mengsama.releaseJar")).toFile())) {
            for (String name : List.of("pink_walkman", "rose_gramophone")) {
                JsonObject geo = json(jar, "geo/" + name + ".geo.json");
                Model model = software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter.GEO_GSON.fromJson(geo, Model.class);
                assertEquals(1, model.minecraftGeometry().length);
                JsonObject animations = json(jar, "animations/" + name + ".animation.json").getAsJsonObject("animations");
                BakedAnimations baked = software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter.GEO_GSON.fromJson(animations, BakedAnimations.class);
                assertEquals(animations.size(), baked.animations().size());
                var play = baked.getAnimation("animation." + name + ".play");
                assertNotNull(play);
                assertTrue(play.length() > 0);
                assertTrue(play.boneAnimations().length > 0, "Parser must preserve the moving bones");
                var bones = new HashSet<String>();
                geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")
                        .forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));
                animations.entrySet().forEach(a -> assertTrue(bones.containsAll(a.getValue().getAsJsonObject()
                        .getAsJsonObject("bones").keySet()), "Animation references missing bones"));
                var texture = ImageIO.read(new ByteArrayInputStream(resource(jar, "textures/block/" + name + ".png")));
                assertNotNull(texture);
                var description = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
                assertEquals(description.get("texture_width").getAsInt(), texture.getWidth());
                assertEquals(description.get("texture_height").getAsInt(), texture.getHeight());
            }
        }
    }

    @Test void pauseStopAndResumeSelectStaticOrMovingAnimations() {
        for (var selector : List.<java.util.function.BiFunction<Boolean,Boolean,RawAnimation>>of(
                PlacedMusicAnimations::walkman, PlacedMusicAnimations::gramophone)) {
            RawAnimation stopped = selector.apply(false, false);
            RawAnimation playing = selector.apply(true, false);
            assertNotEquals(stopped, playing);
            assertSame(stopped, selector.apply(true, true), "Paused playback must not keep rotating");
            assertSame(stopped, selector.apply(false, true));
            assertSame(playing, selector.apply(true, false), "Resume selects the moving sequence again");
        }
    }

    @Test void gramophoneStartOverridesHoldSoRecordLoopCanBeReached() {
        var stages = PlacedMusicAnimations.gramophone(true, false).getAnimationStages();
        assertEquals(2, stages.size());
        assertEquals("animation.rose_gramophone.start", stages.get(0).animationName());
        assertSame(Animation.LoopType.PLAY_ONCE, stages.get(0).loopType());
        assertEquals("animation.rose_gramophone.play", stages.get(1).animationName());
        assertSame(Animation.LoopType.LOOP, stages.get(1).loopType());
    }

    @Test void controllersOnlyReferenceAnimationsPresentInReleaseJar() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("mengsama.releaseJar")).toFile())) {
            for (boolean playing : List.of(false, true)) {
                for (RawAnimation sequence : List.of(PlacedMusicAnimations.walkman(playing, false),
                        PlacedMusicAnimations.gramophone(playing, false))) {
                    for (var stage : sequence.getAnimationStages()) {
                        String model = stage.animationName().split("\\.")[1];
                        assertTrue(json(jar, "animations/" + model + ".animation.json")
                                .getAsJsonObject("animations").has(stage.animationName()));
                    }
                }
            }
        }
    }

    @Test void bothPlacedEntitiesUseGeckoLibsAdvancingClock() throws Exception {
         
        for (String type : List.of("MusicPlayerBlockEntity", "PortableMusicPlayerBlockEntity")) {
            try (var input = getClass().getResourceAsStream(type + ".class")) {
                assertNotNull(input);
                new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces) {
                        assertTrue(List.of(interfaces).contains("software/bernie/geckolib/animatable/GeoBlockEntity"));
                    }
                    @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                        assertNotEquals("getTick", name, "Do not replace GeckoLib's default clock with a constant");
                        return null;
                    }
                }, org.objectweb.asm.ClassReader.SKIP_CODE);
            }
        }
    }
}
