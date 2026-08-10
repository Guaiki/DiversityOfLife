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

    /** Textura vanilla provisional; se cambiará por la GUI custom del cliente. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/demo_background.png");

    private static final int SCALE_MARGIN_X = 24;
    private static final int SCALE_Y = 45;
    private static final int SCALE_HEIGHT = 14;

    private HumidityTier tier;
    private int size;
    private final Button[] sizeButtons = new Button[HumidifierBlockEntity.ALLOWED_SIZES.length];

    public HumidifierScreen(HumidifierMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 248;
        this.imageHeight = 166;
        this.tier = pMenu.blockEntity.getTier();
        this.size = pMenu.blockEntity.getSize();
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 10000; // sin inventario de jugador

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleTier(-1))
                .bounds(this.leftPos + SCALE_MARGIN_X, this.topPos + SCALE_Y + SCALE_HEIGHT + 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleTier(1))
                .bounds(this.leftPos + this.imageWidth - SCALE_MARGIN_X - 20, this.topPos + SCALE_Y + SCALE_HEIGHT + 6, 20, 20).build());

        int[] sizes = HumidifierBlockEntity.ALLOWED_SIZES;
        int buttonWidth = 36;
        int gap = 8;
        int totalWidth = sizes.length * buttonWidth + (sizes.length - 1) * gap;
        int x0 = this.leftPos + (this.imageWidth - totalWidth) / 2;
        for (int i = 0; i < sizes.length; i++) {
            final int s = sizes[i];
            sizeButtons[i] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(s)), b -> setSize(s))
                    .bounds(x0 + i * (buttonWidth + gap), this.topPos + 128, buttonWidth, 20).build());
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
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        super.renderLabels(guiGraphics, pMouseX, pMouseY);

        // Escala de 7 tiers con marcador sobre el tier actual
        HumidityTier[] tiers = HumidityTier.values();
        int scaleWidth = this.imageWidth - SCALE_MARGIN_X * 2;
        int segmentWidth = scaleWidth / tiers.length;
        int x0 = SCALE_MARGIN_X + (scaleWidth - segmentWidth * tiers.length) / 2;

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
        guiGraphics.drawString(this.font, tierName, (this.imageWidth - nameWidth) / 2, SCALE_Y + SCALE_HEIGHT + 12, this.tier.getColor(), true);

        Component humidityLabel = Component.translatable("gui.diversityofcritters.humidifier.humidity");
        guiGraphics.drawString(this.font, humidityLabel, (this.imageWidth - this.font.width(humidityLabel)) / 2, SCALE_Y - 12, 0xFF404040, false);

        Component sizeLabel = Component.translatable("gui.diversityofcritters.humidifier.size");
        guiGraphics.drawString(this.font, sizeLabel, (this.imageWidth - this.font.width(sizeLabel)) / 2, 116, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
