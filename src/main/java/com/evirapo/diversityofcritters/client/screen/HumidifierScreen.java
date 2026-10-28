package com.evirapo.diversityofcritters.client.screen;

import com.evirapo.diversityofcritters.common.block.HumidifierMenu;
import com.evirapo.diversityofcritters.common.block.entity.HumidifierBlockEntity;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import com.evirapo.diversityofcritters.network.DOCNetworkHandler;
import com.evirapo.diversityofcritters.network.SetHumidifierMsg;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class HumidifierScreen extends AbstractContainerScreen<HumidifierMenu> {

    /** Arte del panel: región 120x75 del PNG de 128x128 (desde 4,30), dibujada al doble. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("diversityofcritters", "textures/gui/humidifier.png");
    private static final int ART_U = 4;
    private static final int ART_V = 30;
    private static final int ART_W = 120;
    private static final int ART_H = 75;

    /** Ventana clara del panel donde van los controles (coordenadas de la GUI). */
    private static final int INNER_X = 108;
    private static final int INNER_W = 114;
    private static final int INNER_CENTER = INNER_X + INNER_W / 2;
    private static final int ARROW_SIZE = 14;
    private static final int ARROW_Y = 34;
    /** La escala de tiers va en la misma fila que las flechas, entre ambas. */
    private static final int SCALE_X = INNER_X + ARROW_SIZE + 4;
    private static final int SCALE_W = INNER_W - 2 * (ARROW_SIZE + 4);
    private static final int SCALE_HEIGHT = 12;
    private static final int SCALE_Y = ARROW_Y + (ARROW_SIZE - SCALE_HEIGHT) / 2;
    private static final int HUMIDITY_LABEL_Y = 22;
    private static final int NAME_Y = 55;
    private static final int SIZE_LABEL_Y = 78;
    private static final int SIZE_BUTTONS_Y = 90;

    private HumidityTier tier;
    private int size;
    private final Button[] sizeButtons = new Button[HumidifierBlockEntity.ALLOWED_SIZES.length];

    public HumidifierScreen(HumidifierMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = ART_W * 2;
        this.imageHeight = ART_H * 2;
        this.tier = pMenu.blockEntity.getTier();
        this.size = pMenu.blockEntity.getSize();
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.inventoryLabelY = 10000; // sin inventario de jugador

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleTier(-1))
                .bounds(this.leftPos + INNER_X, this.topPos + ARROW_Y, ARROW_SIZE, ARROW_SIZE).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleTier(1))
                .bounds(this.leftPos + INNER_X + INNER_W - ARROW_SIZE, this.topPos + ARROW_Y, ARROW_SIZE, ARROW_SIZE).build());

        int[] sizes = HumidifierBlockEntity.ALLOWED_SIZES;
        int buttonWidth = 26;
        int gap = 4;
        int totalWidth = sizes.length * buttonWidth + (sizes.length - 1) * gap;
        int x0 = this.leftPos + INNER_CENTER - totalWidth / 2;
        for (int i = 0; i < sizes.length; i++) {
            final int s = sizes[i];
            sizeButtons[i] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(s)), b -> setSize(s))
                    .bounds(x0 + i * (buttonWidth + gap), this.topPos + SIZE_BUTTONS_Y, buttonWidth, 16).build());
        }
        updateSizeButtons();
    }

    private void cycleTier(int direction) {
        int newOrdinal = Math.max(0, Math.min(HumidityTier.values().length - 1, this.tier.ordinal() + direction));
        if (newOrdinal != this.tier.ordinal()) {
            this.tier = HumidityTier.byId(newOrdinal);
            sendSettings();
        }
    }

    private void setSize(int newSize) {
        this.size = newSize;
        updateSizeButtons();
        sendSettings();
    }

    private void updateSizeButtons() {
        int[] sizes = HumidifierBlockEntity.ALLOWED_SIZES;
        for (int i = 0; i < sizeButtons.length; i++) {
            sizeButtons[i].active = sizes[i] != this.size;
        }
    }

    private void sendSettings() {
        DOCNetworkHandler.CHANNEL.sendToServer(
                new SetHumidifierMsg(this.menu.blockEntity.getBlockPos(), this.tier.ordinal(), this.size));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, imageWidth, imageHeight, ART_U, ART_V, ART_W, ART_H, 128, 128);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        super.renderLabels(guiGraphics, pMouseX, pMouseY);

        // Escala de 7 tiers con marcador sobre el tier actual
        HumidityTier[] tiers = HumidityTier.values();
        int segmentWidth = SCALE_W / tiers.length;
        int x0 = SCALE_X + (SCALE_W - segmentWidth * tiers.length) / 2;

        for (int i = 0; i < tiers.length; i++) {
            int sx = x0 + i * segmentWidth;
            guiGraphics.fill(sx, SCALE_Y, sx + segmentWidth - 1, SCALE_Y + SCALE_HEIGHT, tiers[i].getColor());
            if (tiers[i] == this.tier) {
                // borde blanco del segmento seleccionado
                guiGraphics.fill(sx, SCALE_Y, sx + segmentWidth - 1, SCALE_Y + 1, 0xFFFFFFFF);
                guiGraphics.fill(sx, SCALE_Y + SCALE_HEIGHT - 1, sx + segmentWidth - 1, SCALE_Y + SCALE_HEIGHT, 0xFFFFFFFF);
                guiGraphics.fill(sx, SCALE_Y, sx + 1, SCALE_Y + SCALE_HEIGHT, 0xFFFFFFFF);
                guiGraphics.fill(sx + segmentWidth - 2, SCALE_Y, sx + segmentWidth - 1, SCALE_Y + SCALE_HEIGHT, 0xFFFFFFFF);
            }
        }

        String tierName = this.tier.getDisplayName();
        int nameWidth = this.font.width(tierName);
        guiGraphics.drawString(this.font, tierName, INNER_CENTER - nameWidth / 2, NAME_Y, this.tier.getColor(), true);

        Component humidityLabel = Component.translatable("gui.diversityofcritters.humidifier.humidity");
        guiGraphics.drawString(this.font, humidityLabel, INNER_CENTER - this.font.width(humidityLabel) / 2, HUMIDITY_LABEL_Y, 0xFF404040, false);

        Component sizeLabel = Component.translatable("gui.diversityofcritters.humidifier.size");
        guiGraphics.drawString(this.font, sizeLabel, INNER_CENTER - this.font.width(sizeLabel) / 2, SIZE_LABEL_Y, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
