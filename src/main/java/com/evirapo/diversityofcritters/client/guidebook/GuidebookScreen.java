package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.client.guidebook.page.HomePage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The guidebook: a stack of {@link BookPage}s drawn over the client's two-page textures. Opening a
 * section pushes a page, "Back" pops it, Escape closes the book.
 */
public class GuidebookScreen extends Screen {

    private static final int LEAF_STRIPS = 48;

    private final GuidebookState state;
    private final Deque<BookPage> pages = new ArrayDeque<>();
    private int left, top;
    @Nullable private PageTurn turn;
    /** The widgets of the page showing now, and of the page that showed before the last turn. */
    private List<Renderable> widgets = new ArrayList<>(), fromWidgets = List.of();
    private final PageSnapshot fromShot = new PageSnapshot(), toShot = new PageSnapshot();
    private boolean captured;

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
        this.captured = false;
        this.fromWidgets = List.copyOf(this.widgets);
        rebuildWidgets();
    }

    /** True while a page is being turned: widgets are hidden and clicks are ignored. */
    private boolean turning() {
        if (this.turn != null && this.turn.done(Util.getMillis())) this.turn = null;
        return this.turn != null;
    }

    @Override
    protected void init() {
        this.widgets = new ArrayList<>();
        this.left = (this.width - BookLayout.WIDTH) / 2;
        // A window shorter than the book keeps the top in view; the bottom is what gets cut.
        this.top = Math.max(0, (this.height - BookLayout.HEIGHT) / 2);
        if (this.pages.size() > 1) {
            addPageWidget(new BookButton(this.left + BookLayout.LEFT_PAGE_X0, this.top + BookLayout.PAGE_TOP, 34, 11,
                    Component.translatable("gui.diversityofcritters.guidebook.back"), this::pop).silent());
        }
        page().init(this, this.left, this.top);
    }

    /** Lets pages add widgets without the protected method. */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(T widget) {
        this.widgets.add(widget);
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
     * right to left. Back is the mirror image. Both pages are snapshotted whole on the first frame,
     * so the leaf carries their text, icons and buttons too.
     */
    private void renderTurn(GuiGraphics g, PageTurn turn, float partialTick) {
        BookPage from = turn.from() != null ? turn.from() : page(), to = page();
        if (!this.captured) {
            this.fromShot.capture(g, () -> drawPage(g, from, this.fromWidgets, partialTick));
            this.toShot.capture(g, () -> drawPage(g, to, this.widgets, partialTick));
            this.captured = true;
        }
        long now = Util.getMillis();
        float angle = turn.angle(now);
        PageSnapshot leftHalf = turn.forward() ? this.fromShot : this.toShot;
        PageSnapshot rightHalf = turn.forward() ? this.toShot : this.fromShot;
        PageSnapshot face = PageTurn.showsBack(turn.progress(now)) ? this.toShot : this.fromShot;

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        // The leaf's winding flips as it crosses the spine.
        RenderSystem.disableCull();
        int x0 = this.left, xs = this.left + BookLayout.SPINE_X, x1 = this.left + BookLayout.WIDTH;
        int y0 = this.top, y1 = this.top + BookLayout.HEIGHT;
        flat(m, leftHalf, x0, xs, y0, y1);
        flat(m, rightHalf, xs, x1, y0, y1);
        shadows(m, angle);
        leaf(m, face, angle);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void drawPage(GuiGraphics g, BookPage page, List<Renderable> pageWidgets, float partialTick) {
        g.blit(page.background(), this.left, this.top, 0, 0, BookLayout.WIDTH, BookLayout.HEIGHT, BookLayout.WIDTH, BookLayout.HEIGHT);
        page.render(g, -1, -1, partialTick, this.left, this.top);
        for (Renderable widget : pageWidgets) widget.render(g, -1, -1, partialTick);
    }

    /** One half of a snapshot, lying flat where it was drawn. */
    private static void flat(Matrix4f m, PageSnapshot shot, int x0, int x1, int y0, int y1) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, shot.textureId());
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        b.vertex(m, x0, y0, 0).color(1.0F, 1.0F, 1.0F, 1.0F).uv(shot.u(x0), shot.v(y0)).endVertex();
        b.vertex(m, x0, y1, 0).color(1.0F, 1.0F, 1.0F, 1.0F).uv(shot.u(x0), shot.v(y1)).endVertex();
        b.vertex(m, x1, y1, 0).color(1.0F, 1.0F, 1.0F, 1.0F).uv(shot.u(x1), shot.v(y1)).endVertex();
        b.vertex(m, x1, y0, 0).color(1.0F, 1.0F, 1.0F, 1.0F).uv(shot.u(x1), shot.v(y0)).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /**
     * The leaf as thin vertical strips, each edge placed by the perspective in {@link PageTurn}: the
     * hinge stays on the spine, the outer edge lifts towards the viewer. It dims as it stands up and
     * a little more towards the spine, where it curves out of the gutter.
     */
    private void leaf(Matrix4f m, PageSnapshot face, float angle) {
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        // Which half of the face page the leaf shows: the one on its side of the spine.
        float side = cos >= 0 ? 1.0F : -1.0F;
        float spineX = this.left + BookLayout.SPINE_X, midY = this.top + BookLayout.HEIGHT / 2.0F;
        float halfH = BookLayout.HEIGHT / 2.0F, half = BookLayout.SPINE_X;
        float vTop = face.v(this.top), vBottom = face.v(this.top + BookLayout.HEIGHT);

        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, face.textureId());
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        float px = 0, pTop = 0, pBottom = 0, pu = 0, pShade = 0;
        for (int i = 0; i <= LEAF_STRIPS; i++) {
            float d = half * i / LEAF_STRIPS;
            float scale = PageTurn.heightScale(d, angle);
            float x = spineX + PageTurn.projectedX(d, angle);
            float yTop = midY - halfH * scale, yBottom = midY + halfH * scale;
            float u = face.u(spineX + side * d);
            float shade = (1.0F - 0.35F * sin) * (1.0F - 0.25F * sin * (1.0F - d / half));
            if (i > 0) {
                b.vertex(m, px, pTop, 0).color(pShade, pShade, pShade, 1.0F).uv(pu, vTop).endVertex();
                b.vertex(m, px, pBottom, 0).color(pShade, pShade, pShade, 1.0F).uv(pu, vBottom).endVertex();
                b.vertex(m, x, yBottom, 0).color(shade, shade, shade, 1.0F).uv(u, vBottom).endVertex();
                b.vertex(m, x, yTop, 0).color(shade, shade, shade, 1.0F).uv(u, vTop).endVertex();
            }
            px = x;
            pTop = yTop;
            pBottom = yBottom;
            pu = u;
            pShade = shade;
        }
        BufferUploader.drawWithShader(b.end());
    }

    /**
     * Soft shadows on the flat pages while the leaf is raised: one cast just past its outer edge,
     * and one in the gutter of the page it is swinging towards or away from.
     */
    private void shadows(Matrix4f m, float angle) {
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        if (sin <= 0.01F) return;
        float side = cos >= 0 ? 1.0F : -1.0F;
        float spineX = this.left + BookLayout.SPINE_X;
        float paperX0 = this.left + BookLayout.PAPER_X0, paperX1 = this.left + BookLayout.PAPER_X1;
        float y0 = this.top + BookLayout.PAPER_Y0, y1 = this.top + BookLayout.PAPER_Y1;

        float edge = spineX + PageTurn.projectedX(BookLayout.SPINE_X, angle);
        float castEnd = Math.max(paperX0, Math.min(paperX1, edge + side * 28.0F * sin));
        gradient(m, edge, castEnd, y0, y1, 0.38F * sin, 0.0F);

        float gutterEnd = spineX - side * 45.0F * sin;
        gradient(m, spineX, gutterEnd, y0, y1, 0.3F * sin, 0.0F);
    }

    /** A black band from {@code xa} (alpha {@code aa}) to {@code xb} (alpha {@code ab}). */
    private static void gradient(Matrix4f m, float xa, float xb, float y0, float y1, float aa, float ab) {
        if (Math.abs(xb - xa) < 0.5F) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(m, xa, y0, 0).color(0.0F, 0.0F, 0.0F, aa).endVertex();
        b.vertex(m, xa, y1, 0).color(0.0F, 0.0F, 0.0F, aa).endVertex();
        b.vertex(m, xb, y1, 0).color(0.0F, 0.0F, 0.0F, ab).endVertex();
        b.vertex(m, xb, y0, 0).color(0.0F, 0.0F, 0.0F, ab).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    @Override
    public void removed() {
        this.fromShot.close();
        this.toShot.close();
        super.removed();
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
