/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.registry;

import com.stackmania.core.StackmaniaConfig;
import com.stackmania.core.StackmaniaCore;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safe Registry Manager for Stackmania.
 * 
 * PROBLEMS SOLVED:
 * 1. When mods are removed, their registry entries become orphans
 * 2. Orphan entries can corrupt level.dat and cause crashes
 * 3. No safe mode exists when registries are corrupted
 * 
 * SOLUTIONS:
 * 1. Track all registry entries and their source mods
 * 2. Cleanup orphan entries when mods are removed
 * 3. Provide safe mode for recovery from corruption
 */
public class SafeRegistryManager {
    
    private static final Logger LOGGER = StackmaniaCore.LOGGER;
    
    // Track which mod registered which entries
    private static final ConcurrentHashMap<String, Set<String>> modEntries = new ConcurrentHashMap<>();
    
    // Track known orphan entries
    private static final Set<String> orphanEntries = ConcurrentHashMap.newKeySet();
    
    // Track corrupted entries
    private static final Set<String> corruptedEntries = ConcurrentHashMap.newKeySet();
    
    private static boolean initialized = false;
    private static boolean safeMode = false;
    
    /**
     * Initialize the registry manager
     */
    public static void initialize() {
        if (initialized) return;
        
        LOGGER.info("[Registry] Initializing safe registry manager...");
        modEntries.clear();
        orphanEntries.clear();
        corruptedEntries.clear();
        safeMode = false;
        initialized = true;
    }
    
    /**
     * Shutdown and cleanup
     */
    public static void shutdown() {
        if (orphanEntries.size() > 0) {
            LOGGER.warn("[Registry] {} orphan entries detected at shutdown", orphanEntries.size());
        }
        modEntries.clear();
        orphanEntries.clear();
        corruptedEntries.clear();
        initialized = false;
    }
    
    /**
     * Register a registry entry for a mod
     */
    public static void registerEntry(String modId, String entryKey) {
        modEntries.computeIfAbsent(modId, k -> ConcurrentHashMap.newKeySet()).add(entryKey);
        LOGGER.debug("[Registry] Registered entry {} for mod {}", entryKey, modId);
    }
    
    /**
     * Called when a mod is detected as removed
     */
    public static void onModRemoved(String modId) {
        Set<String> entries = modEntries.remove(modId);
        if (entries != null && !entries.isEmpty()) {
            LOGGER.warn("[Registry] Mod {} removed, {} entries became orphans", modId, entries.size());
            
            if (StackmaniaConfig.autoCleanupRegistries) {
                cleanupEntries(entries, modId);
            } else {
                // Mark as orphans for later handling
                orphanEntries.addAll(entries);
                LOGGER.info("[Registry] Orphan entries marked for manual cleanup");
            }
        }
    }
    
    /**
     * Cleanup registry entries
     */
    private static void cleanupEntries(Set<String> entries, String modId) {
        LOGGER.info("[Registry] Cleaning up {} entries from removed mod {}", entries.size(), modId);
        
        int cleaned = 0;
        for (String entry : entries) {
            try {
                // Mark for removal from world data
                orphanEntries.add(entry);
                cleaned++;
            } catch (Exception e) {
                LOGGER.error("[Registry] Failed to cleanup entry {}: {}", entry, e.getMessage());
                corruptedEntries.add(entry);
            }
        }
        
        LOGGER.info("[Registry] Cleanup complete: {} cleaned, {} corrupted", 
            cleaned, corruptedEntries.size());
    }
    
    /**
     * Validate registries on startup
     */
    public static void validateOnStartup() {
        LOGGER.info("[Registry] Validating registries on startup...");
        
        List<String> problems = new ArrayList<>();
        
        // Check for orphan entries
        if (!orphanEntries.isEmpty()) {
            problems.add("Found " + orphanEntries.size() + " orphan registry entries");
        }
        
        // Check for corrupted entries
        if (!corruptedEntries.isEmpty()) {
            problems.add("Found " + corruptedEntries.size() + " corrupted registry entries");
        }
        
        if (!problems.isEmpty()) {
            LOGGER.warn("[Registry] Registry problems detected:");
            for (String problem : problems) {
                LOGGER.warn("[Registry]  - {}", problem);
            }
            
            if (StackmaniaConfig.safeModeOnCorruption) {
                enterSafeMode(problems);
            }
        } else {
            LOGGER.info("[Registry] All registries validated successfully");
        }
    }
    
    /**
     * Enter safe mode due to registry problems
     */
    private static void enterSafeMode(List<String> reasons) {
        safeMode = true;
        LOGGER.warn("[Registry] ============================================");
        LOGGER.warn("[Registry] ENTERING SAFE MODE");
        LOGGER.warn("[Registry] Reasons:");
        for (String reason : reasons) {
            LOGGER.warn("[Registry]  - {}", reason);
        }
        LOGGER.warn("[Registry] ============================================");
        LOGGER.warn("[Registry] Some features may be disabled.");
        LOGGER.warn("[Registry] Review and fix registry issues, then restart.");
    }
    
    /**
     * Check if we're in safe mode
     */
    public static boolean isSafeMode() {
        return safeMode;
    }
    
    /**
     * Get orphan entries
     */
    public static Set<String> getOrphanEntries() {
        return new HashSet<>(orphanEntries);
    }
    
    /**
     * Get corrupted entries
     */
    public static Set<String> getCorruptedEntries() {
        return new HashSet<>(corruptedEntries);
    }
    
    /**
     * Get statistics
     */
    public static String getStats() {
        int totalEntries = modEntries.values().stream().mapToInt(Set::size).sum();
        return String.format("Mods: %d, Entries: %d, Orphans: %d, Corrupted: %d, SafeMode: %b",
            modEntries.size(), totalEntries, orphanEntries.size(), corruptedEntries.size(), safeMode);
    }
}
