package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HandheldPlaybackStabilityTest {
    private static MusicPlayerItem item;
    @BeforeAll static void setup() throws Exception {
        MusicPlayerItemTest.bootstrapMinecraft();
        item=Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("mengsama_test", "stable_walkman"),new MusicPlayerItem(Blocks.STONE,new Item.Properties().stacksTo(1)));
    }
    private ItemStack playing(){
        ItemStack stack=new ItemStack(item);MusicPlayerItem.getOrCreateInstanceId(stack);
        MusicPlayerItem.setPlay(stack,true);MusicPlayerItem.setCurrentTime(stack,1200);return stack;
    }
    @Test void firstPersonBothHandsUsePlayingAnimationAndPauseReturnsIdle(){
        ItemStack device=playing();
        for(var view:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND}){
            MusicPlayerItem.setPaused(device,false);assertEquals("animation.pink_walkman.play",MusicPlayerItem.handheldPlaybackAnimation(device,view));
            MusicPlayerItem.setPaused(device,true);assertEquals("animation.pink_walkman.idle",MusicPlayerItem.handheldPlaybackAnimation(device,view));
        }
        MusicPlayerItem.setPaused(device,false);MusicPlayerItem.setPlay(device,false);
        assertEquals("animation.pink_walkman.idle",MusicPlayerItem.handheldPlaybackAnimation(device,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        assertTrue(item.isPerspectiveAware());
    }
    @Test void inventoryAndOtherViewsDoNotStartFirstPersonPlaybackAnimation(){
        for(var view:ItemDisplayContext.values())if(view!=ItemDisplayContext.FIRST_PERSON_LEFT_HAND && view!=ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
            assertEquals("animation.pink_walkman.idle",MusicPlayerItem.handheldPlaybackAnimation(playing(),view));
        assertEquals("animation.pink_walkman.idle",MusicPlayerItem.handheldPlaybackAnimation(null,null));
        assertEquals("animation.pink_walkman.idle",MusicPlayerItem.handheldPlaybackAnimation(ItemStack.EMPTY,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
    }
    @Test void selectedPlayAnimationHasMatchingItemBonesAndNeverMovesTheRoot() throws Exception {
        var parser=new com.google.gson.Gson();
        try(var animation=getClass().getResourceAsStream("/assets/mengsamanetmusic/animations/pink_walkman.animation.json");
            var geometry=getClass().getResourceAsStream("/assets/mengsamanetmusic/geo/pink_walkman_item.geo.json")){
            assertNotNull(animation);assertNotNull(geometry);
            var a=parser.fromJson(new java.io.InputStreamReader(animation,java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            var g=parser.fromJson(new java.io.InputStreamReader(geometry,java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            var play=a.getAsJsonObject("animations").getAsJsonObject("animation.pink_walkman.play");
            assertTrue(play.get("loop").getAsBoolean());var bones=play.getAsJsonObject("bones");
            assertEquals(java.util.Set.of("reel_left","reel_right","button_play"),bones.keySet());
            var present=new java.util.HashSet<String>();
            for(var bone:g.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))present.add(bone.getAsJsonObject().get("name").getAsString());
            assertTrue(present.containsAll(bones.keySet()));assertFalse(bones.has("root"));
        }
    }
    @Test void twoHundredSyncedCountdownTicksNeverLowerTheSameDevice(){
        ItemStack held=playing();
        for(int i=0;i<200;i++){
            ItemStack synced=held.copy();MusicPlayerItem.tickTime(synced);
            assertFalse(ItemStack.isSameItemSameComponents(held,synced));
            assertFalse(item.shouldCauseReequipAnimation(held,synced,false));
            held=synced;
        }
        assertEquals(1000,MusicPlayerItem.getCurrentTime(held));
    }
    @Test void pauseTrackAndOutputChangesDoNotMoveTheHandOrModifyStackData(){
        ItemStack old=playing(),updated=old.copy();
        MusicPlayerItem.setPaused(updated,true);MusicPlayerItem.setPlayIndex(updated,2);MusicPlayerItem.setBroadcast(updated,false);
        var beforeOld=ItemData.nullable(old).copy();var beforeNew=ItemData.nullable(updated).copy();
        assertFalse(item.shouldCauseReequipAnimation(old,updated,false));
        assertEquals(beforeOld,ItemData.nullable(old));assertEquals(beforeNew,ItemData.nullable(updated));
    }
    @Test void switchingSlotsOrItemsAndDroppingStillReequips(){
        ItemStack old=playing();
        assertTrue(item.shouldCauseReequipAnimation(old,old.copy(),true));
        assertTrue(item.shouldCauseReequipAnimation(old,new ItemStack(Items.STICK),false));
        assertTrue(item.shouldCauseReequipAnimation(old,ItemStack.EMPTY,false));
        assertTrue(item.shouldCauseReequipAnimation(ItemStack.EMPTY,old,false));
    }
    @Test void replacingWithAnotherPhysicalWalkmanStillReequips(){
        assertTrue(item.shouldCauseReequipAnimation(playing(),playing(),false));
    }
    @Test void firstServerIdentityAssignmentDoesNotRestartRaisingAnimation(){
        ItemStack old=new ItemStack(item),synced=old.copy();
        UUID id=MusicPlayerItem.getOrCreateInstanceId(synced);
        assertFalse(item.shouldCauseReequipAnimation(old,synced,false));
        assertFalse(ItemData.has(old));assertEquals(id,MusicPlayerItem.getInstanceId(synced));
    }
}
