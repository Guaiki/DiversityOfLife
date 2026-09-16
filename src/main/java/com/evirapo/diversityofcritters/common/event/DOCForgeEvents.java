package com.evirapo.diversityofcritters.common.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.command.CivetDebugCommand;
import com.evirapo.diversityofcritters.common.command.SetNeedsCommand;
import com.evirapo.diversityofcritters.common.command.SetPregnancyCommand;
import com.evirapo.diversityofcritters.common.command.SpawnDebugCommand;
import com.evirapo.diversityofcritters.common.spawn.ReleaseRegistry;
import com.evirapo.diversityofcritters.common.spawn.SpawnRuleEngine;
import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DOCForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Server thread watchdog: samples the server thread stack every 200ms.
    // If the server thread is stuck in the same place for > 1s, dumps its stack.
    private static ScheduledExecutorService watchdogExecutor;
    private static ScheduledFuture<?> watchdogTask;
    private static Thread serverThread;
    private static StackTraceElement[] lastStack;
    private static int sameStackCount = 0;

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        SetPregnancyCommand.register(event.getDispatcher());
        SetNeedsCommand.register(event.getDispatcher());
        CivetDebugCommand.register(event.getDispatcher());
        SpawnDebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Load the release registry at startup so its counter cache is populated before the first
        // spawn cycle.
        ReleaseRegistry.get(event.getServer());

        // Capture the server thread reference
        serverThread = Thread.currentThread();

        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CivetWatchdog");
            t.setDaemon(true);
            return t;
        });

        watchdogTask = watchdogExecutor.scheduleAtFixedRate(() -> {
            try {
                if (serverThread == null) return;

                StackTraceElement[] stack = serverThread.getStackTrace();
                if (stack.length == 0) return;

                // Compare top 3 frames to detect if stuck
                boolean stuck = false;
                if (lastStack != null && lastStack.length >= 3 && stack.length >= 3) {
                    stuck = stack[0].equals(lastStack[0])
                            && stack[1].equals(lastStack[1])
                            && stack[2].equals(lastStack[2]);
                }

                if (stuck) {
                    sameStackCount++;
                } else {
                    sameStackCount = 0;
                }

                lastStack = stack;
            } catch (Exception e) {
                // Never crash the watchdog
            }
        }, 500, 200, TimeUnit.MILLISECONDS);

    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Stops one world's counters from leaking into the next within the same session.
        ReleaseRegistry.resetCache();
        SpawnRuleEngine.clearChanceOverrides();

        if (watchdogTask != null) watchdogTask.cancel(false);
        if (watchdogExecutor != null) watchdogExecutor.shutdownNow();
    }
}
