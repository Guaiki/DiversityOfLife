package com.evirapo.diversityofcritters.client.guidebook;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Text helpers for the book: no shadow, optional scale, centred or wrapped. */
public final class BookText {

    public static final float SMALL = 0.75F;

    /** Draws {@code text} centred on {@code centerX}, shrunk further if it is wider than {@code maxWidth}. */
    public static void centered(GuiGraphics g, Font font, Component text, int centerX, int y, int color, float scale, int maxWidth) {
        int width = font.width(text);
        float s = width * scale > maxWidth ? maxWidth / (float) width : scale;
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, text, -width / 2, 0, color, false);
        g.pose().popPose();
    }

    /** Wraps {@code text} to {@code width} (screen units) and centres each line. Returns the height used. */
    public static int wrappedCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int width, int color, float scale) {
        List<FormattedCharSequence> lines = font.split(text, (int) (width / scale));
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(font, line, -font.width(line) / 2, i * 10, color, false);
        }
        g.pose().popPose();
        return Math.round(lines.size() * 10 * scale);
    }

    private BookText() {
    }
}
