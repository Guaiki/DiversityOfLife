package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookScreen;
import com.evirapo.diversityofcritters.client.guidebook.IconGrid;
import com.evirapo.diversityofcritters.client.guidebook.data.SpeciesEntry;
import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One animal's spread. The photo, the range map, the IUCN dots and the two boxes are painted into the
 * species texture; this page writes the name, taxonomy and fact, rings the real-world (white) and
 * this-world (yellow) status, and fills the diet and enrichment boxes.
 */
public class SpeciesPage extends BookPage {

    private static final int RING_REAL = 0xFFFFFFFF, RING_WORLD = 0xFFFFD21F;
    private static final int TEXT_X0 = BookLayout.PHOTO_X0, TEXT_X1 = BookLayout.PHOTO_X1;
    private static final int FACT_Y0 = 104, FACT_Y1 = 166, FACT_LINE = 8;
    private static final int GRID_MARGIN = 2;
    /** Ring legend above the status dots: centre line, swatch ring radius, and spacing. */
    private static final int LEGEND_Y = BookLayout.DOT_Y - 13, LEGEND_RING = 3, LEGEND_PAD = 2, LEGEND_GAP = 8;

    private final SpeciesEntry entry;
    private final ResourceLocation background;
    private final IconGrid diet, enrichment;
    @Nullable private ConservationTier worldTier;
    private int factScroll;
    private int left, top;

    public SpeciesPage(SpeciesEntry entry) {
        this.entry = entry;
        boolean hasTexture = Minecraft.getInstance().getResourceManager().getResource(entry.background()).isPresent();
        this.background = hasTexture ? entry.background() : BookLayout.PLAIN;
        this.diet = grid(BookLayout.DIET_X0, BookLayout.DIET_X1, dietIcons(entry));
        this.enrichment = grid(BookLayout.ENRICH_X0, BookLayout.ENRICH_X1, enrichmentIcons(entry));
    }

    private static IconGrid grid(int x0, int x1, List<IconGrid.Icon> icons) {
        return new IconGrid(x0 + GRID_MARGIN, BookLayout.BOX_Y0 + GRID_MARGIN,
                x1 - x0 - GRID_MARGIN * 2, BookLayout.BOX_Y1 - BookLayout.BOX_Y0 - GRID_MARGIN * 2, icons);
    }

