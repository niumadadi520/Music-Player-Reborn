package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.block.IMusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BlockAddSongPacket {
    private final BlockPos blockPos;
    private final SongInfo songInfo;
    private final boolean playNow;

    public BlockAddSongPacket(BlockPos blockPos, SongInfo songInfo, boolean playNow) {
        this.blockPos = blockPos;
        this.songInfo = songInfo;
        this.playNow = playNow;
    }

    public static void encode(BlockAddSongPacket message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.blockPos);
        CompoundTag tag = new CompoundTag();
        SongInfo.serializeNBT(message.songInfo, tag);
        buf.writeNbt(tag);
        buf.writeBoolean(message.playNow);
    }

    public static BlockAddSongPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        SongInfo info = SongInfo.deserializeNBT(tag);
        boolean playNow = buf.readBoolean();
        return new BlockAddSongPacket(pos, info, playNow);
    }

    public static void handle(BlockAddSongPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketDispatch.withPlayer(contextSupplier, sender -> {

                if (!(sender.containerMenu instanceof MusicPlayerPlaylistMenu menu) || !menu.stillValid(sender)) return;
                IMusicPlayerBlockEntity be = menu.getBlockEntity();
                if (be != null && message.blockPos.equals(be.getBlockPos())) {

                    var playerInv = be.getPlayerInv();
                    for (int i = 0; i < playerInv.getSlots(); i++) {
                        ItemStack existingStack = playerInv.getStackInSlot(i);
                        if (existingStack.isEmpty()) continue;
                        SongInfo existing = com.mengsama.mod.mengsamanetmusic.item.MusicListItem.getSongInfo(existingStack);
                        if (message.songInfo.sameIdentity(existing)) {
                            if (message.playNow) {
                                 
                                menu.clickMenuButton(sender, MusicPlayerPlaylistMenu.BUTTON_SELECT_BASE + i);
                            } else {
                                sender.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.mengsamanetmusic.duplicate_song"));
                            }
                            return;
                        }
                    }
                    int targetSlot = -1;
                    for (int i = 0; i < playerInv.getSlots(); i++) {
                        if (playerInv.getStackInSlot(i).isEmpty()) {
                            targetSlot = i;
                            break;
                        }
                    }

                    if (targetSlot >= 0) {
                        ItemStack cdStack = new ItemStack(ModItems.MUSIC_LIST.get());
                        com.mengsama.mod.mengsamanetmusic.item.MusicListItem.addSongInfo(message.songInfo, cdStack);
                        if (!playerInv.insertItem(targetSlot, cdStack, false).isEmpty()) return;
                        be.markDirty();
                        menu.broadcastChanges();
                        if (message.playNow) {
                            menu.clickMenuButton(sender, MusicPlayerPlaylistMenu.BUTTON_SELECT_BASE + targetSlot);
                        }
                    } else {
                        sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("设备歌单已满，无法加入歌曲"));
                    }
                }
        });
    }
}
