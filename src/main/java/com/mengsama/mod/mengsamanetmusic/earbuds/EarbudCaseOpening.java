package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

 
@EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID)
public final class EarbudCaseOpening {
     
    static final int OPEN_TICKS = 14;
    private static final Map<UUID, Pending> OPENING = new HashMap<>();
    private record Pending(ItemStack stack, InteractionHand hand, int started, Object dimension) {}

    public static void begin(ServerPlayer player, InteractionHand hand) {
        if (player.isSpectator() || !player.isAlive() || player.containerMenu != player.inventoryMenu
                || OPENING.containsKey(player.getUUID())) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof EarbudItem item) || item.kind != 3) return;
        GeoItem.getOrAssignId(stack, player.serverLevel());
        ItemData.putBoolean(stack, "EarbudCaseOpen", true);
        OPENING.put(player.getUUID(), new Pending(stack, hand, player.tickCount, player.level().dimension()));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    static boolean finished(int started, int now) { return now - started >= OPEN_TICKS; }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = OPENING.get(player.getUUID());
        if (pending == null) return;
        if (!player.isAlive() || player.isSpectator() || player.getItemInHand(pending.hand) != pending.stack
                || player.containerMenu != player.inventoryMenu || player.level().dimension() != pending.dimension) {
            cancel(player);
        } else if (finished(pending.started, player.tickCount)) {
            OPENING.remove(player.getUUID());
            EarbudMenu.finishOpeningCase(player, pending.hand);
        }
    }

    private static void cancel(ServerPlayer player) {
        Pending pending = OPENING.remove(player.getUUID());
        if (pending != null) ItemData.putBoolean(pending.stack, "EarbudCaseOpen", false);
        player.getInventory().setChanged();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        OPENING.values().forEach(p -> ItemData.putBoolean(p.stack, "EarbudCaseOpen", false));
        OPENING.clear();
    }
}
