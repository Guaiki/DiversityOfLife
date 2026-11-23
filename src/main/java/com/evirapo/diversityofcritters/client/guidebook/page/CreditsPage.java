package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import com.evirapo.diversityofcritters.client.guidebook.DiscordButton;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookScreen;
import com.evirapo.diversityofcritters.client.guidebook.data.Credits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The team, grouped by role, on the left; the Discord button on the right. */
public class CreditsPage extends BookPage {

    private static final int ROLE_HEIGHT = 8, NAME_HEIGHT = 11, GROUP_GAP = 4;

    private final List<Credits.Group> team = Credits.get().team();

    @Override
    public ResourceLocation background() {
        return BookLayout.PLAIN;
    }

    @Override
    public void init(GuidebookScreen screen, int left, int top) {
        screen.addPageWidget(new DiscordButton(screen, left + BookLayout.RIGHT_CENTER - 45, top + 96, 90, 14));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        int lx = left + BookLayout.LEFT_CENTER, rx = left + BookLayout.RIGHT_CENTER;
        int pageW = BookLayout.LEFT_PAGE_X1 - BookLayout.LEFT_PAGE_X0;
        BookText.centered(g, font, HomePage.key("credits"), lx, top + 28, BookLayout.COLOR_TEXT, 1.0F, pageW);
        int y = top + 42 + BookText.wrappedCentered(g, font, HomePage.key("credits_sub"), lx, top + 42, pageW - 8, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL) + 6;
        for (Credits.Group group : this.team) {
            BookText.centered(g, font, HomePage.key("role." + group.role()), lx, y, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, pageW);
            y += ROLE_HEIGHT;
            for (String name : group.names()) {
                BookText.centered(g, font, Component.literal(name), lx, y, BookLayout.COLOR_TEXT, 1.0F, pageW);
                y += NAME_HEIGHT;
            }
            y += GROUP_GAP;
        }
        BookText.wrappedCentered(g, font, HomePage.key("thanks"), rx, top + 70, pageW - 16, BookLayout.COLOR_TEXT_SOFT, 1.0F);
    }
}
