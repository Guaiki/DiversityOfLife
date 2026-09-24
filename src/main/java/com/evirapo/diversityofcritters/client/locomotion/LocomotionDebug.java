package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.locomotion.LedgeSample;
import com.evirapo.diversityofcritters.common.locomotion.LedgeSampler;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Debug tools for the locomotion rigs.
 *
 * <ul>
 *   <li><b>Overlay</b> (F6): in the world, per foot, the animated sole (yellow), the ground read
 *       under it (green when the foot carries weight, red when it does not) and the corrected sole
 *       (cyan). On screen, the numbers for the critter under the crosshair or the nearest one.</li>
 *   <li><b>Dump</b> (F7): writes the same numbers for every rigged critter nearby to the log.</li>
 *   <li><b>Bypass</b> (F9): fades every rig out, to compare against the pose as animated.</li>
 * </ul>
 *
 * <p>The keys are bound by default only in a development environment; in a release build they
 * exist in the controls menu, unbound.
 */
@OnlyIn(Dist.CLIENT)
public final class LocomotionDebug {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CATEGORY = "key.categories." + DiversityOfCritters.MODID + ".locomotion";
    private static final double RANGE = 32.0;
    private static final double MARK = 0.015;

    public static final KeyMapping OVERLAY = key("overlay", GLFW.GLFW_KEY_F6);
    public static final KeyMapping DUMP = key("dump", GLFW.GLFW_KEY_F7);
    public static final KeyMapping BYPASS = key("bypass", GLFW.GLFW_KEY_F9);

    private static final LedgeSample LEDGE = new LedgeSample();

    private LocomotionDebug() {
    }

