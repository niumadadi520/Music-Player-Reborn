package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

 
public final class MusicVolumeButton extends AbstractButton {
    private int shownPercent = -1;
    private boolean skinned;

    public MusicVolumeButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        updateLabel();
    }

    public void setSkinned(boolean skinned) { this.skinned = skinned; }

    private void updateLabel() {
        int value = ClientMusicVolume.percent();
        if (shownPercent == value) return;
        shownPercent = value;
        setMessage(Component.literal(value == 0 ? "已静音" : "音量" + value + "%"));
        setTooltip(Tooltip.create(Component.literal("本机音乐音量：" + value + "%（当前游戏会话）\n左键：静音 / 恢复\n右键：提高 25%，到顶后回到 25%\n滚轮：微调 5%\n只影响本模组音乐")));
    }

    @Override public void onPress() {
        ClientMusicVolume.toggleMute();
        updateLabel();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
            ClientMusicVolume.increaseStep();
            updateLabel();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || delta == 0) return false;
        ClientMusicVolume.setPercent(ClientMusicVolume.percent() + (delta > 0 ? 5 : -5));
        updateLabel();
        return true;
    }

    @Override protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLabel();
        var bounds = new RosewoodPlayerLayout.Rect(getX(), getY(), width, height);
        if (com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom()) {
            if(skinned)MusicPlayerSkin.renderVolume(graphics,bounds,shownPercent,isHoveredOrFocused(),active);
            else {
                com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.button(graphics,bounds,isHoveredOrFocused(),false,active);
                MusicPlayerSkin.renderLabel(graphics,bounds,getMessage(),MusicPlayerSkin.primary());
            }
            return;
        }
        if(skinned)MusicPlayerSkin.renderVolume(graphics,bounds,shownPercent);
        else MusicPlayerSkin.renderLabel(graphics,bounds,getMessage(),MusicPlayerSkin.primary());
        if(isHoveredOrFocused())graphics.renderOutline(getX(),getY(),width,height,MusicPlayerSkin.accent());
    }

    @Override protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
