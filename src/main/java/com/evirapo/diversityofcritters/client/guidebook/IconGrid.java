package com.evirapo.diversityofcritters.client.guidebook;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Small item icons in a box that is too small for the whole list: as many as fit, paged by the mouse
 * wheel over the box and on their own every few seconds while the mouse is elsewhere. Positions are
 * in book space; the page passes the book's corner when drawing.
 */
public class IconGrid {

    /** One icon; a null tooltip shows the item's own name. */
    public record Icon(ItemStack stack, @Nullable Component tooltip) {
    }

    private static final int CELL = 12;
    private static final float ITEM_SCALE = 11 / 16.0F;
    /** 5 seconds. */
    static final int AUTO_PAGE_TICKS = 100;

    private final int x, y, w, h;
    private final List<Icon> icons;
    private final int perPage;
    private int page, ticks;
    private boolean mouseOver;
    @Nullable private Icon hovered;

    public IconGrid(int x, int y, int w, int h, List<Icon> icons) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.icons = icons;
        this.perPage = Math.max(1, (w / CELL) * (h / CELL));
    }

    private int pages() {
        return Math.max(1, (this.icons.size() + this.perPage - 1) / this.perPage);
    }

    public void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int cols = this.w / CELL;
        int x0 = left + this.x, y0 = top + this.y;
        this.mouseOver = mouseX >= x0 && mouseX < x0 + this.w && mouseY >= y0 && mouseY < y0 + this.h;
        this.hovered = null;
        int first = this.page * this.perPage;
        for (int i = first; i < Math.min(this.icons.size(), first + this.perPage); i++) {
            int slot = i - first;
            int cx = x0 + (slot % cols) * CELL, cy = y0 + (slot / cols) * CELL;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(ITEM_SCALE, ITEM_SCALE, 1);
            g.renderItem(this.icons.get(i).stack(), 0, 0);
            g.pose().popPose();
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                this.hovered = this.icons.get(i);
            }
        }
        // Page marks under the box: one short dash per page, the current one darker.
        int pages = pages();
        if (pages > 1) {
            int markW = 3, gap = 2, total = pages * markW + (pages - 1) * gap;
            int mx = x0 + (this.w - total) / 2, my = y0 + this.h + 3;
            for (int p = 0; p < pages; p++) {
                g.fill(mx + p * (markW + gap), my, mx + p * (markW + gap) + markW, my + 1,
                        p == this.page ? BookLayout.COLOR_TEXT : BookLayout.COLOR_BORDER);
            }
        }
    }

    /** Tooltip of the icon under the mouse, drawn last so it sits over everything. */
    public void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (this.hovered == null) return;
        if (this.hovered.tooltip() != null) {
            g.renderTooltip(font, this.hovered.tooltip(), mouseX, mouseY);
        } else {
            g.renderTooltip(font, this.hovered.stack(), mouseX, mouseY);
        }
    }

    public boolean mouseScrolled(double delta) {
        if (!this.mouseOver || pages() < 2) return false;
        this.page = Math.floorMod(this.page - (int) Math.signum(delta), pages());
        this.ticks = 0;
        return true;
    }

    public void tick() {
        if (this.mouseOver || pages() < 2) {
            this.ticks = 0;
            return;
        }
        if (++this.ticks >= AUTO_PAGE_TICKS) {
            this.ticks = 0;
            this.page = (this.page + 1) % pages();
        }
    }
}
