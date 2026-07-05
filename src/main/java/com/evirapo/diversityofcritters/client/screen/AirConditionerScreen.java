package com.evirapo.diversityofcritters.client.screen;

import com.evirapo.diversityofcritters.common.block.AirConditionerMenu;
import com.evirapo.diversityofcritters.common.block.entity.AirConditionerBlockEntity;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import com.evirapo.diversityofcritters.network.DOCNetworkHandler;
import com.evirapo.diversityofcritters.network.SetAirConditionerMsg;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AirConditionerScreen extends AbstractContainerScreen<AirConditionerMenu> {

    /** Textura vanilla provisional; se cambiará por la GUI custom del cliente. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/demo_background.png");

    private static final int SCALE_MARGIN_X = 24;
    private static final int SCALE_Y = 45;
    private static final int SCALE_HEIGHT = 14;

    private TemperatureTier tier;
    private int size;
    private boolean snowEnabled;
    private boolean iceEnabled;
    private final Button[] sizeButtons = new Button[AirConditionerBlockEntity.ALLOWED_SIZES.length];
    private Button snowButton;
    private Button iceButton;

    public AirConditionerScreen(AirConditionerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 248;
        this.imageHeight = 166;
        this.tier = pMenu.blockEntity.getTier();
        this.size = pMenu.blockEntity.getSize();
        this.snowEnabled = pMenu.blockEntity.isSnowEnabled();
        this.iceEnabled = pMenu.blockEntity.isIceEnabled();
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

        int toggleWidth = 76;
        int toggleGap = 8;
        int togglesX0 = this.leftPos + (this.imageWidth - toggleWidth * 2 - toggleGap) / 2;
        this.snowButton = addRenderableWidget(Button.builder(snowLabel(), b -> toggleSnow())
                .bounds(togglesX0, this.topPos + 92, toggleWidth, 20).build());
        this.iceButton = addRenderableWidget(Button.builder(iceLabel(), b -> toggleIce())
                .bounds(togglesX0 + toggleWidth + toggleGap, this.topPos + 92, toggleWidth, 20).build());

        int[] sizes = AirConditionerBlockEntity.ALLOWED_SIZES;
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
        int newOrdinal = Math.max(0, Math.min(TemperatureTier.values().length - 1, this.tier.ordinal() + direction));
        if (newOrdinal != this.tier.ordinal()) {
            this.tier = TemperatureTier.byId(newOrdinal);
            sendSettings();
        }
    }

    private void setSize(int newSize) {
        this.size = newSize;
        updateSizeButtons();
        sendSettings();
    }

    private Component snowLabel() {
        return Component.literal("Snow: " + (this.snowEnabled ? "ON" : "OFF"));
    }

    private Component iceLabel() {
        return Component.literal("Ice: " + (this.iceEnabled ? "ON" : "OFF"));
    }

    private void toggleSnow() {
        this.snowEnabled = !this.snowEnabled;
        this.snowButton.setMessage(snowLabel());
        sendSettings();
    }

    private void toggleIce() {
        this.iceEnabled = !this.iceEnabled;
        this.iceButton.setMessage(iceLabel());
        sendSettings();
    }

    private void updateSizeButtons() {
        int[] sizes = AirConditionerBlockEntity.ALLOWED_SIZES;
        for (int i = 0; i < sizeButtons.length; i++) {
            sizeButtons[i].active = sizes[i] != this.size;
        }
    }

    private void sendSettings() {
        DOCNetworkHandler.CHANNEL.sendToServer(
                new SetAirConditionerMsg(this.menu.blockEntity.getBlockPos(), this.tier.ordinal(), this.size,
                        this.snowEnabled, this.iceEnabled));
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
        TemperatureTier[] tiers = TemperatureTier.values();
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

        Component tempLabel = Component.translatable("gui.diversityofcritters.air_conditioner.temperature");
        guiGraphics.drawString(this.font, tempLabel, (this.imageWidth - this.font.width(tempLabel)) / 2, SCALE_Y - 12, 0xFF404040, false);

        Component sizeLabel = Component.translatable("gui.diversityofcritters.air_conditioner.size");
        guiGraphics.drawString(this.font, sizeLabel, (this.imageWidth - this.font.width(sizeLabel)) / 2, 116, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
