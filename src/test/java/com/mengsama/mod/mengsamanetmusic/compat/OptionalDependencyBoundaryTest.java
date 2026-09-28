package com.mengsama.mod.mengsamanetmusic.compat;

import net.neoforged.fml.common.EventBusSubscriber;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptionalDependencyBoundaryTest {
    @Test void commonPlaybackAndPacketBytecodeNeverDirectlyReferencesTlmTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        String prefix = "com/mengsama/mod/mengsamanetmusic/";
        for (String name : List.of("compat/MaidMusicAccess", "compat/EntityMusicDevice", "gui/MusicPlayerMenu",
                "item/MusicPlayerItem", "network/PlayerPlayMusicPacket", "network/OpenMaidMusicPacket",
                "network/ReturnToMaidGuiPacket", "util/PlayerNetMusicSound")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(prefix + name + ".class")) {
                assertNotNull(input);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    private void check(String type) { if (type != null && type.contains("com/github/tartaricacid/touhoulittlemaid")) violations.add(name + ": " + type); }
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        check(descriptor);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) { check(type); }
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { check(owner); check(desc); }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { check(owner); check(desc); }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
        assertFalse(MaidMusicAccess.isMaid(null));
        assertFalse(MaidMusicAccess.mayControl(null, null));
    }

    @Test void optionalServerHandlersAreNotAutomaticallyRegisteredByForge() throws Exception {
        for (String name : List.of("ActiveMaidMusicTracker", "MaidLyricSynchronizer")) {
            List<String> annotations = new ArrayList<>();
            try (var input = getClass().getResourceAsStream(name + ".class")) {
                assertNotNull(input);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        annotations.add(descriptor); return null;
                    }
                }, ClassReader.SKIP_CODE);
            }
            assertTrue(annotations.stream().noneMatch(a -> a.contains("EventBusSubscriber")));
        }
    }
}
