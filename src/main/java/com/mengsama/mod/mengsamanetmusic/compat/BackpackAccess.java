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
import net.neoforged.fml.ModList;
import java.util.UUID;

 
public final class BackpackAccess {
    private BackpackAccess() {}
    public static boolean available() { return ModList.get() != null && ModList.get().isLoaded("sophisticatedbackpacks") && ModList.get().isLoaded("sophisticatedcore"); }
    public static MusicPlayerItem createItem(Block block, Item.Properties properties) {
        return available() ? Loaded.createItem(block, properties) : new MusicPlayerItem(block, properties);
    }
    public static void register() { if (available()) Loaded.register(); }
    public static ItemStack earbudDevice(Player player) { return available() ? Loaded.earbudDevice(player) : ItemStack.EMPTY; }
    public static ItemStack find(Player player, UUID instance) {
        return available() ? Loaded.find(player, instance) : ItemStack.EMPTY;
    }
    public static Binding read(FriendlyByteBuf buffer, Player player) {
        return available() ? Loaded.read(buffer, player) : null;
    }
    public static void open(ServerPlayer player, int container, int slot) {
        if (available()) Loaded.open(player, container, slot);
    }
    public static boolean validBlock(Level level, BlockPos pos, String target) {
        return available() && Loaded.validBlock(level, pos, target);
    }
    public static boolean refresh(ServerPlayer player, BlockPos pos, String target, long generation, long nonce, SongInfo song) {
        return available() && Loaded.refresh(player, pos, target, generation, nonce, song);
    }
     



    private static final class Loaded {
        static MusicPlayerItem createItem(Block block, Item.Properties properties) {
            return new com.mengsama.mod.mengsamanetmusic.compat.backpack.WalkmanUpgradeItem(block, properties);
        }
        static void register() { com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.register(); }
        static ItemStack earbudDevice(Player player) { return com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.earbudDevice(player); }
        static ItemStack find(Player player, UUID instance) { return com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.find(player, instance); }
        static Binding read(FriendlyByteBuf buffer, Player player) { return com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackBinding.read(buffer, player); }
        static void open(ServerPlayer player, int container, int slot) { com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.open(player, container, slot); }
        static boolean validBlock(Level level, BlockPos pos, String target) { return com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.validBlock(level, pos, target); }
        static boolean refresh(ServerPlayer player, BlockPos pos, String target, long generation, long nonce, SongInfo song) {
            return com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackIntegration.refresh(player, pos, target, generation, nonce, song);
        }
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
