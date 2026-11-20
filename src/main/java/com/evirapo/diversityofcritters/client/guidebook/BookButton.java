package com.evirapo.diversityofcritters.client.guidebook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Flat button in the book's paper palette. The client's textures have no button art, so it is
 * drawn with fills: a box with a one-pixel border, darker while hovered, faded while inactive.
 */
public class BookButton extends AbstractButton {

    private final Runnable onPress;
    private boolean silent;

    public BookButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    /** The screen plays its own page sound when a button changes page; this skips the click on top of it. */
    public BookButton silent() {
        this.silent = true;
        return this;
    }

    @Override
    public void onPress() {
        this.onPress.run();
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (!this.silent) super.playDownSound(soundManager);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = getX(), y0 = getY(), x1 = x0 + this.width, y1 = y0 + this.height;
        int fill = !this.active ? BookLayout.COLOR_PAPER : this.isHoveredOrFocused() ? BookLayout.COLOR_HOVER : BookLayout.COLOR_BOX;
        g.fill(x0, y0, x1, y1, BookLayout.COLOR_BORDER);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);

        Font font = Minecraft.getInstance().font;
        int color = this.active ? BookLayout.COLOR_TEXT : BookLayout.COLOR_TEXT_SOFT;
        int textWidth = font.width(getMessage());
        float scale = textWidth > this.width - 4 ? (this.width - 4) / (float) textWidth : 1.0F;
        g.pose().pushPose();
        g.pose().translate(x0 + this.width / 2.0F, y0 + (this.height - 8 * scale) / 2.0F + 0.5F, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, getMessage(), -textWidth / 2, 0, color, false);
        g.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
