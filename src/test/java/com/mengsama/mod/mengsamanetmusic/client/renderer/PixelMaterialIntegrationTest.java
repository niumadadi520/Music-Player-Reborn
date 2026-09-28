package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class PixelMaterialIntegrationTest {
    private static final String PREFIX="assets/mengsamanetmusic/";
    private static JsonObject fixture() throws Exception {
        try(var in=PixelMaterialIntegrationTest.class.getResourceAsStream("/pixel-model-baseline.json")) {
            assertNotNull(in);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static byte[] bytes(JarFile jar,String path) throws Exception {
        var entry=jar.getJarEntry(PREFIX+path);assertNotNull(entry,path);
        try(var in=jar.getInputStream(entry)){return in.readAllBytes();}
    }
    private static JsonObject geometry(JarFile jar,String name) throws Exception {
        return JsonParser.parseString(new String(bytes(jar,"geo/"+name),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void allRetexturedModelsBakeAndEveryUvStaysInsideItsActualTexture() throws Exception {
        try(var jar=new JarFile(System.getProperty("mengsama.releaseJar"))) {
            for(var entry:fixture().getAsJsonObject("models").entrySet()) {
                var name=entry.getKey();var expected=entry.getValue().getAsJsonObject();var raw=geometry(jar,name);
                var model=BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(JsonUtil.GEO_GSON.fromJson(raw,Model.class)));
                assertFalse(model.topLevelBones().isEmpty(),name);
                var geo=raw.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();var desc=geo.getAsJsonObject("description");
                var image=ImageIO.read(new ByteArrayInputStream(bytes(jar,"textures/"+expected.get("texture").getAsString())));
                assertEquals(image.getWidth(),desc.get("texture_width").getAsInt(),name);
                assertEquals(image.getHeight(),desc.get("texture_height").getAsInt(),name);
                for(var b:geo.getAsJsonArray("bones")) {
                    var cubes=b.getAsJsonObject().getAsJsonArray("cubes");if(cubes==null)continue;
                    for(var c:cubes)for(var uv:c.getAsJsonObject().getAsJsonObject("uv").entrySet()) {
                        var face=uv.getValue().getAsJsonObject();var start=face.getAsJsonArray("uv");var size=face.getAsJsonArray("uv_size");
                        for(int axis=0;axis<2;axis++) {
                            double a=start.get(axis).getAsDouble(),z=a+size.get(axis).getAsDouble();
                            assertTrue(Double.isFinite(a)&&Double.isFinite(z),name);
                            assertTrue(Math.min(a,z)>=-1e-5&&Math.max(a,z)<=(axis==0?image.getWidth():image.getHeight())+1e-5,name+" "+uv.getKey());
                        }
                    }
                }
            }
        }
    }
    @Test void animationBindingsAndNonGramophoneGeometryRemainUnchanged() throws Exception {
        try(var jar=new JarFile(System.getProperty("mengsama.releaseJar"))) {
            for(var entry:fixture().getAsJsonObject("models").entrySet()) {
                var bones=geometry(jar,entry.getKey()).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
                var actual=new JsonArray();
                for(var b:bones) {
                    var bone=b.getAsJsonObject().deepCopy();
                    if(entry.getKey().startsWith("rose_gramophone"))bone.remove("cubes");
                    else {var cubes=bone.getAsJsonArray("cubes");if(cubes!=null)for(var cube:cubes)cube.getAsJsonObject().remove("uv");}
                    actual.add(bone);
                }
                assertEquals(entry.getValue().getAsJsonObject().get("geometryWithoutUv"),actual,entry.getKey());
            }
        }
    }
    @Test void gramophoneWalkmanAndAllAnimationFilesKeepTheirPreviousHashes() throws Exception {
        try(var jar=new JarFile(System.getProperty("mengsama.releaseJar"))) {
            for(var entry:fixture().getAsJsonObject("protected").entrySet()) {
                var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(jar,entry.getKey())));
                assertEquals(entry.getValue().getAsString(),hash,entry.getKey());
            }
        }
    }
    @Test void combinedEarbudAtlasKeepsWalkmanPixelsAndUsesTheNewEarbudPixels() throws Exception {
        try(var jar=new JarFile(System.getProperty("mengsama.releaseJar"))) {
            var combined=ImageIO.read(new ByteArrayInputStream(bytes(jar,"textures/block/pink_wired_display.png")));
            var earbuds=ImageIO.read(new ByteArrayInputStream(bytes(jar,"textures/armor/pink_earbuds.png")));
            var digest=MessageDigest.getInstance("SHA-256");
            for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
                int rgba=combined.getRGB(x,y);digest.update((byte)(rgba>>16));digest.update((byte)(rgba>>8));digest.update((byte)rgba);digest.update((byte)(rgba>>24));
            }
            assertEquals(fixture().get("combinedWalkmanPixels").getAsString(),HexFormat.of().formatHex(digest.digest()));
            for(int y=0;y<128;y++)for(int x=0;x<128;x++)assertEquals(earbuds.getRGB(x/2,y/2),combined.getRGB(x+256,y));
        }
    }
}
