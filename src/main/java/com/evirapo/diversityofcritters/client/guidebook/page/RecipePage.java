package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import com.evirapo.diversityofcritters.client.guidebook.BookText;
import com.evirapo.diversityofcritters.client.guidebook.GuidebookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Care and Etc: a list of the mod's items on the left and the selected one's crafting recipe on the
 * right, read from the game's own recipes so it always matches the crafting table.
 */
public class RecipePage extends BookPage {

    private static final int LIST_X0 = 22, LIST_X1 = 138, LIST_Y0 = 50, ROW_H = 14;
    private static final int SLOT = 18, GRID_X = 166, GRID_Y = 44, RESULT_X = 246, RESULT_Y = GRID_Y + SLOT;
    private static final int DESC_Y = 104;
    private static final float ICON_SCALE = 11 / 16.0F;

    private final Component title, subtitle;
    private final List<Item> items = new ArrayList<>();
    private int selected;
    private int left, top;
    @Nullable private CraftingRecipe recipe;
    private final ItemStack[] grid = new ItemStack[9];
    private final List<Ingredient> ingredients = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;

    public RecipePage(Component title, Component subtitle, List<ResourceLocation> ids) {
        this.title = title;
        this.subtitle = subtitle;
        for (ResourceLocation id : ids) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) this.items.add(item);
        }
        select(0);
    }

    private void select(int index) {
        this.selected = index;
        this.recipe = null;
        this.ingredients.clear();
        if (this.items.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Item item = this.items.get(index);
        for (CraftingRecipe candidate : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (candidate.getResultItem(level.registryAccess()).is(item)) {
                this.recipe = candidate;
                break;
            }
        }
        if (this.recipe == null) return;
        NonNullList<Ingredient> list = this.recipe.getIngredients();
        for (int i = 0; i < 9; i++) this.ingredients.add(Ingredient.EMPTY);
        if (this.recipe instanceof ShapedRecipe shaped) {
            for (int i = 0; i < list.size(); i++) {
                this.ingredients.set((i / shaped.getWidth()) * 3 + i % shaped.getWidth(), list.get(i));
            }
        } else {
            for (int i = 0; i < Math.min(9, list.size()); i++) this.ingredients.set(i, list.get(i));
        }
    }

    @Override
    public ResourceLocation background() {
        return BookLayout.PLAIN;
    }

    @Override
    public void init(GuidebookScreen screen, int left, int top) {
        this.left = left;
        this.top = top;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        Font font = Minecraft.getInstance().font;
        this.hovered = ItemStack.EMPTY;
        int lx = left + BookLayout.LEFT_CENTER, rx = left + BookLayout.RIGHT_CENTER;
        int pageW = BookLayout.LEFT_PAGE_X1 - BookLayout.LEFT_PAGE_X0;
        BookText.centered(g, font, this.title, lx, top + 24, BookLayout.COLOR_TEXT, 1.0F, pageW);
        BookText.wrappedCentered(g, font, this.subtitle, lx, top + 36, pageW - 8, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL);

        for (int i = 0; i < this.items.size(); i++) {
            int y0 = top + LIST_Y0 + i * ROW_H;
            boolean selected = i == this.selected;
            boolean over = mouseX >= left + LIST_X0 && mouseX < left + LIST_X1 && mouseY >= y0 && mouseY < y0 + ROW_H;
            if (selected || over) {
                g.fill(left + LIST_X0, y0, left + LIST_X1, y0 + ROW_H, selected ? BookLayout.COLOR_TEXT_SOFT : BookLayout.COLOR_HOVER);
            }
            ItemStack stack = new ItemStack(this.items.get(i));
            g.pose().pushPose();
            g.pose().translate(left + LIST_X0 + 2, y0 + 1.5F, 0);
            g.pose().scale(ICON_SCALE, ICON_SCALE, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(left + LIST_X0 + 16, y0 + 4, 0);
            g.pose().scale(BookText.SMALL, BookText.SMALL, 1);
            g.drawString(font, stack.getHoverName(), 0, 0, selected ? BookLayout.COLOR_PAPER : BookLayout.COLOR_TEXT, false);
            g.pose().popPose();
        }

        if (this.items.isEmpty()) return;
        ItemStack result = new ItemStack(this.items.get(this.selected));
        int rightW = BookLayout.RIGHT_PAGE_X1 - BookLayout.RIGHT_PAGE_X0;
        BookText.centered(g, font, result.getHoverName(), rx, top + 18, BookLayout.COLOR_TEXT, 1.0F, rightW);
        Component kind = this.recipe == null ? HomePage.key("no_recipe")
                : this.recipe instanceof ShapedRecipe ? HomePage.key("crafting_table") : HomePage.key("shapeless");
        BookText.centered(g, font, kind, rx, top + 30, BookLayout.COLOR_TEXT_SOFT, BookText.SMALL, rightW);

        long second = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < 9; i++) {
            int sx = left + GRID_X + (i % 3) * SLOT, sy = top + GRID_Y + (i / 3) * SLOT;
            slot(g, sx, sy, SLOT);
            ItemStack[] options = i < this.ingredients.size() ? this.ingredients.get(i).getItems() : new ItemStack[0];
            ItemStack shown = options.length == 0 ? ItemStack.EMPTY : options[(int) (second % options.length)];
            drawItem(g, font, shown, sx + 1, sy + 1, mouseX, mouseY, false);
        }
        BookText.centered(g, font, Component.literal("->"), left + RESULT_X - 9, top + RESULT_Y + 5, BookLayout.COLOR_TEXT, 1.0F, 16);
        slot(g, left + RESULT_X - 2, top + RESULT_Y - 2, SLOT + 4);
        ItemStack output = this.recipe != null && Minecraft.getInstance().level != null
                ? this.recipe.getResultItem(Minecraft.getInstance().level.registryAccess()) : result;
        drawItem(g, font, output, left + RESULT_X + 1, top + RESULT_Y + 1, mouseX, mouseY, true);

        BookText.wrappedCentered(g, font, Component.translatable("guidebook.diversityofcritters.desc." + BuiltInRegistries.ITEM.getKey(this.items.get(this.selected)).getPath()),
                rx, top + DESC_Y, rightW - 10, BookLayout.COLOR_TEXT, BookText.SMALL);
    }

    private void drawItem(GuiGraphics g, Font font, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean count) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        if (count) g.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) this.hovered = stack;
    }

    /** A vanilla-style inventory slot: grey with a dark top-left and a light bottom-right edge. */
    private static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF8B8B8B);
        g.fill(x, y, x + size - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + size - 1, 0xFF373737);
        g.fill(x + 1, y + size - 1, x + size, y + size, 0xFFFFFFFF);
        g.fill(x + size - 1, y + 1, x + size, y + size, 0xFFFFFFFF);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, int left, int top) {
        if (!this.hovered.isEmpty()) g.renderTooltip(Minecraft.getInstance().font, this.hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - this.left, y = mouseY - this.top;
        if (x < LIST_X0 || x >= LIST_X1 || y < LIST_Y0) return false;
        int index = (int) ((y - LIST_Y0) / ROW_H);
        if (index >= this.items.size()) return false;
        if (index != this.selected) {
            select(index);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }
}
