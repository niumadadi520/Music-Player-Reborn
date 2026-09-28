package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class LodGeometryTest {
    @Test void allDetailLevelsBakeAndPreserveAnimationBones() throws Exception {
        try(var jar=new JarFile(System.getProperty("mengsama.releaseJar"))) {
            for (String model:List.of("pink_walkman","rose_gramophone")) {
                Map<String,JsonObject> original=new HashMap<>();
                int fullCount=0;
                for (String suffix:List.of("","_medium","_far")) {
                    var entry=jar.getJarEntry("assets/mengsamanetmusic/geo/"+model+suffix+".geo.json");
                    assertNotNull(entry);
                    String text;
                    try(var input=jar.getInputStream(entry)){text=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
                    JsonObject json=JsonParser.parseString(text).getAsJsonObject();
                    var bones=json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
                    int count=0;
                    for(var el:bones) {
                        var bone=el.getAsJsonObject();String name=bone.get("name").getAsString();
                        if(suffix.isEmpty())original.put(name,bone);
                        else {
                            assertNotNull(original.get(name));
                            for(String key:List.of("pivot","parent","rotation"))
                                assertEquals(original.get(name).get(key),bone.get(key),"Animation bind pose changed for "+name);
                        }
                        if(bone.has("cubes"))count+=bone.getAsJsonArray("cubes").size();
                    }
                    var baked=BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(
                            GeometryTree.fromModel(JsonUtil.GEO_GSON.fromJson(json,Model.class)));
                    assertFalse(baked.topLevelBones().isEmpty());
                    for(String bone:original.keySet())assertTrue(baked.getBone(bone).isPresent(),bone);
                    if(suffix.isEmpty())fullCount=count;
                    else assertTrue(count<fullCount*(suffix.equals("_far")?.3:.55),"Geometry reduction budget not met");
                }
            }
        }
    }
}
