package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Numbers render frames.
 *
 * <p>A model can be set up several times per frame (extra render passes, shadows), and every pass
 * starts from a reset pose, so the rig has to solve on each one. What it must not do is advance
 * its springs on each one. Comparing the frame number tells the first pass from the repeats.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LocomotionClock {

    private static long frame;
    private static long frameNanos = System.nanoTime();

    private LocomotionClock() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
            frameNanos = System.nanoTime();
        }
    }

    public static long frame() {
        return frame;
    }

    public static long frameNanos() {
        return frameNanos;
    }
}
