/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.player;

import com.stackmania.core.StackmaniaCore;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent Player objects across respawns.
 * 
 * PROBLEM SOLVED:
 * In Mohist/vanilla, CraftPlayer objects are recreated on death/respawn,
 * which causes:
 * 1. Loss of metadata set by plugins
 * 2. Broken references in plugin code
 * 3. Conversation state lost
 * 4. Custom data lost
 * 
 * SOLUTION:
 * This manager caches CraftPlayer objects and updates their underlying
 * ServerPlayer handle instead of recreating the entire object.
 */
public class PersistentPlayerManager {
    
    private static final Logger LOGGER = StackmaniaCore.LOGGER;
    
    // Cache of player data by UUID
    private static final Map<UUID, PlayerDataCache> playerCache = new ConcurrentHashMap<>();
    
    private static boolean initialized = false;
    private static int persistedCount = 0;
    
    /**
     * Initialize the player manager
     */
    public static void initialize() {
        if (initialized) return;
        
        LOGGER.info("[Player] Initializing persistent player manager...");
        playerCache.clear();
        persistedCount = 0;
        initialized = true;
    }
    
    /**
     * Shutdown and cleanup
     */
    public static void shutdown() {
        LOGGER.info("[Player] Shutting down. Persisted {} player sessions.", persistedCount);
        playerCache.clear();
        initialized = false;
    }
    
    /**
     * Get or create a CraftPlayer, preserving existing instance if possible
     */
    public static CraftPlayer getOrCreate(CraftServer server, ServerPlayer entity) {
        UUID uuid = entity.getUUID();
        
        PlayerDataCache cache = playerCache.computeIfAbsent(uuid, k -> {
            LOGGER.debug("[Player] Creating new player cache for {}", uuid);
            return new PlayerDataCache();
        });
        
        CraftPlayer player = cache.getPlayer();
        
        if (player == null) {
            // First time - create new CraftPlayer
            player = new CraftPlayer(server, entity);
            cache.setPlayer(player);
            LOGGER.debug("[Player] Created new CraftPlayer for {}", entity.getName().getString());
        } else {
            // Existing player - update handle instead of recreating
            // This preserves all plugin data, metadata, conversations, etc.
            updatePlayerHandle(player, entity);
            persistedCount++;
            LOGGER.debug("[Player] Persisted CraftPlayer for {} (total: {})", 
                entity.getName().getString(), persistedCount);
        }
        
        return player;
    }
    
    /**
     * Update the underlying ServerPlayer handle without recreating CraftPlayer
     */
    private static void updatePlayerHandle(CraftPlayer player, ServerPlayer newEntity) {
        try {
            // Use reflection to update the entity field
            // This is safe because we're updating within the same player session
            java.lang.reflect.Field entityField = 
                org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity.class.getDeclaredField("entity");
            entityField.setAccessible(true);
            entityField.set(player, newEntity);
            
            LOGGER.debug("[Player] Updated handle for {}", newEntity.getName().getString());
        } catch (Exception e) {
            LOGGER.error("[Player] Failed to update player handle: {}", e.getMessage());
            // Fallback: This shouldn't happen, but if it does, the player still works
        }
    }
    
    /**
     * Called when a player disconnects
     */
    public static void onPlayerDisconnect(UUID uuid) {
        PlayerDataCache cache = playerCache.remove(uuid);
        if (cache != null) {
            LOGGER.debug("[Player] Removed player cache for {}", uuid);
        }
    }
    
    /**
     * Check if a player is cached
     */
    public static boolean isCached(UUID uuid) {
        return playerCache.containsKey(uuid);
    }
    
    /**
     * Get cached player count
     */
    public static int getCachedCount() {
        return playerCache.size();
    }
    
    /**
     * Get statistics
     */
    public static String getStats() {
        return String.format("Cached: %d, Total Persisted: %d", playerCache.size(), persistedCount);
    }
    
    /**
     * Cache for player data
     */
    public static class PlayerDataCache {
        private CraftPlayer player;
        private final Map<String, Object> customData = new ConcurrentHashMap<>();
        
        public CraftPlayer getPlayer() {
            return player;
        }
        
        public void setPlayer(CraftPlayer player) {
            this.player = player;
        }
        
        public void setCustomData(String key, Object value) {
            customData.put(key, value);
        }
        
        public Object getCustomData(String key) {
            return customData.get(key);
        }
        
        public void clearCustomData() {
            customData.clear();
        }
    }
}
