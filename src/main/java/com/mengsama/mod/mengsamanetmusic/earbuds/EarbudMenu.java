package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mengsama.mod.mengsamanetmusic.init.ModMenuTypes;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class EarbudMenu extends AbstractContainerMenu {
    public final boolean caseMenu;
    public final SimpleContainer earbuds = new SimpleContainer(3);
    private final ItemStack source;
    private final MusicPlayerMenu parent;
    private final InteractionHand hand;
    private final Player owner;
    public EarbudMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(id, inv, b.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf)b), null, InteractionHand.MAIN_HAND);
    }
    private EarbudMenu(int id, Inventory inv, boolean caseMenu, ItemStack source, MusicPlayerMenu parent, InteractionHand hand) {
        super(ModMenuTypes.EARBUDS.get(), id); this.caseMenu=caseMenu; this.source=source; this.parent=parent; this.hand=hand; owner=inv.player;
        for (int i=0;i<3;i++) earbuds.setItem(i,EarbudSlots.get(source,i));
        for (int i=0;i<3;i++) {
            final int slot=i;
            addSlot(new Slot(earbuds,i,45+i*64,53) {
                @Override public boolean mayPlace(ItemStack item) { return (!caseMenu || slot!=0) && EarbudSlots.accepts(slot,item); }
                @Override public boolean mayPickup(Player player) { return !caseMenu || slot!=0; }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean isActive() { return !caseMenu || slot!=0; }
            });
        }
        for (int row=0;row<3;row++) for (int col=0;col<9;col++) addSlot(playerSlot(inv,9+row*9+col,35+col*18,112+row*18));
        for (int col=0;col<9;col++) addSlot(playerSlot(inv,col,35+col*18,170));
        earbuds.addListener(ignored -> persist());
    }
    private Slot playerSlot(Inventory inv,int index,int x,int y) {
        return new Slot(inv,index,x,y) { @Override public boolean mayPickup(Player player) { return getItem()!=source; } };
    }
    private void persist() {
        if (owner.level().isClientSide || !stillValid(owner)) return;
        boolean changed=false;
        for(int i=0;i<3;i++) if(!ItemStack.matches(EarbudSlots.get(source,i),earbuds.getItem(i))) changed=true;
        if (!changed) return;
        if (!caseMenu && owner instanceof ServerPlayer player) {
             
            EarbudSessions.disconnect(player,"耳机配置已改变，一起听已结束");
            parent.stopPlayback(player);
        }
        for(int i=0;i<3;i++) EarbudSlots.set(source,i,earbuds.getItem(i));
        if(!caseMenu) {
            MusicPlayerItem.setBroadcast(source,!EarbudSlots.canListen(owner,source));
            if(parent.getBackpackBinding()!=null) parent.getBackpackBinding().save(owner);
        }
        owner.getInventory().setChanged();
    }
    @Override public boolean stillValid(Player player) {
        if(player!=owner || !player.isAlive() || player.isSpectator()) return false;
        if(player.level().isClientSide) return true;
        return caseMenu ? player.getItemInHand(hand)==source : parent!=null && parent.resolveValidatedDevice(player)==source;
    }
    @Override public void clicked(int index,int button,ClickType type,Player player) {
        if(!stillValid(player)) return;
        if(type==ClickType.SWAP && (button>=0 && button<9 || button==40) && player.getInventory().getItem(button)==source) return;
        super.clicked(index,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player) || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); if(!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<3) { if(!moveItemStackTo(stack,3,slots.size(),true)) return ItemStack.EMPTY; }
        else if(stack.getItem() instanceof EarbudItem e && e.kind<3 && (!caseMenu || e.kind!=0)) {
            if(!moveItemStackTo(stack,e.kind,e.kind+1,false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); return copy;
    }
    @Override public void removed(Player player) {
        if(!player.level().isClientSide && caseMenu) ItemData.putBoolean(source, "EarbudCaseOpen",false);
        super.removed(player);
    }
    public static void openDevice(ServerPlayer player,MusicPlayerMenu parent) {
        ItemStack stack=parent.resolveValidatedDevice(player);
        if(stack.isEmpty() || !parent.supportsEarbudSlots()) return;
        open(player,false,stack,parent,InteractionHand.MAIN_HAND);
    }
    public static void openCase(ServerPlayer player,InteractionHand hand) {
        EarbudCaseOpening.begin(player, hand);
    }
    static void finishOpeningCase(ServerPlayer player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!(stack.getItem() instanceof EarbudItem e) || e.kind!=3) return;
        if(!ItemData.get(stack).getBoolean("EarbudCaseInitialized")) {
            EarbudSlots.initializeCase(stack,new ItemStack(ModItems.BLUETOOTH_LEFT.get()),new ItemStack(ModItems.BLUETOOTH_RIGHT.get()));
        }
        open(player,true,stack,null,hand);
    }
    private static void open(ServerPlayer player,boolean isCase,ItemStack stack,MusicPlayerMenu parent,InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new EarbudMenu(id,inv,isCase,stack,parent,hand),
                Component.literal(isCase?"蓝牙耳机盒":"耳机连接")),b->{b.writeBoolean(isCase);ItemStack.OPTIONAL_STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf)b,stack);});
    }
    public void returnToMusic(ServerPlayer player) {
        if(caseMenu || !stillValid(player)) return;
        if(parent.getBackpackBinding()!=null) {
            var binding=parent.getBackpackBinding();
            player.openMenu(new SimpleMenuProvider((id,inv,p)->MusicPlayerMenu.forBackpack(id,inv,binding),Component.literal("随身听")),
                    b->{b.writeByte(MusicPlayerMenu.Context.BACKPACK.ordinal());binding.write(b);});
        } else {
            UUID id=MusicPlayerItem.getOrCreateInstanceId(source);
            player.openMenu(new SimpleMenuProvider((wid,inv,p)->MusicPlayerMenu.forPlayerHand(wid,inv,id),Component.literal("随身听")),
                    b->{b.writeByte(MusicPlayerMenu.Context.PLAYER_HAND.ordinal());b.writeUUID(id);});
        }
    }
}
