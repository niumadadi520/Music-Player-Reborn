package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

public final class EarbudItem extends Item implements GeoItem {
     
    public final int kind;
    public final String model;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public EarbudItem(int kind, String model) { super(new Properties().stacksTo(1)); this.kind = kind; this.model = model; }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (kind == 3) controllers.add(new AnimationController<>(this, "lid", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            boolean open = stack != null && ItemData.has(stack) && ItemData.nullable(stack).getBoolean("EarbudCaseOpen");
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.pink_bluetooth_case." + (open ? "open" : "close")));
        }));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind != 3) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer server) EarbudMenu.openCase(server, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
    public void createClientExtensions(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudItemRenderer(EarbudItem.this);
                return renderer;
            }
        });
    }
}
