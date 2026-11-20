package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.client.guidebook.page.HomePage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
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
    @Nullable private PageTurn turn;

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
        BookPage from = page();
        this.pages.push(page);
        changedPage(from, 1);
    }

    public void pop() {
        if (this.pages.size() > 1) {
            changedPage(this.pages.pop(), -1);
        }
    }

    private BookPage page() {
        return this.pages.peek();
    }

    private void changedPage(BookPage from, int direction) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        this.turn = new PageTurn(from, direction, Util.getMillis());
        rebuildWidgets();
    }

    /** True while a page is being turned: widgets are hidden and clicks are ignored. */
    private boolean turning() {
        if (this.turn != null && this.turn.done(Util.getMillis())) this.turn = null;
        return this.turn != null;
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
        if (turning()) {
            renderTurn(g, this.turn, partialTick);
            return;
        }
        g.blit(page().background(), this.left, this.top, 0, 0, BookLayout.WIDTH, BookLayout.HEIGHT, BookLayout.WIDTH, BookLayout.HEIGHT);
        page().render(g, mouseX, mouseY, partialTick, this.left, this.top);
        super.render(g, mouseX, mouseY, partialTick);
        page().renderOverlay(g, mouseX, mouseY, partialTick, this.left, this.top);
    }

    /**
     * Turning forward, the left half still shows the old page and the right half already shows the
     * new one; the leaf (old right half on its front, new left half on its back) swings over from
     * right to left. Back is the mirror image. The leaf carries the page art but not its text.
     */
    private void renderTurn(GuiGraphics g, PageTurn turn, float partialTick) {
        BookPage from = turn.from() != null ? turn.from() : page(), to = page();
        int spine = BookLayout.SPINE_X, half = BookLayout.SPINE_X;
        renderHalf(g, turn.forward() ? from : to, 0, spine, partialTick);
        renderHalf(g, turn.forward() ? to : from, spine, BookLayout.WIDTH, partialTick);

        float progress = turn.progress(Util.getMillis());
        int width = PageTurn.leafWidth(progress, half);
        if (width <= 0) return;
        boolean back = PageTurn.showsBack(progress);
        // Forward: the front lies right of the spine, the back left of it. Back: the other way round.
        boolean rightOfSpine = turn.forward() != back;
        BookPage face = back ? to : from;
        int x = rightOfSpine ? spine : spine - width;
        int u = rightOfSpine ? spine : 0;
        g.pose().pushPose();
        // Above the item icons of the page underneath, which render at z 150 and up.
        g.pose().translate(0, 0, 400);
        g.blit(face.background(), this.left + x, this.top, width, BookLayout.HEIGHT, u, 0, half, BookLayout.HEIGHT,
                BookLayout.WIDTH, BookLayout.HEIGHT);
        // Darker the more edge-on the leaf is.
        int alpha = Math.round(120 * (1.0F - width / (float) half));
        g.fill(this.left + x, this.top, this.left + x + width, this.top + BookLayout.HEIGHT, alpha << 24 | 0x2A2F1E);
        g.pose().popPose();
    }

    /** One half of the book showing {@code page}: its art and text, clipped to x0..x1. */
    private void renderHalf(GuiGraphics g, BookPage page, int x0, int x1, float partialTick) {
        g.enableScissor(this.left + x0, this.top, this.left + x1, this.top + BookLayout.HEIGHT);
        g.blit(page.background(), this.left, this.top, 0, 0, BookLayout.WIDTH, BookLayout.HEIGHT, BookLayout.WIDTH, BookLayout.HEIGHT);
        page.render(g, -1, -1, partialTick, this.left, this.top);
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (turning()) return true;
        if (page().mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (turning()) return true;
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
