/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.material;

import com.stackmania.core.StackmaniaCore;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.bukkit.Material;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a unified cache of Bukkit Materials to prevent double-injection issues.
 * 
 * PROBLEM SOLVED:
 * In Mohist, when a Forge mod adds a block that also has an item form,
 * addMaterial() could be called twice (once for block, once for item),
 * creating duplicate entries or corrupted state.
 * 
 * SOLUTION:
 * This cache ensures each material is registered only once, with proper
 * tracking of whether it's a block, item, or both.
 */
public class MaterialCacheManager {
    
    private static final Logger LOGGER = StackmaniaCore.LOGGER;
    
    // Cache keyed by normalized material name
    private static final Map<String, MaterialEntry> materialCache = new ConcurrentHashMap<>();
    
    // Cache keyed by ResourceLocation for quick lookup
    private static final Map<ResourceLocation, MaterialEntry> resourceCache = new ConcurrentHashMap<>();
    
    private static boolean initialized = false;
    private static int duplicatesAvoided = 0;
    
    /**
     * Initialize the material cache manager
     */
    public static void initialize() {
        if (initialized) return;
        
        LOGGER.info("[MaterialCache] Initializing material cache manager...");
        materialCache.clear();
        resourceCache.clear();
        duplicatesAvoided = 0;
        initialized = true;
    }
    
    /**
     * Shutdown and cleanup
     */
    public static void shutdown() {
        LOGGER.info("[MaterialCache] Shutting down. Avoided {} duplicate registrations.", duplicatesAvoided);
        materialCache.clear();
        resourceCache.clear();
        initialized = false;
    }
    
    /**
     * Get an existing material from cache by normalized name
     */
    public static Material get(String normalizedName) {
        MaterialEntry entry = materialCache.get(normalizedName);
        return entry != null ? entry.material : null;
    }
    
    /**
     * Get an existing material from cache by ResourceLocation
     */
    public static Material get(ResourceLocation resourceLocation) {
        MaterialEntry entry = resourceCache.get(resourceLocation);
        return entry != null ? entry.material : null;
    }
    
    /**
     * Check if a material is already registered
     */
    public static boolean isRegistered(String normalizedName) {
        return materialCache.containsKey(normalizedName);
    }
    
    /**
     * Check if a material is already registered by ResourceLocation
     */
    public static boolean isRegistered(ResourceLocation resourceLocation) {
        return resourceCache.containsKey(resourceLocation);
    }
    
    /**
     * Register a new material in the cache
     * Returns the existing material if already registered (avoiding duplicates)
     */
    public static Material register(String normalizedName, Material material, ResourceLocation resourceLocation, 
                                    boolean isBlock, boolean isItem) {
        
        // Check if already exists
        MaterialEntry existing = materialCache.get(normalizedName);
        if (existing != null) {
            // Update flags if needed (a material can be both block and item)
            if (isBlock && !existing.isBlock) {
                existing.isBlock = true;
                LOGGER.debug("[MaterialCache] Updated {} to include block form", normalizedName);
            }
            if (isItem && !existing.isItem) {
                existing.isItem = true;
                LOGGER.debug("[MaterialCache] Updated {} to include item form", normalizedName);
            }
            duplicatesAvoided++;
            return existing.material;
        }
        
        // Create new entry
        MaterialEntry entry = new MaterialEntry(material, resourceLocation, isBlock, isItem);
        materialCache.put(normalizedName, entry);
        
        if (resourceLocation != null) {
            resourceCache.put(resourceLocation, entry);
        }
        
        LOGGER.debug("[MaterialCache] Registered new material: {} (block={}, item={})", 
            normalizedName, isBlock, isItem);
        
        return material;
    }
    
    /**
     * Get statistics about the cache
     */
    public static String getStats() {
        return String.format("Materials: %d, Duplicates Avoided: %d", 
            materialCache.size(), duplicatesAvoided);
    }
    
    /**
     * Get the number of registered materials
     */
    public static int size() {
        return materialCache.size();
    }
    
    /**
     * Entry holder for cached materials
     */
    private static class MaterialEntry {
        final Material material;
        final ResourceLocation resourceLocation;
        boolean isBlock;
        boolean isItem;
        
        MaterialEntry(Material material, ResourceLocation resourceLocation, boolean isBlock, boolean isItem) {
            this.material = material;
            this.resourceLocation = resourceLocation;
            this.isBlock = isBlock;
            this.isItem = isItem;
        }
    }
}
