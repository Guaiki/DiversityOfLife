package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.resources.ResourceLocation;

/**
 * Measurements and colours of the guidebook, taken from the client's textures. Every position is in
 * texture pixels, which the screen draws 1:1 in GUI units, relative to the book's top-left corner.
 */
public final class BookLayout {

    public static final int WIDTH = 294;
    public static final int HEIGHT = 180;

    /** Usable text area of each page. */
    public static final int LEFT_PAGE_X0 = 18, LEFT_PAGE_X1 = 142;
    public static final int RIGHT_PAGE_X0 = 152, RIGHT_PAGE_X1 = 275;
    public static final int PAGE_TOP = 10, PAGE_BOTTOM = 166;

    public static final int LEFT_CENTER = (LEFT_PAGE_X0 + LEFT_PAGE_X1) / 2;
    public static final int RIGHT_CENTER = (RIGHT_PAGE_X0 + RIGHT_PAGE_X1) / 2;

    /** Species pages: the photo is painted into the texture; text goes below it. */
    public static final int PHOTO_X0 = 22, PHOTO_X1 = 140, PHOTO_BOTTOM = 64;

    /** Species pages: the IUCN dots, the diet box and the enrichment box are painted into the texture. */
    public static final int DOT_Y = 108, DOT_SIZE = 9;
    /** The rings drawn around the dots: 6 px, or 7 px for the yellow one when both rings share a dot. */
    public static final int RING_RADIUS = 6, MAX_RING_RADIUS = 7;
    public static final int DIET_X0 = 168, DIET_X1 = 213;
    public static final int ENRICH_X0 = 213, ENRICH_X1 = 259;
    public static final int BOX_Y0 = 125, BOX_Y1 = 165;
    /** Between the bottom of the rings (y 115) and the top of the boxes (y 125). */
    public static final int BOX_TITLE_Y = 118;

    public static final int COLOR_BOX = 0xFFA5A889;
    public static final int COLOR_BORDER = 0xFF999C7F;
    public static final int COLOR_PAPER = 0xFFB7BA98;
    public static final int COLOR_TEXT = 0xFF3F4A2E;
    public static final int COLOR_TEXT_SOFT = 0xFF6E7556;
    public static final int COLOR_HOVER = 0xFF9A9D7E;
    public static final int COLOR_DISCORD = 0xFF5865F2;

    public static final ResourceLocation HOME = texture("home");
    public static final ResourceLocation ANIMALS = texture("animals");
    public static final ResourceLocation PLAIN = texture("plain");

    public static ResourceLocation texture(String name) {
        return new ResourceLocation(DiversityOfCritters.MODID, "textures/gui/guidebook/" + name + ".png");
    }

    private BookLayout() {
    }
}
