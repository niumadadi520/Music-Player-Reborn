package com.mengsama.mod.mengsamanetmusic.compat;

import org.junit.jupiter.api.Test;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class RemovedWalkmanCuriosTest {
    @Test void releaseDoesNotDeclareWalkmanSlotsTagsRendererOrOpeningButton() throws Exception {
        try(var jar=new ZipFile(System.getProperty("mengsama.releaseJar"))) {
            for(var path:java.util.List.of("data/curios/tags/items/belt.json","data/curios/tags/items/curio.json",
                    "data/mengsamanetmusic/curios/slots/belt.json","data/mengsamanetmusic/curios/entities/walkman_player.json"))
                assertNull(jar.getEntry(path),path);
            for(var path:java.util.List.of("compat/WornWalkmanAccess","compat/CuriosWalkmanCompat","client/renderer/WornWalkmanPose",
                    "client/renderer/CuriosWalkmanRenderer","client/WornWalkmanButton","network/OpenWornMusicPacket"))
                assertNull(jar.getEntry("com/mengsama/mod/mengsamanetmusic/"+path+".class"),path);
        }
    }
    @Test void existingHeadphoneCuriosSupportStillShips() throws Exception {
        try(var jar=new ZipFile(System.getProperty("mengsama.releaseJar"))) {
            assertNotNull(jar.getEntry("com/mengsama/mod/mengsamanetmusic/compat/CuriosHeadphonesCompat.class"));
            assertNotNull(jar.getEntry("com/mengsama/mod/mengsamanetmusic/client/renderer/CuriosHeadphonesRenderer.class"));
            assertNotNull(jar.getEntry("data/curios/tags/items/head.json"));
        }
    }
}