    private static KeyMapping key(String name, int devKey) {
        final int key = FMLEnvironment.production ? InputConstants.UNKNOWN.getValue() : devKey;
        return new KeyMapping("key." + DiversityOfCritters.MODID + ".locomotion." + name,
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    @Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OVERLAY);
            event.register(DUMP);
            event.register(BYPASS);
        }
    }

    @Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Events {
        private static boolean dumpNextFrame;

        private Events() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (OVERLAY.consumeClick()) {
                LocomotionDebugFlags.overlay = !LocomotionDebugFlags.overlay;
                say("Locomotion overlay " + (LocomotionDebugFlags.overlay ? "on" : "off"));
            }
            while (BYPASS.consumeClick()) {
                LocomotionDebugFlags.bypass = !LocomotionDebugFlags.bypass;
                say("Locomotion bypass " + (LocomotionDebugFlags.bypass ? "on: poses as animated" : "off"));
            }
            if (dumpNextFrame) {
                // The capture requested last tick has had a frame to fill in.
                dumpNextFrame = false;
                LocomotionDebugFlags.captureOnce = false;
                dump();
            }
            while (DUMP.consumeClick()) {
                LocomotionDebugFlags.captureOnce = true;
                dumpNextFrame = true;
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (!LocomotionDebugFlags.overlay || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            final Vec3 camera = event.getCamera().getPosition();
            final PoseStack poseStack = event.getPoseStack();
            final MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            LocomotionStateCache.forEach((entity, state) -> {
                if (entity.isRemoved() || entity.distanceToSqr(camera) > RANGE * RANGE) {
                    return;
                }
                for (LocomotionState.LegState leg : state.legs()) {
                    mark(poseStack, lines, leg.soleX, leg.soleY, leg.soleZ, MARK, 1.0f, 1.0f, 0.2f);
                    final float carried = leg.support * Math.max(leg.plantWeight, leg.contactWeight);
                    LevelRenderer.renderLineBox(poseStack, lines,
                            new AABB(leg.soleX - 2 * MARK, leg.groundY - 0.002, leg.soleZ - 2 * MARK,
                                    leg.soleX + 2 * MARK, leg.groundY + 0.002, leg.soleZ + 2 * MARK),
                            1.0f - carried, carried, 0.2f, 1.0f);
                    mark(poseStack, lines, leg.debugCorrectedSoleX, leg.debugCorrectedSoleY, leg.debugCorrectedSoleZ,
                            MARK * 0.7, 0.2f, 1.0f, 1.0f);
                }
                drawLedge(poseStack, lines, entity);
            });
            poseStack.popPose();
            buffers.endBatch(RenderType.lines());
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (!LocomotionDebugFlags.overlay) {
                return;
            }
            final Entity target = target();
            final Minecraft minecraft = Minecraft.getInstance();
            final Font font = minecraft.font;
            final GuiGraphics graphics = event.getGuiGraphics();
            int y = 4;
            if (target == null) {
                graphics.drawString(font, "[Locomotion] no rigged critter in range", 4, y, 0xFFFFFF);
                return;
            }
            final LocomotionState state = LocomotionStateCache.peek(target);
            for (String line : describe(target, state)) {
                graphics.drawString(font, line, 4, y, 0xFFFFFF);
                y += font.lineHeight + 1;
            }
        }
    }

    private static void mark(PoseStack poseStack, VertexConsumer lines, double x, double y, double z, double size,
                             float r, float g, float b) {
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(x - size, y - size, z - size, x + size, y + size, z + size), r, g, b, 1.0f);
    }

    /**
     * Probes for a ledge ahead of the critter. Uses the civet's profile until each rig carries its
     * own. Render thread only: the result is shared.
     */
    private static LedgeSample ledge(Entity entity) {
        final float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        LedgeSampler.sample(entity.level(), entity.position(), yaw, entity.getBbWidth() * 0.5f,
                entity.getBbHeight(), MantleProfile.CIVET, LEDGE);
        return LEDGE;
    }

    /** The wall's face across the body's width, and its lip: green if it can be mantled, orange if not. */
    private static void drawLedge(PoseStack poseStack, VertexConsumer lines, Entity entity) {
        final LedgeSample s = ledge(entity);
        if (!s.hasWall()) {
            return;
        }
        final float r = s.found ? 0.2f : 1.0f;
        final float g = s.found ? 1.0f : 0.5f;
        final double half = entity.getBbWidth() * 0.5;
        final double feetY = s.lipY - s.lipHeight;
        final int stepX = s.facing.getStepX();
        final int stepZ = s.facing.getStepZ();
        final double thin = 0.003;
        final double onTop = 0.1;
        if (stepX != 0) {
            final double z = entity.getZ();
            LevelRenderer.renderLineBox(poseStack, lines,
                    new AABB(s.wallCoord - thin, feetY, z - half, s.wallCoord + thin, s.lipY, z + half), r, g, 0.1f, 1.0f);
            LevelRenderer.renderLineBox(poseStack, lines,
                    new AABB(s.wallCoord, s.lipY - thin, z - half, s.wallCoord + stepX * onTop, s.lipY + thin, z + half),
                    1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            final double x = entity.getX();
            LevelRenderer.renderLineBox(poseStack, lines,
                    new AABB(x - half, feetY, s.wallCoord - thin, x + half, s.lipY, s.wallCoord + thin), r, g, 0.1f, 1.0f);
            LevelRenderer.renderLineBox(poseStack, lines,
                    new AABB(x - half, s.lipY - thin, s.wallCoord, x + half, s.lipY + thin, s.wallCoord + stepZ * onTop),
                    1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static String describeLedge(Entity entity) {
        final LedgeSample s = ledge(entity);
        if (!s.hasWall()) {
            return "ledge: " + s.reason;
        }
        return String.format(Locale.ROOT, "ledge: %s  %s  lip %+.2f  top %.2f  headroom %.2f  conf %.2f%s",
                s.found ? "MANTLE" : s.reason, s.facing.getName(), s.lipHeight, s.topY, s.headroom, s.confidence,
                s.continuesUp ? "  climbs on" : "");
    }

    /** The critter under the crosshair, or else the nearest rigged one. */
    private static @Nullable Entity target() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Entity looked = minecraft.crosshairPickEntity;
        if (looked != null && LocomotionStateCache.peek(looked) != null) {
            return looked;
        }
        if (minecraft.player == null) {
            return null;
        }
        final Vec3 eye = minecraft.player.position();
        final Entity[] best = new Entity[1];
        final double[] bestDistance = {RANGE * RANGE};
        LocomotionStateCache.forEach((entity, state) -> {
            final double distance = entity.isRemoved() ? Double.MAX_VALUE : entity.distanceToSqr(eye);
            if (distance < bestDistance[0]) {
                bestDistance[0] = distance;
                best[0] = entity;
            }
        });
        return best[0];
    }

    private static String[] describe(Entity entity, LocomotionState state) {
        final LocomotionState.LegState[] legs = state.legs();
        final LocomotionState.SpineState[] spine = state.spine();
        final String[] lines = new String[4 + legs.length + (spine.length > 0 ? 1 : 0)];
        lines[0] = String.format(Locale.ROOT, "[Locomotion] %s #%d  %s  gate %.2f  authority %.2f%s",
                entity.getType().getDescriptionId().replace("entity." + DiversityOfCritters.MODID + ".", ""),
                entity.getId(), state.rigName, state.gate.value(), state.debugAuthority,
                LocomotionDebugFlags.bypass ? "  BYPASS" : "");
        lines[1] = String.format(Locale.ROOT, "pitch %+.1f°  roll %+.1f°  drop %+.2fu  air %.2fs  %.1f u/block",
                Math.toDegrees(state.pelvisPitch.value()), Math.toDegrees(state.pelvisRoll.value()),
                state.pelvisDrop.value(), state.airborneSeconds, state.debugUnitsPerBlock);
        lines[2] = "leg      plant contact support   lift  resid  stretch  ground  sole->fixed";
        for (int i = 0; i < legs.length; i++) {
            final LocomotionState.LegState leg = legs[i];
            lines[3 + i] = String.format(Locale.ROOT, "%-7s  %4.2f  %4.2f  %4.2f  %+6.2f %+6.2f  %+5.2f  %7.3f  %7.3f->%7.3f",
                    leg.name, leg.plantWeight, leg.contactWeight, leg.support, leg.debugLift, leg.debugResidual,
                    leg.debugStretch, leg.groundY, leg.soleY, leg.debugCorrectedSoleY);
        }
        if (spine.length > 0) {
            final StringBuilder trails = new StringBuilder("spine trail yaw:");
            for (LocomotionState.SpineState s : spine) {
                trails.append(String.format(Locale.ROOT, " %+.1f°", Math.toDegrees(s.debugTrailYaw)));
            }
            lines[lines.length - 2] = trails.toString();
        }
        lines[lines.length - 1] = describeLedge(entity);
        return lines;
    }

    private static void dump() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        final Vec3 at = minecraft.player.position();
        final int[] count = {0};
        LocomotionStateCache.forEach((entity, state) -> {
            if (entity.isRemoved() || entity.distanceToSqr(at) > RANGE * RANGE) {
                return;
            }
            count[0]++;
            for (String line : describe(entity, state)) {
                LOGGER.info(line);
            }
        });
        say("Locomotion dump: " + count[0] + " critter(s) written to the log");
    }

    private static void say(String message) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }
}
