package com.evirapo.diversityofcritters.client.guidebook;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * A whole page (art, text, icons and buttons) drawn once into an off-screen texture the size of the
 * window, so a turning leaf can carry everything on it rather than just the page art.
 */
final class PageSnapshot implements AutoCloseable {

    @Nullable private TextureTarget target;

    /** Draws {@code drawing} into this snapshot instead of the screen. */
    void capture(GuiGraphics g, Runnable drawing) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        g.flush();
        if (this.target == null) {
            this.target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            this.target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (this.target.width != w || this.target.height != h) {
            this.target.resize(w, h, Minecraft.ON_OSX);
        }
        // Smooth when the leaf squeezes the page; drawn 1:1 the halves still land on exact texels.
        this.target.setFilterMode(GL11.GL_LINEAR);
        this.target.clear(Minecraft.ON_OSX);
        this.target.bindWrite(true);
        drawing.run();
        g.flush();
        mc.getMainRenderTarget().bindWrite(true);
    }

    int textureId() {
        return this.target == null ? 0 : this.target.getColorTextureId();
    }

    /** Texture u of the GUI x coordinate {@code guiX}. */
    float u(float guiX) {
        return (float) (guiX * Minecraft.getInstance().getWindow().getGuiScale() / this.target.width);
    }

    /** Texture v of the GUI y coordinate {@code guiY}: the texture is stored bottom row first. */
    float v(float guiY) {
        return 1.0F - (float) (guiY * Minecraft.getInstance().getWindow().getGuiScale() / this.target.height);
    }

    @Override
    public void close() {
        if (this.target != null) {
            this.target.destroyBuffers();
            this.target = null;
        }
    }
}
