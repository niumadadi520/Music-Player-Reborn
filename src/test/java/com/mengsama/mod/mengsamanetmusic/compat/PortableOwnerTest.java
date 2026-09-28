package com.mengsama.mod.mengsamanetmusic.compat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableOwnerTest {
    @Test void sharedPrivateListenerDoesNotNeedToCarryOwnersDevice() {
        UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),device=UUID.randomUUID();
        String target="item:"+owner+":4:"+device;
        assertTrue(PlaybackTargetId.isItemOwner(target,owner));
        assertFalse(PlaybackTargetId.isItemOwner(target,guest));
        assertTrue(PlaybackTargetId.isItemOwner("item:"+owner+":35:"+device,owner));
    }
    @Test void otherPlaybackContextsAreNotMistakenForDroppedHandheldDevices() {
        UUID player=UUID.randomUUID(),device=UUID.randomUUID();
        for(String target:new String[]{"maid:dimension:"+player+":"+device,"backpack:"+player+":"+device,"block:1:2:3:"+device,"item:"+player+":4:invalid",""})
            assertFalse(PlaybackTargetId.isItemOwner(target,player));
        assertFalse(PlaybackTargetId.isItemOwner(null,player));
        assertFalse(PlaybackTargetId.isItemOwner("item:"+player+":4:"+device,null));
    }
}
