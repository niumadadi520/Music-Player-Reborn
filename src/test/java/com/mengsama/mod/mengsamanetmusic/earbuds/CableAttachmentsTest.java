package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableAttachments;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableAttachments.Kind;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableCurve.Point;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CableAttachmentsTest {
    @Test void heldDeviceNeverUsesTheOtherDeviceOnTheBackpack() {
        var samples = new CableAttachments();
        UUID held = UUID.randomUUID(), backpack = UUID.randomUUID();
        Point hand = new Point(.2,.5,-.4), back = new Point(-.3,.8,.9);
        samples.capture(backpack, Kind.BACKPACK, back);
        samples.capture(held, Kind.HAND, hand);
        assertEquals(new CableAttachments.Plug(Kind.HAND,hand),samples.resolve(held));
        assertEquals(new CableAttachments.Plug(Kind.BACKPACK,back),samples.resolve(backpack));
    }
    @Test void missingHandSampleCannotFallBackToUnrelatedBackpack() {
        var samples = new CableAttachments();
        samples.capture(UUID.randomUUID(), Kind.BACKPACK, new Point(1,2,3));
        assertNull(samples.resolve(UUID.randomUUID()));
        assertNull(samples.resolve(null));
    }
    @Test void duplicateIdentityDuringRenderSyncPrefersVisibleHeldItemOverBackpack() {
        var samples = new CableAttachments(); UUID id = UUID.randomUUID();
        for (Kind kind:Kind.values()) samples.capture(id,kind,new Point(kind.ordinal(),0,0));
        assertEquals(Kind.HAND,samples.resolve(id).kind());
    }
    @Test void changingFramesAndMovingToBackpackCannotReuseOldHandPose() {
        var samples=new CableAttachments();UUID id=UUID.randomUUID();
        samples.capture(id,Kind.HAND,new Point(1,2,3));samples.clear();assertNull(samples.resolve(id));
        Point hip=new Point(-.38,.81,-.07);samples.capture(id,Kind.BACKPACK,hip);
        assertEquals(new CableAttachments.Plug(Kind.BACKPACK,hip),samples.resolve(id));
    }
    @Test void firstPersonHidesBothOwnerAndSharedListenersCableButNotOtherPlayers() {
        UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),viewer=UUID.randomUUID();
        assertFalse(CableAttachments.visible(true,owner,owner,null));
        assertFalse(CableAttachments.visible(true,owner,owner,guest));
        assertFalse(CableAttachments.visible(true,guest,owner,guest));
        assertTrue(CableAttachments.visible(true,viewer,owner,guest));
        assertTrue(CableAttachments.visible(false,owner,owner,guest));
        assertTrue(CableAttachments.visible(false,guest,owner,guest));
    }
}
