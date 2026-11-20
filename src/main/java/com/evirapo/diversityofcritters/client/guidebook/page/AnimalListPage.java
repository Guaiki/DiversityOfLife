package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookButton;
import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookScreen;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookSearch;
import com.evirapo.diversityofcritters.client.guidebook.data.GuidebookData;
import com.evirapo.diversityofcritters.client.guidebook.data.SpeciesEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The animal index: search box and "F" tag filters over a scrolling list. Animals the player has not
 * discovered show as "???". The page object stays on the screen's stack, so coming back from a
 * species keeps the search, the filters and the scroll.
 */
public class AnimalListPage extends BookPage {

    private static final int SEARCH_X = 156, SEARCH_Y = 14, SEARCH_W = 96, SEARCH_H = 14;
    private static final int LIST_X0 = 156, LIST_X1 = 268, LIST_Y0 = 32, LIST_Y1 = 166, ROW_H = 14;
    private static final int PANEL_Y = 32, PANEL_ROW = 12, PANEL_COL_W = 56;

    private final List<SpeciesEntry> species = GuidebookData.species();
    private final Set<String> filters = new LinkedHashSet<>();
    private String query = "";
    private int scroll;
    private boolean filterOpen;

    private GuidebookScreen screen;
    private List<SpeciesEntry> shown = List.of();
    private int left, top;

    @Override
    public ResourceLocation background() {
        return BookLayout.ANIMALS;
    }

    @Override
    public void init(GuidebookScreen screen, int left, int top) {
        this.screen = screen;
        this.left = left;
        this.top = top;
        Font font = Minecraft.getInstance().font;
        EditBox search = new EditBox(font, left + SEARCH_X + 3, top + SEARCH_Y + 3, SEARCH_W - 6, SEARCH_H - 4, HomePage.key("search"));
        search.setBordered(false);
        search.setTextColor(BookLayout.COLOR_TEXT);
        search.setHint(HomePage.key("search"));
        search.setValue(this.query);
        search.setResponder(value -> {
            this.query = value;
            this.scroll = 0;
            refresh();
        });
        screen.addPageWidget(search);
        screen.addPageWidget(new BookButton(left + SEARCH_X + SEARCH_W + 2, top + SEARCH_Y, 14, SEARCH_H,
                Component.literal("F"), () -> this.filterOpen = !this.filterOpen));
        refresh();
    }

    private void refresh() {
        List<SpeciesEntry> result = new ArrayList<>();
        for (SpeciesEntry entry : this.species) {
            boolean known = this.screen.state().isDiscovered(entry.entityId());
            if (GuidebookSearch.matches(I18n.get(entry.nameKey()), entry.tags(), known, this.query, this.filters)) {
                result.add(entry);
            }
        }
        this.shown = result;
        this.scroll = Math.min(this.scroll, maxScroll());
    }

    private int visibleRows() {
        return (LIST_Y1 - LIST_Y0) / ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, this.shown.size() - visibleRows());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        int lx = left + BookLayout.LEFT_CENTER;
        int pageW = BookLayout.LEFT_PAGE_X1 - BookLayout.LEFT_PAGE_X0;
        BookText.centered(g, font, HomePage.key("animals"), lx, top + 24, BookLayout.COLOR_TEXT, 1.0F, pageW);
        BookText.wrappedCentered(g, font, HomePage.key("animals_sub"), lx, top + 36, pageW - 8, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL);
        if (!this.filters.isEmpty()) {
            Component active = Component.translatable("gui.diversityofcritters.guidebook.filters_active", String.join(", ", this.filters));
            BookText.wrappedCentered(g, font, active, lx, top + 50, pageW - 8, BookLayout.COLOR_TEXT, BookText.SMALL);
        }
        long knownCount = this.species.stream().filter(e -> this.screen.state().isDiscovered(e.entityId())).count();
        BookText.centered(g, font, Component.translatable("gui.diversityofcritters.guidebook.discovered", knownCount, this.species.size()),
                lx, top + 154, BookLayout.COLOR_TEXT, BookText.SMALL, pageW);

        // Search box frame; the EditBox itself draws the text.
        frame(g, left + SEARCH_X, top + SEARCH_Y, left + SEARCH_X + SEARCH_W, top + SEARCH_Y + SEARCH_H, BookLayout.COLOR_BOX);

