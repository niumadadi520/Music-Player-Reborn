package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import java.util.UUID;

 
public final class BackpackAccess {
    private BackpackAccess() {}
    public static boolean available() { return ModList.get().isLoaded("sophisticatedbackpacks") && ModList.get().isLoaded("sophisticatedcore"); }
    public static MusicPlayerItem createItem(Block block, Item.Properties properties) {
        return available() ? new com.mengsama.mod.mengsamanetmusic.compat.backpack.WalkmanUpgradeItem(block, properties) : new MusicPlayerItem(block, properties);
    }
    public static void register() { if (available()) com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.register(); }
    public static ItemStack earbudDevice(Player player) { return available() ? com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.earbudDevice(player) : ItemStack.EMPTY; }
    public static ItemStack find(Player player, UUID instance) {
        return available() ? com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.find(player, instance) : ItemStack.EMPTY;
    }
    public static Binding read(FriendlyByteBuf buffer, Player player) {
        return available() ? com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackBinding.read(buffer, player) : null;
    }
    public static void open(ServerPlayer player, int container, int slot) {
        if (available()) com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.open(player, container, slot);
    }
    public static boolean validBlock(Level level, BlockPos pos, String target) {
        return available() && com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.validBlock(level, pos, target);
    }
    public static boolean refresh(ServerPlayer player, BlockPos pos, String target, long generation, long nonce, SongInfo song) {
        return available() && com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.refresh(player, pos, target, generation, nonce, song);
    }
    public interface Binding {
        ItemStack resolve(Player player);
        void save(Player player);
        String target(Player player);
        void write(FriendlyByteBuf buffer);
        void play(ServerPlayer player, SongInfo song, int second, boolean paused);
        void stop(ServerPlayer player);
        void pause(ServerPlayer player, boolean paused);
        void broadcast(ServerPlayer player, boolean broadcast);
        void returnToBackpack(ServerPlayer player);
        boolean portable();
    }
}
