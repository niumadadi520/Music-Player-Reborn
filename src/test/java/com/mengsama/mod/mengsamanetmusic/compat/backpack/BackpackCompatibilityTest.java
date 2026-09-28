package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.CharmDynamics;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AnnotationNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackpackCompatibilityTest {
    @Test void suppliedMotionAndClearanceContract() throws Exception { CharmDynamicsCheck.main(new String[0]); }
    @Test void reloadAndTeleportDiscardOldMotion() {
        CharmDynamics state=new CharmDynamics();
        for(int i=0;i<60;i++)state.sample(i,i*.08,0,0,i*3,true,false);
        state.sample(70,1000,80,-1000,350,true,false);
        assertEquals(CharmDynamics.Pose.ZERO,state.interpolated(1));
        state.sample(71,Double.NaN,80,0,0,true,false);
        assertEquals(CharmDynamics.Pose.ZERO,state.interpolated(.5));
    }
    @Test void oneRenderHookMatchesTheOlderSupportedBackpackBytecode() throws Exception {
        ClassNode target=read("net/p3pp3rf1y/sophisticatedbackpacks/client/render/BackpackLayerRenderer.class");
        ClassNode mixin=read("com/mengsama/mod/mengsamanetmusic/mixin/BackpackCharmLayerMixin.class");
        Set<String> signatures=new HashSet<>();target.methods.forEach(m->signatures.add(m.name+m.desc));
        int matches=0;
        for(var method:mixin.methods) {
            var annotations=method.visibleAnnotations==null?List.<AnnotationNode>of():method.visibleAnnotations;
            for(var annotation:annotations)if(annotation.desc.endsWith("/Inject;")) {
                for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals("method"))
                    for(Object name:(List<?>)annotation.values.get(i+1))if(signatures.contains(name.toString()))matches++;
            }
        }
        assertEquals(1,matches,"Exactly one explicitly described hook must match; the old API includes IBackpackModel");
    }
    @Test void modernAndLegacyBodyTransformsReachTheSameAttachmentSpace() {
        var old=new org.joml.Matrix4f().rotateY((float)Math.PI).translate(0,-.75f,-.3f).translate(0,1,0).rotateZ((float)Math.PI);
        var modern=new org.joml.Matrix4f().rotateY((float)Math.PI).rotateZ((float)Math.PI).translate(0,-.25f,-.3f);
        var anchor=new org.joml.Vector3f((4.25f-8)/16,(5.55f-8)/16,(1.8f-8)/16);
        assertTrue(old.transformPosition(new org.joml.Vector3f(anchor)).distance(modern.transformPosition(new org.joml.Vector3f(anchor)))<.00001f);
    }
    private static ClassNode read(String resource)throws Exception{
        try(var stream=BackpackCompatibilityTest.class.getClassLoader().getResourceAsStream(resource)){
            assertNotNull(stream,resource);var node=new ClassNode();new ClassReader(stream).accept(node,ClassReader.SKIP_CODE);return node;
        }
    }
}
