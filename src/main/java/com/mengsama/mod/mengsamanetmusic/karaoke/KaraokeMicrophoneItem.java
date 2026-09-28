package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;
import java.util.UUID;

public final class KaraokeMicrophoneItem extends MusicPlayerItem {
    public static final String ID_TAG = "KaraokeMicrophoneId";
    private final boolean handheld;
    public KaraokeMicrophoneItem(Block block, String modelName) {
        super(block, new Properties().stacksTo(1), modelName);
        this.handheld = "pink_handheld_microphone".equals(modelName);
    }
    public boolean isHandheld() { return handheld; }
    public static UUID getId(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(ID_TAG) ? stack.getTag().getUUID(ID_TAG) : null;
    }
    public static UUID ensureId(ItemStack stack, net.minecraft.server.MinecraftServer server) {
        UUID id = getId(stack);
        if (id == null) { id = KaraokeData.get(server).create(); stack.getOrCreateTag().putUUID(ID_TAG, id); }
        KaraokeData.get(server).remember(id);
        return id;
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && entity instanceof ServerPlayer player) KaraokeServer.observeItem(player, stack);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            KaraokeServer.observeItem(serverPlayer, stack);
            UUID instance = getOrCreateInstanceId(stack);
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (window, inv, p) -> MusicPlayerMenu.forPlayerHand(window, inv, instance), stack.getHoverName()),
                    buf -> { buf.writeByte(MusicPlayerMenu.Context.PLAYER_HAND.ordinal()); buf.writeUUID(instance); });
            KaraokeServer.sendState(serverPlayer, "");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isSpectator()) return InteractionResult.PASS;
        if (player != null && player.isShiftKeyDown()) return use(context.getLevel(), player, context.getHand()).getResult();
         
        if (handheld) return InteractionResult.PASS;
        return super.useOn(context);
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
         
        return handheld ? InteractionResult.FAIL : super.place(context);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        UUID id = getId(stack);
        if (id != null) lines.add(Component.literal("连接码：" + KaraokeCode.format(id)));
        lines.add(Component.literal("Shift + 右键：K 歌开关与音乐界面"));
        lines.add(Component.literal(handheld ? "普通右键空麦克风架安装；空手右键取下" : "默认关闭麦克风；普通右键方块可放置"));
    }
}
