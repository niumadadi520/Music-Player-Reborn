package com.mengsama.mod.mengsamanetmusic.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHudVisibilityPacketTest {
    @Test
    void ownerTargetIsRecognizedWithoutMatchingOtherPlayers() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String target = "item:" + owner + ":4:" + UUID.randomUUID();

        assertTrue(PlayerHudVisibilityPacket.isOwnerTarget(target, owner));
        assertFalse(PlayerHudVisibilityPacket.isOwnerTarget(target, other));
        assertFalse(PlayerHudVisibilityPacket.isOwnerTarget("block:minecraft:overworld:1", owner));
    }
}
