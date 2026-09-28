package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

 
public final class TransparentButton extends AbstractButton {
    private final OnPress onPress;
    private boolean selected;
    private RosewoodPlayerLayout.Control skinControl;

    public TransparentButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        setTooltip(Tooltip.create(message));
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public void setSelected(boolean selected) { this.selected = selected; }


    public void setSkinControl(RosewoodPlayerLayout.Control control) {
        this.skinControl = control;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override public void setMessage(Component message) {
        boolean changed = getMessage() == null || !getMessage().equals(message);
        super.setMessage(message);
         
        if (changed && skinControl != RosewoodPlayerLayout.Control.MODE) setTooltip(Tooltip.create(message));
    }

    @Override
    public void onPress() { this.onPress.onPress(this); }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var bounds = new RosewoodPlayerLayout.Rect(getX(), getY(), width, height);
        if (com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom()) {
            if(skinControl!=null)MusicPlayerSkin.renderControl(graphics,skinControl,bounds,getMessage(),isHoveredOrFocused(),selected,active);
            else {
                com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.button(graphics,bounds,isHoveredOrFocused(),selected,active);
                MusicPlayerSkin.renderLabel(graphics,com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.content(bounds),getMessage(),active?MusicPlayerSkin.primary():MusicPlayerSkin.secondary());
            }
            return;
        }
        if (skinControl == RosewoodPlayerLayout.Control.NAV_SEARCH && !selected) MusicPlayerSkin.renderInactiveSearchTab(graphics, bounds);
        else if (skinControl != null) MusicPlayerSkin.renderControl(graphics, skinControl, bounds, getMessage());
        else {
            graphics.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?0xFFD8ABA6:0xE6ECDBC5);
            graphics.renderOutline(getX(),getY(),width,height,0xFFAD8175);
            MusicPlayerSkin.renderLabel(graphics,bounds,getMessage(),active?MusicPlayerSkin.primary():MusicPlayerSkin.secondary());
        }
        if (!active) graphics.fill(getX(), getY(), getX() + width, getY() + height, (0x66000000 | (MusicPlayerSkin.inputBackground() & 0xFFFFFF)));
        if (selected) {
            graphics.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 1, MusicPlayerSkin.accent());
        }
        if (this.isHoveredOrFocused() || this.selected) {
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height,
                    MusicPlayerSkin.accent());
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @FunctionalInterface
    public interface OnPress { void onPress(TransparentButton button); }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x, y, width = 100, height = 20;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public TransparentButton build() { return new TransparentButton(x, y, width, height, message, onPress); }
    }
}
