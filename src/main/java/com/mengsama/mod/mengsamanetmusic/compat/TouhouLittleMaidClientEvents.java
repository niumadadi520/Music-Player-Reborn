package com.mengsama.mod.mengsamanetmusic.compat;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import com.mengsama.mod.mengsamanetmusic.network.OpenMaidMusicPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

 
@OnlyIn(Dist.CLIENT)
public final class TouhouLittleMaidClientEvents {
    private static final String BUTTON_ID = "mengsamanetmusic:maid_music";
    private static final int BUTTON_SIZE = 20;
    private static final int EDGE_GAP = 6;

    private TouhouLittleMaidClientEvents() {}

    @SubscribeEvent
    public static void onMaidContainerInit(MaidContainerGuiEvent.Init event) {
        var gui = event.getGui();
        var maid = gui.getMaid();
        if (maid.getTask() == null || !TouhouLittleMaidExtension.MUSIC_TASK_UID.equals(maid.getTask().getUid())) return;

        ItemStack device = EntityMusicDevice.heldPlayer(maid);
        if (device.isEmpty()) return;

        List<Rect2i> occupied = new ArrayList<>(gui.getExclusionArea());
        List<Integer> nativeRightXs = new ArrayList<>();
        for (var child : gui.children()) {
            if (child instanceof AbstractWidget widget) {
                occupied.add(new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
                nativeRightXs.add(widget.getX());
            }
        }
        for (AbstractWidget widget : gui.getEventAddButtons().values()) {
            occupied.add(new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        }

         
         
        int x = MaidGuiLayout.alignedButtonX(event.getLeftPos(), gui.getXSize(), EDGE_GAP, nativeRightXs);
        if (x + BUTTON_SIZE > gui.width - 2) return;
        int y = findFreeY(x, event.getTopPos() + 6, gui.height, occupied);
        if (y < 0) return;

        UUIDBoundButton button = new UUIDBoundButton(x, y, maid.getUUID(), maid.getId(),
                MusicPlayerItem.getOrCreateInstanceId(device));
        button.setTooltip(Tooltip.create(Component.translatable("gui.mengsamanetmusic.maid_music.tooltip")));
        event.addButton(BUTTON_ID, button);
    }

    private static int findFreeY(int x, int preferredY, int screenHeight, List<Rect2i> occupied) {
        int maxY = Math.max(0, screenHeight - BUTTON_SIZE - 2);
        int y = Math.max(2, Math.min(preferredY, maxY));
        while (y <= maxY) {
            Rect2i candidate = new Rect2i(x, y, BUTTON_SIZE, BUTTON_SIZE);
            boolean intersects = occupied.stream().anyMatch(area -> intersects(candidate, area));
            if (!intersects) return y;
            y += BUTTON_SIZE + 2;
        }
        return -1;
    }

    private static boolean intersects(Rect2i a, Rect2i b) {
        return a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    private static final class UUIDBoundButton extends AbstractButton {
        private final java.util.UUID maidId;
        private final int entityId;
        private final java.util.UUID instanceId;

        private UUIDBoundButton(int x, int y, java.util.UUID maidId, int entityId, java.util.UUID instanceId) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.translatable("gui.mengsamanetmusic.maid_music"));
            this.maidId = maidId;
            this.entityId = entityId;
            this.instanceId = instanceId;
        }

        @Override
        public void onPress() {
             
            this.active = false;
            ModNetwork.CHANNEL.sendToServer(new OpenMaidMusicPacket(maidId, entityId, instanceId));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            boolean pressed = isHovered && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed();
            int outer = active ? (pressed ? 0xFF101010 : hovered ? 0xFFFFFFFF : 0xFFA0A0A0) : 0xFF555555;
            int inner = pressed ? 0xFF606060 : hovered ? 0xFF8A8A8A : 0xFF6E6E6E;
            int highlight = pressed ? 0xFF303030 : 0xFFC6C6C6;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + 3, highlight);
            graphics.fill(getX() + 2, getY() + 3, getX() + 3, getY() + height - 2, highlight);
            int offset = pressed ? 1 : 0;
            graphics.renderItem(new ItemStack(ModItems.MUSIC_PLAYER.get()), getX() + 2 + offset, getY() + 2 + offset);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
