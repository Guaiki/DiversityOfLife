package com.evirapo.diversityofcritters.client.guidebook;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * One spread of the guidebook. The screen draws {@link #background()} and the widgets the page added
 * in {@link #init}; the page draws everything else in {@link #render}.
 *
 * {@code left} and {@code top} are the book's top-left corner on screen, so a page positions things
 * as {@code left + BookLayout.X}.
 */
public abstract class BookPage {

    public abstract ResourceLocation background();

    /** Adds this page's widgets. Called every time the page becomes active or the window resizes. */
    public void init(GuidebookScreen screen, int left, int top) {
    }

    /** Drawn over the background and under the widgets. */
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
    }

    /** Drawn over the widgets: tooltips and popups. */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    public void tick() {
    }
}
