/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.mohistmc.plugins;

import com.stackmania.core.StackmaniaConfig;
import com.stackmania.optimization.StackmaniaTickOptimizer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;

/**
 * Periodically adjusts every online player's view distance based on the
 * current server TPS reported by {@link StackmaniaTickOptimizer}.
 *
 * The three-tier ladder, configured by {@link StackmaniaConfig#dynamicViewDistanceMax}
 * and {@link StackmaniaConfig#dynamicViewDistanceMin}:
 *
 * - TPS &gt;= 19.5  -&gt; max view distance (server is healthy)
 * - TPS &gt;= 18.0  -&gt; halfway between min and max
 * - TPS &lt; 18.0   -&gt; min view distance (shed load)
 *
 * Triggered every {@link StackmaniaConfig#dynamicViewDistanceCheckIntervalTicks}
 * ticks (default 600, i.e. every 30 s at 20 TPS).
 *
 * Default: OFF. Operators opt in via the {@code dynamic_view_distance.enabled}
 * key in {@code stackmania-config/stackmania.yml}. While disabled the task
 * does nothing on each tick.
 *
 * Granularity: per-world, not per-player. Paper exposes a per-player setter
 * but that is not part of the Bukkit/Spigot API surface we compile against.
 * In practice every online player in the same world shares the same value.
 *
 * Implementation note: {@code World#setViewDistance(int)} is not in the
 * Bukkit API that the {@code :stackmaniaplugins} module compiles against, so
 * we resolve it reflectively at class-load time. When the running server
 * does expose the method (Spigot 1.20+, Paper, this Stackmania build), the
 * adjuster works. When it does not, every cycle becomes a no-op and a single
 * warning is logged at startup — the module is then effectively disabled
 * regardless of the config flag.
 */
public class StackmaniaDynamicViewDistance extends BukkitRunnable {

    private static final Method SET_VIEW_DISTANCE = resolveSetViewDistance();

    private static Method resolveSetViewDistance() {
        try {
            return World.class.getMethod("setViewDistance", int.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Starts the periodic adjuster on the provided plugin's scheduler.
     */
    public static StackmaniaDynamicViewDistance start(Plugin plugin) {
        StackmaniaDynamicViewDistance task = new StackmaniaDynamicViewDistance();
        long interval = Math.max(20L, (long) StackmaniaConfig.dynamicViewDistanceCheckIntervalTicks);
        task.runTaskTimer(plugin, interval, interval);
        if (SET_VIEW_DISTANCE == null) {
            plugin.getLogger().warning(
                    "[Stackmania/DynamicView] World#setViewDistance(int) not available on this server "
                            + "build — the dynamic view distance feature will be a no-op even if enabled.");
        }
        return task;
    }

    @Override
    public void run() {
        if (!StackmaniaConfig.moduleDynamicViewDistanceEnabled) return;
        if (SET_VIEW_DISTANCE == null) return;

        double tps;
        try {
            tps = StackmaniaTickOptimizer.getInstance().getCurrentTPS();
        } catch (Throwable t) {
            // TickOptimizer is the module that owns the TPS measurement. If
            // it's disabled via stackmania.yml we have nothing reliable to
            // base the decision on — bail rather than guess.
            return;
        }

        int max = clamp(StackmaniaConfig.dynamicViewDistanceMax, 2, 32);
        int min = clamp(StackmaniaConfig.dynamicViewDistanceMin, 2, max);

        final int target;
        if (tps >= 19.5) {
            target = max;
        } else if (tps >= 18.0) {
            target = (max + min) / 2;
        } else {
            target = min;
        }

        for (World world : Bukkit.getWorlds()) {
            try {
                if (world.getViewDistance() != target) {
                    SET_VIEW_DISTANCE.invoke(world, target);
                }
            } catch (Throwable t) {
                Bukkit.getLogger().warning(
                        "[Stackmania/DynamicView] Could not adjust view distance for world "
                                + world.getName() + ": " + t.getMessage());
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
