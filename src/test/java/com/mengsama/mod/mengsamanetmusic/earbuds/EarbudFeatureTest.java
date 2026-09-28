package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableCurve;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.handoff.HandoffClientAdapter;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffState;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import org.joml.Matrix4f;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EarbudFeatureTest {
    static EarbudItem wired,left,right,box;static MusicPlayerItem walkman;
    @BeforeAll static void bootstrap()throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");field.setAccessible(true);field.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        wired=register("pink_wired_earbuds_both",new EarbudItem(0,"pink_wired_earbuds_both"));
        left=register("pink_bluetooth_earbuds_left",new EarbudItem(1,"pink_bluetooth_earbuds_left"));
        right=register("pink_bluetooth_earbuds_right",new EarbudItem(2,"pink_bluetooth_earbuds_right"));
        box=register("pink_bluetooth_case",new EarbudItem(3,"pink_bluetooth_case"));
        walkman=register("test_earbud_walkman",new MusicPlayerItem(Blocks.STONE,new Item.Properties().stacksTo(1)));
    }
    private static <T extends Item>T register(String name,T item){return Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("mengsamanetmusic",name),item);}
    @Test void onlyWalkmanOffersEarbudSlotsEvenThoughMicrophonesInheritItsPlayback() {
        var standing=register("test_ui_standing_mic",new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeMicrophoneItem(Blocks.STONE,"pink_microphone"));
        var handheld=register("test_ui_handheld_mic",new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeMicrophoneItem(Blocks.STONE,"pink_handheld_microphone"));
        assertTrue(MusicPlayerItem.supportsEarbuds(new ItemStack(walkman)));
        assertFalse(MusicPlayerItem.supportsEarbuds(new ItemStack(standing)));
        assertFalse(MusicPlayerItem.supportsEarbuds(new ItemStack(handheld)));
        assertFalse(MusicPlayerItem.supportsEarbuds(ItemStack.EMPTY));
        assertFalse(MusicPlayerItem.supportsEarbuds(new ItemStack(box)));
    }
    @Test void openingTheLidDoesNotLowerAndReequipTheHeldCase() {
        ItemStack closed=new ItemStack(box), opened=closed.copy();
        opened.getOrCreateTag().putBoolean("EarbudCaseOpen",true);
        assertFalse(box.shouldCauseReequipAnimation(closed,opened,false));
        assertTrue(box.shouldCauseReequipAnimation(closed,opened,true));
        assertTrue(box.shouldCauseReequipAnimation(closed,new ItemStack(left),false));
    }
    @Test void earbudSlotRoundTripPreservesPlaylistIdentityAndCustomData() {
        ItemStack device=new ItemStack(walkman);UUID id=MusicPlayerItem.getOrCreateInstanceId(device);
        var songs=new ListTag();var song=new CompoundTag();song.putString("Title","认真的雪");songs.add(song);
        device.getOrCreateTag().put("ExistingPlaylist",songs);device.getOrCreateTag().putString("CustomName","keep");
        CompoundTag original=device.getTag().copy();ItemStack earbud=new ItemStack(left);earbud.getOrCreateTag().putString("OwnerNote","left-only");
        EarbudSlots.set(device,1,earbud);ItemStack restored=ItemStack.of(device.save(new CompoundTag()));
        assertEquals("left-only",EarbudSlots.get(restored,1).getOrCreateTag().getString("OwnerNote"));
        assertEquals(id,MusicPlayerItem.getInstanceId(restored));CompoundTag other=restored.getTag().copy();other.remove(EarbudSlots.KEY);assertEquals(original,other);
        EarbudSlots.set(restored,1,ItemStack.EMPTY);assertEquals(0,EarbudSlots.mask(restored));assertEquals(original.get("ExistingPlaylist"),restored.getTag().get("ExistingPlaylist"));
    }
    @Test void slotsRejectWrongEarCaseAndOversizedStackWithoutChangingDevice() {
        var device=new ItemStack(walkman);var before=device.save(new CompoundTag());
        assertThrows(IllegalArgumentException.class,()->EarbudSlots.set(device,1,new ItemStack(right)));
        assertThrows(IllegalArgumentException.class,()->EarbudSlots.set(device,0,new ItemStack(box)));
        assertThrows(IllegalArgumentException.class,()->EarbudSlots.set(device,1,new ItemStack(left,2)));
        assertThrows(IllegalArgumentException.class,()->EarbudSlots.set(device,3,ItemStack.EMPTY));
        assertEquals(before,device.save(new CompoundTag()));
    }
    @Test void wiredHasPriorityAndAllInstalledModesOverrideBroadcast() {
        ItemStack device=new ItemStack(walkman);MusicPlayerItem.setBroadcast(device,true);assertTrue(MusicPlayerItem.isBroadcast(device));
        EarbudSlots.set(device,1,new ItemStack(left));assertEquals(1,EarbudSlots.mask(device));assertFalse(MusicPlayerItem.isBroadcast(device));
        EarbudSlots.set(device,2,new ItemStack(right));assertEquals(3,EarbudSlots.mask(device));assertFalse(EarbudSlots.wired(device));
        EarbudSlots.set(device,0,new ItemStack(wired));assertTrue(EarbudSlots.wired(device));assertFalse(MusicPlayerItem.isBroadcast(device));
        EarbudSlots.set(device,0,ItemStack.EMPTY);assertEquals(3,EarbudSlots.mask(device));assertFalse(EarbudSlots.wired(device));
    }
    @Test void caseReopenAndSaveReloadNeverRegenerateRemovedEarbuds() {
        ItemStack item=new ItemStack(box);EarbudSlots.initializeCase(item,new ItemStack(left),new ItemStack(right));
        assertFalse(EarbudSlots.get(item,1).isEmpty());EarbudSlots.set(item,1,ItemStack.EMPTY);EarbudSlots.set(item,2,ItemStack.EMPTY);
        ItemStack restored=ItemStack.of(item.save(new CompoundTag()));EarbudSlots.initializeCase(restored,new ItemStack(left),new ItemStack(right));
        assertTrue(EarbudSlots.get(restored,1).isEmpty());assertTrue(EarbudSlots.get(restored,2).isEmpty());
    }
    @Test void invitationCanOnlyBeAnsweredByItsRecipientBeforeExpiry() {
        UUID owner=UUID.randomUUID(),guest=UUID.randomUUID();var invitation=new EarbudSessions.Invitation(UUID.randomUUID(),owner,guest,UUID.randomUUID(),0,true,400);
        assertFalse(invitation.mayAnswer(owner,100));assertFalse(invitation.mayAnswer(UUID.randomUUID(),100));assertTrue(invitation.mayAnswer(guest,400));assertFalse(invitation.mayAnswer(guest,401));
    }
    @Test void warningAndDisconnectThresholdsIncludeExactRangeBoundary() {
        assertFalse(EarbudSlots.nearLimit(true,3.19*3.19));assertTrue(EarbudSlots.nearLimit(true,3.2*3.2));
        assertTrue(EarbudSlots.inRange(true,16));assertFalse(EarbudSlots.inRange(true,16.001));
        assertTrue(EarbudSlots.nearLimit(false,25.6*25.6));assertTrue(EarbudSlots.inRange(false,1024));assertFalse(EarbudSlots.inRange(false,1024.001));
        assertFalse(EarbudSlots.inRange(true,Double.NaN));assertFalse(EarbudSlots.inRange(false,Double.POSITIVE_INFINITY));
    }
    @Test void movingCableRetainsBothEndpointsAndFiniteGeometry() {
        var start=new CableCurve.Point(.2,1.5,.1);var end=new CableCurve.Point(3.7,1.6,-.5);var down=new CableCurve.Point(0,-1,0);
        for(int frame=0;frame<100;frame++) {
            assertEquals(start,CableCurve.sample(start,end,down,.06,.01,frame,0));assertEquals(end,CableCurve.sample(start,end,down,.06,.01,frame,1));
            for(int segment=0;segment<=28;segment++){var p=CableCurve.sample(start,end,down,.06,.01,frame,segment/28.0);assertTrue(Double.isFinite(p.x()+p.y()+p.z()));}
        }
    }
    @Test void handedEarbudEndsExactlyAtRecipientForBothSides() {
        for(var side:HandoffMotion.Side.values()) {
            var a=HandoffClientAdapter.capture(new Matrix4f().translation(0,1.5f,0).rotateZ((float)Math.PI));
            var b=HandoffClientAdapter.capture(new Matrix4f().translation(side==HandoffMotion.Side.LEFT?-.875f:.875f,1.5f,-.5625f).rotateY((float)Math.PI).rotateZ((float)Math.PI));
            for(int step=0;step<=72;step++) {
                var motion=HandoffMotion.sample(a.head(),b.head(),side,step/20.0,new HandoffMotion.Vec(0,1,0));
                assertTrue(motion.valid(),motion.reason());assertEquals(1,motion.visibility().visibleCopies());assertTrue(motion.earPosition().finite());
                if(step>=53) assertTrue(motion.earPosition().subtract(b.head().anchor(side)).length()<1e-8);
            }
        }
    }
}