    private static List<IconGrid.Icon> dietIcons(SpeciesEntry entry) {
        List<IconGrid.Icon> icons = new ArrayList<>();
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, entry.dietTag()))) {
            icons.add(new IconGrid.Icon(new ItemStack(item.value()), null));
        }
        return icons;
    }

    private static List<IconGrid.Icon> enrichmentIcons(SpeciesEntry entry) {
        List<IconGrid.Icon> icons = new ArrayList<>();
        for (SpeciesEntry.EnrichmentEntry e : entry.enrichment()) {
            Item item = BuiltInRegistries.ITEM.get(e.item());
            if (item != Items.AIR) icons.add(new IconGrid.Icon(new ItemStack(item), Component.translatable(e.tooltipKey())));
        }
        return icons;
    }

    @Override
    public ResourceLocation background() {
        return this.background;
    }

    @Override
    public void init(GuidebookScreen screen, int left, int top) {
        this.left = left;
        this.top = top;
        this.worldTier = screen.state().worldTiers().get(this.entry.entityId());
    }

    private List<FormattedCharSequence> factLines(Font font) {
        return font.split(Component.translatable(this.entry.factKey()), (int) ((TEXT_X1 - TEXT_X0) / BookText.SMALL));
    }

    private int factVisibleLines() {
        return (FACT_Y1 - FACT_Y0) / FACT_LINE;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        int cx = left + (TEXT_X0 + TEXT_X1) / 2, textW = TEXT_X1 - TEXT_X0;

        BookText.centered(g, font, Component.translatable(this.entry.nameKey()), cx, top + 68, BookLayout.COLOR_TEXT, 1.0F, textW);
        BookText.centered(g, font, Component.literal(this.entry.scientificName()).withStyle(ChatFormatting.ITALIC),
                cx, top + 79, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, textW);
        BookText.centered(g, font, Component.translatable("gui.diversityofcritters.guidebook.genus", this.entry.genus()),
                cx, top + 87, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, textW);
        BookText.centered(g, font, Component.translatable("gui.diversityofcritters.guidebook.family", this.entry.family()),
                cx, top + 95, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, textW);

        List<FormattedCharSequence> lines = factLines(font);
        g.enableScissor(left + TEXT_X0, top + FACT_Y0, left + TEXT_X1, top + FACT_Y1);
        g.pose().pushPose();
        g.pose().translate(left + TEXT_X0, top + FACT_Y0, 0);
        g.pose().scale(BookText.SMALL, BookText.SMALL, 1);
        for (int i = this.factScroll; i < Math.min(lines.size(), this.factScroll + factVisibleLines() + 1); i++) {
            g.drawString(font, lines.get(i), 0, Math.round((i - this.factScroll) * FACT_LINE / BookText.SMALL), BookLayout.COLOR_TEXT, false);
        }
        g.pose().popPose();
        g.disableScissor();
        if (this.factScroll + factVisibleLines() < lines.size()) {
            BookText.centered(g, font, Component.literal("v"), left + TEXT_X1 - 2, top + FACT_Y1 - 6, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, 8);
        }

        ring(g, left, top, this.entry.realStatus(), BookLayout.RING_RADIUS, RING_REAL);
        if (this.worldTier != null) {
            ring(g, left, top, this.worldTier, this.worldTier == this.entry.realStatus() ? BookLayout.MAX_RING_RADIUS : BookLayout.RING_RADIUS, RING_WORLD);
        }
        ringLegend(g, font, left, top);

        // The box edges are one-pixel borders at both x0 and x1, so the middle is (x0 + x1 + 1) / 2.
        float dietCx = left + (BookLayout.DIET_X0 + BookLayout.DIET_X1 + 1) / 2.0F;
        float enrichCx = left + (BookLayout.ENRICH_X0 + BookLayout.ENRICH_X1 + 1) / 2.0F;
        BookText.centered(g, font, HomePage.key("diet"), dietCx, top + BookLayout.BOX_TITLE_Y, BookLayout.COLOR_TEXT, BookText.SMALL, 44);
        BookText.centered(g, font, HomePage.key("enrichment"), enrichCx, top + BookLayout.BOX_TITLE_Y, BookLayout.COLOR_TEXT, BookText.SMALL, 44);
        this.diet.render(g, left, top, mouseX, mouseY);
        this.enrichment.render(g, left, top, mouseX, mouseY);
    }

    /**
     * Names the two rings just above the status dots: a small ringed dot in each colour followed by
     * "Real world" or "This world", centred on the right page.
     */
    private void ringLegend(GuiGraphics g, Font font, int left, int top) {
        Component real = HomePage.key("real_world"), world = HomePage.key("this_world");
        int swatch = LEGEND_RING * 2 + 1;
        float realW = (font.width(real) - 1) * BookText.SMALL, worldW = (font.width(world) - 1) * BookText.SMALL;
        float total = swatch + LEGEND_PAD + realW + LEGEND_GAP + swatch + LEGEND_PAD + worldW;
        float x = left + BookLayout.RIGHT_CENTER - total / 2.0F;
        int cy = top + LEGEND_Y;
        x = legendEntry(g, font, real, RING_REAL, x, cy) + LEGEND_GAP;
        legendEntry(g, font, world, RING_WORLD, x, cy);
    }

    /** One legend entry starting at {@code x}; returns where it ends. */
    private static float legendEntry(GuiGraphics g, Font font, Component label, int color, float x, int cy) {
        int cx = Math.round(x) + LEGEND_RING;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, BookLayout.COLOR_TEXT);
        ringAt(g, cx, cy, LEGEND_RING, color);
        float textX = cx + LEGEND_RING + 1 + LEGEND_PAD;
        g.pose().pushPose();
        g.pose().translate(textX, cy - 3, 0);
        g.pose().scale(BookText.SMALL, BookText.SMALL, 1);
        g.drawString(font, label, 0, 0, BookLayout.COLOR_TEXT_SOFT, false);
        g.pose().popPose();
        return textX + (font.width(label) - 1) * BookText.SMALL;
    }

    /** A one-pixel ring of {@code radius} around a status dot, corners cut so it reads as round. */
    private static void ring(GuiGraphics g, int left, int top, ConservationTier tier, int radius, int color) {
        ringAt(g, left + tier.dotX(), top + BookLayout.DOT_Y, radius, color);
    }

    private static void ringAt(GuiGraphics g, int cx, int cy, int radius, int color) {
        int x0 = cx - radius, x1 = cx + radius + 1, y0 = cy - radius, y1 = cy + radius + 1;
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        g.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
        g.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        g.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, color);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, color);
        g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, color);
        g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, color);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        this.diet.renderTooltip(g, font, mouseX, mouseY);
        this.enrichment.renderTooltip(g, font, mouseX, mouseY);
        ConservationTier hovered = dotAt(mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(statusName(hovered));
            if (hovered == this.entry.realStatus()) lines.add(HomePage.key("real_world").copy().withStyle(ChatFormatting.GRAY));
            if (hovered == this.worldTier) lines.add(HomePage.key("this_world").copy().withStyle(ChatFormatting.YELLOW));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private static Component statusName(ConservationTier tier) {
        return Component.translatable("guidebook.diversityofcritters.status." + tier.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Nullable
    private ConservationTier dotAt(double mouseX, double mouseY) {
        double y = mouseY - this.top - BookLayout.DOT_Y;
        if (Math.abs(y) > 5) return null;
        for (ConservationTier tier : ConservationTier.values()) {
            if (Math.abs(mouseX - this.left - tier.dotX()) <= 5) return tier;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.diet.mouseScrolled(delta) || this.enrichment.mouseScrolled(delta)) return true;
        double x = mouseX - this.left, y = mouseY - this.top;
        if (x >= TEXT_X0 && x < TEXT_X1 && y >= FACT_Y0 && y < FACT_Y1) {
            int max = Math.max(0, factLines(Minecraft.getInstance().font).size() - factVisibleLines());
            this.factScroll = Math.max(0, Math.min(max, this.factScroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        this.diet.tick();
        this.enrichment.tick();
    }
}
