/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.mohistmc.plugins;

import com.stackmania.core.StackmaniaConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Per-player hostile mob cap distribution.
 *
 * Vanilla and stock Mohist use a global hostile mob cap per dimension shared
 * across every loaded chunk. With many players spread across the map, mobs
 * cluster around whoever loaded a spawn point first and the rest of the
 * server gets nothing to fight. Paper solves this with
 * {@code per-player-mob-spawns}; this listener does the equivalent for
 * Forge+Bukkit via the standard {@link CreatureSpawnEvent}.
 *
 * Algorithm on each NATURAL hostile spawn:
 *
 * 1. Count players within {@code mobCapConsiderRangeBlocks} of the spawn
 *    location. If zero, cancel (vanilla would have done the same via the
 *    standard "no nearby player" check, but we also handle edge cases like
 *    very-edge-of-render-distance chunks).
 * 2. Count monsters already alive in the same horizontal radius.
 * 3. If {@code monsters >= players * mobCapPerPlayerCap}, cancel the spawn.
 *    Otherwise let it proceed.
 *
 * Performance: bounded by {@link World#getNearbyEntities(Location, double,
 * double, double)} which uses Mojang's chunk index, so a typical 128-block
 * radius touches ~16 chunks max regardless of total mob count.
 *
 * Default: OFF. Mob spawn rate is gameplay-affecting and must be staging-
 * validated before prod. Operators opt in by setting
 * {@code mob_cap.enabled: true} in {@code stackmania-config/stackmania.yml}.
 */
public class StackmaniaMobCapDistributor implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!StackmaniaConfig.moduleMobCapDistributorEnabled) return;
        if (event.getSpawnReason() != SpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Monster)) return;

        final int range = StackmaniaConfig.mobCapConsiderRangeBlocks;
        final int perPlayerCap = StackmaniaConfig.mobCapPerPlayerCap;
        final Location loc = event.getLocation();
        final World world = loc.getWorld();
        if (world == null) return;

        // Step 1: count nearby players. Horizontal distance only — a player
        // far above or below the spawn (Nether bedrock ceiling, e.g.) should
        // not "own" mobs they can't possibly engage.
        int playersInRange = 0;
        final double sqRange = (double) range * range;
        for (Player p : world.getPlayers()) {
            if (!p.getWorld().equals(world)) continue;
            double dx = p.getLocation().getX() - loc.getX();
            double dz = p.getLocation().getZ() - loc.getZ();
            if (dx * dx + dz * dz <= sqRange) {
                playersInRange++;
            }
        }
        if (playersInRange == 0) {
            event.setCancelled(true);
            return;
        }

        // Step 2: count existing monsters in the same horizontal box. We
        // pass world.getMaxHeight() on the Y axis so the entire vertical
        // column counts — mobs spawned at y=20 still compete with those
        // spawned at y=80 for the same player budget.
        long monstersInRange = world.getNearbyEntities(loc, range, world.getMaxHeight(), range)
                .stream()
                .filter(Monster.class::isInstance)
                .count();

        long capacity = (long) playersInRange * perPlayerCap;
        if (monstersInRange >= capacity) {
            event.setCancelled(true);
        }
    }
}