        // List
        frame(g, left + LIST_X0, top + LIST_Y0, left + LIST_X1, top + LIST_Y1, BookLayout.COLOR_BOX);
        if (this.shown.isEmpty()) {
            BookText.centered(g, font, HomePage.key("no_match"), left + (LIST_X0 + LIST_X1) / 2, top + LIST_Y0 + 10,
                    BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, LIST_X1 - LIST_X0 - 6);
        }
        int hovered = this.filterOpen ? -1 : rowAt(mouseX, mouseY);
        for (int i = 0; i < visibleRows() && i + this.scroll < this.shown.size(); i++) {
            SpeciesEntry entry = this.shown.get(i + this.scroll);
            int y0 = top + LIST_Y0 + i * ROW_H;
            if (i + this.scroll == hovered) {
                g.fill(left + LIST_X0 + 1, y0 + 1, left + LIST_X1 - 1, y0 + ROW_H, BookLayout.COLOR_HOVER);
            }
            g.fill(left + LIST_X0 + 1, y0 + ROW_H, left + LIST_X1 - 1, y0 + ROW_H + 1, BookLayout.COLOR_BORDER);
            boolean known = this.screen.state().isDiscovered(entry.entityId());
            Component name = known ? Component.translatable(entry.nameKey()) : Component.literal("???");
            BookText.centered(g, font, name, left + (LIST_X0 + LIST_X1) / 2, y0 + 4,
                    known ? BookLayout.COLOR_TEXT : BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, LIST_X1 - LIST_X0 - 6);
        }
        if (maxScroll() > 0) {
            int track = LIST_Y1 - LIST_Y0 - 2;
            int thumb = Math.max(8, track * visibleRows() / this.shown.size());
            int y = top + LIST_Y0 + 1 + (track - thumb) * this.scroll / maxScroll();
            g.fill(left + LIST_X1 - 3, y, left + LIST_X1 - 1, y + thumb, BookLayout.COLOR_TEXT_SOFT);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        if (!this.filterOpen) return;
        Font font = Minecraft.getInstance().font;
        int rows = (GuidebookSearch.FILTER_TAGS.size() + 1) / 2;
        int x0 = left + LIST_X0, y0 = top + PANEL_Y;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        frame(g, x0, y0, left + LIST_X1, y0 + rows * PANEL_ROW + 4, BookLayout.COLOR_PAPER);
        for (int i = 0; i < GuidebookSearch.FILTER_TAGS.size(); i++) {
            String tag = GuidebookSearch.FILTER_TAGS.get(i);
            int cx = x0 + 4 + (i % 2) * PANEL_COL_W, cy = y0 + 4 + (i / 2) * PANEL_ROW;
            g.fill(cx, cy, cx + 7, cy + 7, BookLayout.COLOR_BORDER);
            g.fill(cx + 1, cy + 1, cx + 6, cy + 6, this.filters.contains(tag) ? BookLayout.COLOR_TEXT : BookLayout.COLOR_PAPER);
            g.pose().pushPose();
            g.pose().translate(cx + 10, cy + 1, 0);
            g.pose().scale(BookText.SMALL, BookText.SMALL, 1);
            g.drawString(font, Component.translatable("gui.diversityofcritters.guidebook.tag." + tag), 0, 0, BookLayout.COLOR_TEXT, false);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int fill) {
        g.fill(x0, y0, x1, y1, BookLayout.COLOR_BORDER);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    /** Index into {@link #shown} under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - this.left, y = mouseY - this.top;
        if (x < LIST_X0 || x >= LIST_X1 || y < LIST_Y0 || y >= LIST_Y1) return -1;
        int index = (int) ((y - LIST_Y0) / ROW_H) + this.scroll;
        return index < this.shown.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.filterOpen) {
            int rows = (GuidebookSearch.FILTER_TAGS.size() + 1) / 2;
            double x = mouseX - this.left - LIST_X0 - 4, y = mouseY - this.top - PANEL_Y - 4;
            if (x >= 0 && x < PANEL_COL_W * 2 && y >= 0 && y < rows * PANEL_ROW) {
                int index = (int) (y / PANEL_ROW) * 2 + (int) (x / PANEL_COL_W);
                if (index < GuidebookSearch.FILTER_TAGS.size()) {
                    String tag = GuidebookSearch.FILTER_TAGS.get(index);
                    if (!this.filters.remove(tag)) this.filters.add(tag);
                    this.scroll = 0;
                    refresh();
                    click();
                }
                return true;
            }
            // A click anywhere else closes the panel; the F button toggles it itself.
            if (!overFilterButton(mouseX, mouseY)) this.filterOpen = false;
            return false;
        }
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            SpeciesEntry entry = this.shown.get(row);
            if (this.screen.state().isDiscovered(entry.entityId())) {
                this.screen.push(new SpeciesPage(entry));
            }
            return true;
        }
        return false;
    }

    private boolean overFilterButton(double mouseX, double mouseY) {
        double x = mouseX - this.left - (SEARCH_X + SEARCH_W + 2), y = mouseY - this.top - SEARCH_Y;
        return x >= 0 && x < 14 && y >= 0 && y < SEARCH_H;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.filterOpen || rowAt(mouseX, mouseY) < 0 && !insideList(mouseX, mouseY)) return false;
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(delta)));
        return true;
    }

    private boolean insideList(double mouseX, double mouseY) {
        double x = mouseX - this.left, y = mouseY - this.top;
        return x >= LIST_X0 && x < LIST_X1 && y >= LIST_Y0 && y < LIST_Y1;
    }
}
