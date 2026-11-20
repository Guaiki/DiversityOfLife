package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A section with no content yet (Plant, Other Life). */
public class ComingSoonPage extends BookPage {

    private final Component title, subtitle;

    public ComingSoonPage(Component title, Component subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public ResourceLocation background() {
        return BookLayout.PLAIN;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        int lx = left + BookLayout.LEFT_CENTER, rx = left + BookLayout.RIGHT_CENTER;
        int pageW = BookLayout.LEFT_PAGE_X1 - BookLayout.LEFT_PAGE_X0;
        BookText.centered(g, font, this.title, lx, top + 28, BookLayout.COLOR_TEXT, 1.0F, pageW);
        BookText.wrappedCentered(g, font, this.subtitle, lx, top + 42, pageW - 8, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL);
        BookText.wrappedCentered(g, font, HomePage.key("coming_soon"), rx, top + 80, pageW - 16, BookLayout.COLOR_TEXT_SOFT, 1.0F);
    }
}
