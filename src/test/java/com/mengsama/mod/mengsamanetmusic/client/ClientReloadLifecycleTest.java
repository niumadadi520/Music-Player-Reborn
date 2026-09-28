package com.mengsama.mod.mengsamanetmusic.client;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class ClientReloadLifecycleTest {
     
    @Test void reloadListenerContainsNoScreenRegistration() throws Exception {
        ClassNode listener = read("ClientModEvents$1.class");
        int calls = 0;
        for (MethodNode method : listener.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/screens/MenuScreens")) calls++;
        }
        assertEquals(0, calls, "Resource reload must not register a menu screen a second time");
    }
    @Test void screensRemainRegisteredInClientSetup() throws Exception {
        int calls = 0;
        for (MethodNode method : read("ClientModEvents.class").methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/screens/MenuScreens")
                    && call.desc.contains("ScreenConstructor")) {
                assertTrue(method.name.startsWith("lambda$onClientSetup$")); calls++;
            }
        }
        assertEquals(3, calls, "Music player, playlist and earbuds screens must all remain available");
    }
    private static ClassNode read(String file) throws Exception {
        try (InputStream stream = ClientReloadLifecycleTest.class.getResourceAsStream(file)) {
            assertNotNull(stream); ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
}
