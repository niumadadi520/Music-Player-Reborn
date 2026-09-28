package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EarbudPresentationTest {
    private static JsonObject resource(String name) throws Exception {
        try (var stream = EarbudPresentationTest.class.getResourceAsStream("/assets/mengsamanetmusic/" + name)) {
            assertNotNull(stream,name);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void actualEarbudGeometryFitsInsideSixteenPixelInventoryCells() throws Exception {
        for (String name : new String[]{"pink_wired_earbuds_both","pink_bluetooth_earbuds_left","pink_bluetooth_earbuds_right"}) {
            var gui = resource("models/item/"+name+".json").getAsJsonObject("display").getAsJsonObject("gui");
            var scale = gui.getAsJsonArray("scale");
            var rotation = gui.getAsJsonArray("rotation");
            Quaternionf q = new Quaternionf().rotationXYZ((float)Math.toRadians(rotation.get(0).getAsFloat()),
                    (float)Math.toRadians(rotation.get(1).getAsFloat()),(float)Math.toRadians(rotation.get(2).getAsFloat()));
            float shift = name.endsWith("left") ? 3.72F : name.endsWith("right") ? -3.72F : 0;
            var bones = resource("geo/"+name+".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for (var bone : bones) {
                var cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
                if(cubes==null) continue;
                for (var element : cubes) {
                    var cube = element.getAsJsonObject();
                    var origin = cube.getAsJsonArray("origin"); var size = cube.getAsJsonArray("size");
                    float inflate=cube.has("inflate")?cube.get("inflate").getAsFloat():0;
                    for(int corner=0;corner<8;corner++) {
                        float[] v = new float[3];
                        for(int axis=0;axis<3;axis++) v[axis]=origin.get(axis).getAsFloat()-inflate+
                                (((corner>>axis)&1)==0?0:size.get(axis).getAsFloat()+inflate*2);
                         
                        Vector3f projected = new Vector3f(-v[0]+shift,v[1]-27.9F+0.16F,v[2]);
                        projected.mul(scale.get(0).getAsFloat(),scale.get(1).getAsFloat(),scale.get(2).getAsFloat()).rotate(q);
                        assertTrue(Math.abs(projected.x)<=7 && Math.abs(projected.y)<=7,
                                name+" exceeds the 14px safe area: "+projected);
                    }
                }
            }
        }
    }
    @Test void caseOpeningWaitCoversTheSuppliedAnimationAndFacesTheCamera() throws Exception {
        var animation = resource("animations/pink_bluetooth_case.animation.json").getAsJsonObject("animations")
                .getAsJsonObject("animation.pink_bluetooth_case.open");
        int animationTicks=(int)Math.ceil(animation.get("animation_length").getAsDouble()*20);
        assertFalse(EarbudCaseOpening.finished(100,100+animationTicks-1));
        assertTrue(EarbudCaseOpening.finished(100,100+EarbudCaseOpening.OPEN_TICKS));
        var display=resource("models/item/pink_bluetooth_case.json").getAsJsonObject("display");
        for(String hand:new String[]{"firstperson_righthand","firstperson_lefthand"}) {
            var r=display.getAsJsonObject(hand).getAsJsonArray("rotation");
            Vector3f front=new Vector3f(0,0,-1).rotate(new Quaternionf().rotationXYZ(
                    (float)Math.toRadians(r.get(0).getAsFloat()),(float)Math.toRadians(r.get(1).getAsFloat()),(float)Math.toRadians(r.get(2).getAsFloat())));
            assertTrue(front.z>0.85F,hand+" must face the camera");
        }
    }
}
