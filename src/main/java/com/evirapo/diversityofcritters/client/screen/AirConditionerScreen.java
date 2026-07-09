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

    // Nuevas texturas
    private static final ResourceLocation LINE_TEXTURE =
            new ResourceLocation("diversityofcritters", "textures/gui/line.png");
    private static final ResourceLocation SNOWFLAKE_TEXTURE =
            new ResourceLocation("diversityofcritters", "textures/gui/snowflake.png");
    private static final ResourceLocation ICE_TEXTURE =
            new ResourceLocation("diversityofcritters", "textures/gui/ice.png");

    private static final int SCALE_MARGIN_X = 24;
    private static final int SCALE_Y = 54;
    private static final int SCALE_HEIGHT = 8;

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

        // ==========================================
        // TUNING: BOTONES DE SNOW Y ICE (MÁS GRANDES)
        // ==========================================
        int iconButtonSize = 26; // <-- Tamaño del contenedor/botón (Antes era 20)
        int toggleGap = 12;      // <-- Separación entre ambos botones
        int buttonsY = 88;       // <-- Posición Y (altura) de los botones en la GUI

        int togglesX0 = this.leftPos + (this.imageWidth - iconButtonSize * 2 - toggleGap) / 2;

        this.snowButton = addRenderableWidget(createIconButton(togglesX0, this.topPos + buttonsY, iconButtonSize, SNOWFLAKE_TEXTURE, false));
        this.iceButton = addRenderableWidget(createIconButton(togglesX0 + iconButtonSize + toggleGap, this.topPos + buttonsY, iconButtonSize, ICE_TEXTURE, true));

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

    /**
     * Método auxiliar para crear un botón con un icono personalizado y estado oscurecido
     */
    private Button createIconButton(int x, int y, int buttonSize, ResourceLocation icon, boolean isIce) {
        return new Button(x, y, buttonSize, buttonSize, Component.empty(), b -> {
            if (isIce) toggleIce();
            else toggleSnow();
        }, s -> s.get()) {

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                // Dibuja el fondo del botón vanilla (ahora adaptado a 'buttonSize')
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

                boolean activeState = isIce ? iceEnabled : snowEnabled;

                // Centrado automático del icono de 16x16 dentro del contenedor más grande
                int offset = (buttonSize - 16) / 2;

                // Dibuja el icono centrado
                guiGraphics.blit(icon, this.getX() + offset, this.getY() + offset, 0, 0, 16, 16, 16, 16);

                // Si está en OFF, aplica el sombreado sobre TODO EL CONTENEDOR del botón
                if (!activeState) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 10); // Capa Z superior

                    // Rectángulo negro al 50% de opacidad (0x80000000) cubriendo toda el área
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + buttonSize, this.getY() + buttonSize, 0x80000000);

                    guiGraphics.pose().popPose();
                }
            }
        };
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

    private void toggleSnow() {
        this.snowEnabled = !this.snowEnabled;
        sendSettings();
    }

    private void toggleIce() {
        this.iceEnabled = !this.iceEnabled;
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

        // Fondo base de la GUI
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // ==========================================
        // TUNING: RENDERIZADO Y ESCALADO DE LINE.PNG
        // ==========================================
        int lineDisplayWidth = 196;  // <-- Ancho en pantalla
        int lineDisplayHeight = 210; // <-- Alto en pantalla
        int lineYOffset = -116;       // <-- Ajuste vertical

        int lineX = x + (this.imageWidth - lineDisplayWidth) / 2; // Centrado horizontal
        int lineY = y + SCALE_Y + lineYOffset;

        // blit(textura, xPantalla, yPantalla, anchoPantalla, altoPantalla, xTextura, yTextura, anchoLeidoTextura, altoLeidoTextura, anchoTotalTextura, altoTotalTextura)
        guiGraphics.blit(LINE_TEXTURE, lineX, lineY, lineDisplayWidth, lineDisplayHeight, 0, 0, 128, 128, 128, 128);
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

        // SE ELIMINÓ EL TEXTO "TEMPERATURA" DE AQUÍ

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