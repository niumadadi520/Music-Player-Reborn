package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderInfo;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BackpackDataTest {
    @BeforeAll static void bootstrap()throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");field.setAccessible(true);field.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
    private static IStorageWrapper storage(UpgradeHandler[] handler){
        var render=new RenderInfo(()->()->{}) {
            private CompoundTag tag=new CompoundTag();
            @Override protected void serializeRenderInfo(CompoundTag nbt){tag=nbt;}
            @Override protected Optional<CompoundTag> getRenderInfoTag(){return Optional.of(tag);}
        };
        return (IStorageWrapper)Proxy.newProxyInstance(BackpackDataTest.class.getClassLoader(),new Class[]{IStorageWrapper.class},(p,m,a)->switch(m.getName()){
            case "getUpgradeHandler"->handler[0];case "getRenderInfo"->render;case "getStorageType"->"backpack";case "getContentsUuid"->Optional.of(new UUID(1,2));default->null;
        });
    }
    @Test void wrapperConstructionIsReadOnlyAndSaveUsesLiveItemData(){
        UpgradeHandler[] handler={null};var storage=storage(handler);handler[0]=new UpgradeHandler(2,storage,new CompoundTag(),()->{},()->{});
        var item=new ItemStack(Items.STICK);UUID id=MusicPlayerItem.getOrCreateInstanceId(item);item.getOrCreateTag().putString("UserNote","keep");handler[0].setStackInSlot(0,item);
        var original=item.save(new CompoundTag());var saved=new AtomicReference<ItemStack>();
        var wrapper=new WalkmanUpgradeWrapper(storage,item,s->saved.set(s.copy()));assertEquals(original,item.save(new CompoundTag()));
        item.getOrCreateTag().putString("LatestPlaylistEdit","edited-through-open-menu");wrapper.save();
        assertNotNull(saved.get());assertEquals("edited-through-open-menu",saved.get().getOrCreateTag().getString("LatestPlaylistEdit"));
        assertEquals("keep",saved.get().getOrCreateTag().getString("UserNote"));assertEquals(id,MusicPlayerItem.getInstanceId(saved.get()));
    }
    @Test void removedWrapperCannotResurrectOrOverwriteAnotherUpgrade(){
        UpgradeHandler[] handler={null};var storage=storage(handler);handler[0]=new UpgradeHandler(2,storage,new CompoundTag(),()->{},()->{});
        var original=new ItemStack(Items.STICK);handler[0].setStackInSlot(0,original);var calls=new AtomicInteger();
        var wrapper=new WalkmanUpgradeWrapper(storage,original,s->calls.incrementAndGet());
        var replacement=new ItemStack(Items.DIAMOND);replacement.getOrCreateTag().putString("ReplacementData","untouched");handler[0].setStackInSlot(0,replacement);
        var before=replacement.save(new CompoundTag());original.getOrCreateTag().putString("StaleAsyncEdit","discard");wrapper.save();
        assertEquals(0,calls.get());assertSame(replacement,handler[0].getStackInSlot(0));assertEquals(before,replacement.save(new CompoundTag()));
    }
    @Test void stopClearsAnArmedEndTimerWithoutTouchingCustomData(){
        var item=new ItemStack(Items.STICK);item.getOrCreateTag().putString("UserData","keep");
        MusicPlayerItem.setPlay(item,true);MusicPlayerItem.setCurrentTime(item,123);
        item.getOrCreateTag().putBoolean("AutoAdvanceArmed",true);
        var wrapper=new WalkmanUpgradeWrapper(null,item,ignored->fail("An unattached wrapper must not save to storage"));
        wrapper.stop();
        assertFalse(MusicPlayerItem.isPlay(item));assertEquals(0,MusicPlayerItem.getCurrentTime(item));
        assertFalse(item.getOrCreateTag().getBoolean("AutoAdvanceArmed"));assertEquals("keep",item.getOrCreateTag().getString("UserData"));
    }
    @Test void realUpgradeExtractionKeepsEarbudsPlaylistAndIdentityButStopsPlayback() throws Exception {
        var upgrade=net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
                new net.minecraft.resources.ResourceLocation("mengsama_test","detach_upgrade"),
                new WalkmanUpgradeItem(net.minecraft.world.level.block.Blocks.STONE,new net.minecraft.world.item.Item.Properties().stacksTo(1)));
        UpgradeHandler[] handler={null};var storage=storage(handler);
        handler[0]=new UpgradeHandler(2,storage,new CompoundTag(),()->{},()->{});
        var stack=new ItemStack(upgrade);var id=MusicPlayerItem.getOrCreateInstanceId(stack);
        var earbuds=new CompoundTag();earbuds.putString("id","mengsamanetmusic:pink_wired_earbuds_both");earbuds.putByte("Count",(byte)1);
        earbuds.putString("CustomEarbudData","keep");var slots=new CompoundTag();slots.put("Slot0",earbuds);
        stack.getOrCreateTag().put("EarbudInventory",slots);
        var cds=net.minecraft.core.NonNullList.withSize(54,ItemStack.EMPTY);cds.set(17,new ItemStack(Items.MUSIC_DISC_CAT));
        MusicPlayerItem.saveAllCdsPreservingSlots(stack,cds);stack.getOrCreateTag().putString("ExtraData","preserve");
        assertSame(Items.MUSIC_DISC_CAT,MusicPlayerItem.loadAllCds(stack).get(17).getItem());
        MusicPlayerItem.setPlay(stack,true);MusicPlayerItem.setPaused(stack,true);MusicPlayerItem.setCurrentTime(stack,1200);
        stack.getOrCreateTag().putBoolean("AutoAdvanceArmed",true);
        handler[0].setStackInSlot(0,stack);
        var wrapper=(WalkmanUpgradeWrapper)handler[0].getSlotWrappers().get(0);assertNotNull(wrapper);
        BackpackIntegration.track(wrapper);
        var before=stack.save(new CompoundTag());
        var task=new java.util.concurrent.FutureTask<ItemStack>(() -> {
            assertEquals(before,handler[0].extractItem(0,1,true).save(new CompoundTag()));
            assertFalse(wrapper.retired);assertEquals(before,stack.save(new CompoundTag()));
            return handler[0].extractItem(0,1,false);
        });
        new Thread(net.minecraftforge.fml.util.thread.SidedThreadGroups.SERVER,task,"earbud-extraction-test").start();
        ItemStack extracted=task.get(10,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1,extracted.getCount());assertTrue(handler[0].getStackInSlot(0).isEmpty());
        assertTrue(wrapper.retired);assertNull(BackpackIntegration.current(wrapper));
        assertEquals(id,MusicPlayerItem.getInstanceId(extracted));
        assertEquals(slots,extracted.getTag().getCompound("EarbudInventory"));
        assertSame(Items.MUSIC_DISC_CAT,MusicPlayerItem.loadAllCds(extracted).get(17).getItem());
        assertEquals("preserve",extracted.getTag().getString("ExtraData"));
        assertFalse(MusicPlayerItem.isPlay(extracted));assertFalse(MusicPlayerItem.isPaused(extracted));
        assertEquals(0,MusicPlayerItem.getCurrentTime(extracted));assertFalse(extracted.getTag().getBoolean("AutoAdvanceArmed"));
        var expected=before.getCompound("tag").copy();var actual=extracted.getTag().copy();
        for(String key:java.util.List.of("IsPlay","IsPaused","CurrentTime","AutoAdvanceArmed")) {expected.remove(key);actual.remove(key);}
        assertEquals(expected,actual,"Removing an upgrade must preserve all non-playback item data");
    }
    @Test void retiredWrapperCannotStopReusedItemOrSaveBackIntoTheBackpack() {
        var stack=new ItemStack(Items.STICK);var calls=new AtomicInteger();
        var wrapper=new WalkmanUpgradeWrapper(null,stack,ignored->calls.incrementAndGet());
        wrapper.onBeforeRemoved();assertTrue(wrapper.retired);
        MusicPlayerItem.setPlay(stack,true);MusicPlayerItem.setCurrentTime(stack,800);
        var reused=stack.save(new CompoundTag());
        wrapper.onBeforeRemoved();wrapper.stop();wrapper.pause(true);wrapper.broadcast(true);wrapper.save();
        assertEquals(reused,stack.save(new CompoundTag()));assertEquals(0,calls.get());
    }
    @Test void removingStaleWrapperDoesNotUntrackItsReplacement() {
        UpgradeHandler[] handler={null};var storage=storage(handler);
        var stack=new ItemStack(Items.STICK);MusicPlayerItem.getOrCreateInstanceId(stack);
        var old=new WalkmanUpgradeWrapper(storage,stack,ignored->{});
        var current=new WalkmanUpgradeWrapper(storage,stack,ignored->{});
        BackpackIntegration.track(old);BackpackIntegration.track(current);
        try {old.onBeforeRemoved();assertSame(current,BackpackIntegration.current(current));assertFalse(current.retired);}
        finally {current.onBeforeRemoved();}
    }
}
