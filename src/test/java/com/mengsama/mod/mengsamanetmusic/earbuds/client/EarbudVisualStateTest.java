package com.mengsama.mod.mengsamanetmusic.earbuds.client;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EarbudVisualStateTest {
    private static CompoundTag row(UUID owner,UUID device) {
        var row=new CompoundTag();row.putUUID("Owner",owner);row.putUUID("Device",device);row.putBoolean("Wired",true);return row;
    }
    private static CompoundTag snapshot(CompoundTag... rows) {
        var tag=new CompoundTag();var list=new ListTag();for(var row:rows)list.add(row);tag.put("Devices",list);return tag;
    }
    @Test void removingOneDeviceClearsOwnerAndGuestConnectionButPreservesOtherDevices() {
        var state=new EarbudVisualState();var owner=UUID.randomUUID();var device=UUID.randomUUID();var other=UUID.randomUUID();
        var shared=row(owner,device);shared.putUUID("Guest",UUID.randomUUID());
        state.replace(snapshot(shared,row(other,UUID.randomUUID())),new Object(),100);
        assertTrue(state.removeDevice(device));assertFalse(state.devices().containsKey(owner));
        assertEquals(1,state.devices().size());assertTrue(state.devices().containsKey(other));
        assertFalse(state.removeDevice(device));
    }
    @Test void lateRemovalOfAnOldDeviceDoesNotClearTheOwnersNewConnection() {
        var state=new EarbudVisualState();var world=new Object();var owner=UUID.randomUUID();var old=UUID.randomUUID();var next=UUID.randomUUID();
        state.replace(snapshot(row(owner,old)),world,100);
        state.replace(snapshot(row(owner,next)),world,110);
        assertFalse(state.removeDevice(old));assertEquals(next,state.devices().get(owner).getUUID("Device"));
    }
    @Test void emptySnapshotClearsModelsAndSnapshotDataIsCopied() {
        var state=new EarbudVisualState();var world=new Object();var owner=UUID.randomUUID();var device=UUID.randomUUID();
        var row=row(owner,device);state.replace(snapshot(row),world,100);row.putUUID("Device",UUID.randomUUID());
        assertEquals(device,state.devices().get(owner).getUUID("Device"));
        state.replace(snapshot(),world,110);assertTrue(state.devices().isEmpty());
    }
    @Test void staleModelsExpireEvenWithoutAnExplicitRemovalPacket() {
        var state=new EarbudVisualState();var world=new Object();
        state.replace(snapshot(row(UUID.randomUUID(),UUID.randomUUID())),world,100);
        assertFalse(state.expire(world,140));assertFalse(state.devices().isEmpty());
        assertTrue(state.expire(world,141));assertTrue(state.devices().isEmpty());
    }
    @Test void worldChangeDisconnectAndRewoundClockDiscardPreviousWorldModels() {
        for(int reason=0;reason<3;reason++) {
            var state=new EarbudVisualState();var world=new Object();
            state.replace(snapshot(row(UUID.randomUUID(),UUID.randomUUID())),world,100);
            assertTrue(state.expire(reason==0?new Object():reason==1?null:world,reason==2?90:100));
            assertTrue(state.devices().isEmpty());
        }
    }
    @Test void missingDeviceIdentityAndPacketsWithoutAWorldCannotLeaveModels() {
        var state=new EarbudVisualState();var incomplete=new CompoundTag();incomplete.putUUID("Owner",UUID.randomUUID());
        state.replace(snapshot(incomplete),new Object(),100);assertTrue(state.devices().isEmpty());
        state.replace(snapshot(row(UUID.randomUUID(),UUID.randomUUID())),null,100);assertTrue(state.devices().isEmpty());
    }
}
