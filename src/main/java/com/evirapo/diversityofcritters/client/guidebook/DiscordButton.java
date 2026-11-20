package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.client.guidebook.data.Credits;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;

/**
 * Opens the community Discord after Minecraft's own "open this link?" screen. Greyed out with a
 * tooltip while credits.json has no invite link.
 */
public class DiscordButton extends BookButton {

    public DiscordButton(GuidebookScreen screen, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.diversityofcritters.guidebook.discord"), () -> open(screen));
        if (Credits.get().discord().isBlank()) {
            this.active = false;
            setTooltip(Tooltip.create(Component.translatable("gui.diversityofcritters.guidebook.discord_soon")));
        }
    }

    private static void open(GuidebookScreen screen) {
        String url = Credits.get().discord();
        Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) Util.getPlatform().openUri(url);
            Minecraft.getInstance().setScreen(screen);
        }, url, true));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        // A small chat-bubble mark in Discord's colour, left of the label.
        int x = getX() + 4, y = getY() + (this.height - 7) / 2;
        int color = this.active ? BookLayout.COLOR_DISCORD : BookLayout.COLOR_TEXT_SOFT;
        g.fill(x, y, x + 9, y + 6, color);
        g.fill(x + 2, y + 6, x + 4, y + 7, color);
        g.fill(x + 2, y + 2, x + 3, y + 4, 0xFFFFFFFF);
        g.fill(x + 6, y + 2, x + 7, y + 4, 0xFFFFFFFF);
    }
}
