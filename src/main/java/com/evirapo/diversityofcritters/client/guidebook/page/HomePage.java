package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookButton;
import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import com.evirapo.diversityofcritters.client.guidebook.DiscordButton;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The book's first spread: title on the left, the section buttons on the right. */
public class HomePage extends BookPage {

    private static final int BUTTON_W = 90, BUTTON_H = 14;
    private static final int[] SEPARATORS = {80, 119};

    @Override
    public ResourceLocation background() {
        return BookLayout.HOME;
    }

    @Override
    public void init(GuidebookScreen screen, int left, int top) {
        int x = left + BookLayout.RIGHT_CENTER - BUTTON_W / 2;
        button(screen, x, top + 30, "animal", null);
        button(screen, x, top + 46, "plant", () -> new ComingSoonPage(key("plants"), key("plants_sub")));
        button(screen, x, top + 62, "other_life", () -> new ComingSoonPage(key("other_life"), key("other_life_sub")));
        button(screen, x, top + 85, "care", null);
        button(screen, x, top + 101, "etc", null);
        button(screen, x, top + 124, "credit", CreditsPage::new);
        screen.addPageWidget(new DiscordButton(screen, x, top + 144, BUTTON_W, BUTTON_H));
    }

    /** A section button; with no page yet the button is shown but inactive. */
    private static void button(GuidebookScreen screen, int x, int y, String name, java.util.function.Supplier<BookPage> page) {
        BookButton button = new BookButton(x, y, BUTTON_W, BUTTON_H, key(name), () -> {
            if (page != null) screen.push(page.get());
        }).silent();
        button.active = page != null;
        screen.addPageWidget(button);
    }

    static Component key(String name) {
        return Component.translatable("gui.diversityofcritters.guidebook." + name);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        int lx = left + BookLayout.LEFT_CENTER, rx = left + BookLayout.RIGHT_CENTER;
        int pageW = BookLayout.LEFT_PAGE_X1 - BookLayout.LEFT_PAGE_X0;
        BookText.centered(g, font, key("title1"), lx, top + 18, BookLayout.COLOR_TEXT, 1.0F, pageW);
        BookText.centered(g, font, key("title2"), lx, top + 29, BookLayout.COLOR_TEXT, 1.0F, pageW);
        BookText.wrappedCentered(g, font, key("intro"), lx, top + 42, pageW - 8, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL);

        BookText.centered(g, font, key("species"), rx, top + 16, BookLayout.COLOR_TEXT, 1.0F, pageW);
        for (int y : SEPARATORS) {
            g.fill(rx - 50, top + y, rx + 50, top + y + 1, BookLayout.COLOR_BORDER);
        }
    }
}
