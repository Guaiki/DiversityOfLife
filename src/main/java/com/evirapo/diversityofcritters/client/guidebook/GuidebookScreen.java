package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.client.guidebook.page.HomePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The guidebook: a stack of {@link BookPage}s drawn over the client's two-page textures. Opening a
 * section pushes a page, "Back" pops it, Escape closes the book.
 */
public class GuidebookScreen extends Screen {

    private final GuidebookState state;
    private final Deque<BookPage> pages = new ArrayDeque<>();
    private int left, top;

    private GuidebookScreen(GuidebookState state) {
        super(Component.translatable("item.diversityofcritters.guidebook"));
        this.state = state;
        this.pages.push(new HomePage());
    }

    public static void open(GuidebookState state) {
        Minecraft.getInstance().setScreen(new GuidebookScreen(state));
    }

    public GuidebookState state() {
        return this.state;
    }

    public void push(BookPage page) {
        this.pages.push(page);
        changedPage();
    }

    public void pop() {
        if (this.pages.size() > 1) {
            this.pages.pop();
            changedPage();
        }
    }

    private BookPage page() {
        return this.pages.peek();
    }

    private void changedPage() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        this.left = (this.width - BookLayout.WIDTH) / 2;
        // A window shorter than the book keeps the top in view; the bottom is what gets cut.
        this.top = Math.max(0, (this.height - BookLayout.HEIGHT) / 2);
        if (this.pages.size() > 1) {
            addRenderableWidget(new BookButton(this.left + BookLayout.LEFT_PAGE_X0, this.top + BookLayout.PAGE_TOP, 34, 11,
                    Component.translatable("gui.diversityofcritters.guidebook.back"), this::pop).silent());
        }
        page().init(this, this.left, this.top);
    }

    /** Lets pages add widgets without the protected method. */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(T widget) {
        return addRenderableWidget(widget);
    }

    public int bookLeft() {
        return this.left;
    }

    public int bookTop() {
        return this.top;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(page().background(), this.left, this.top, 0, 0, BookLayout.WIDTH, BookLayout.HEIGHT, BookLayout.WIDTH, BookLayout.HEIGHT);
        page().render(g, mouseX, mouseY, partialTick, this.left, this.top);
        super.render(g, mouseX, mouseY, partialTick);
        page().renderOverlay(g, mouseX, mouseY, partialTick, this.left, this.top);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page().mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page().mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        page().tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
