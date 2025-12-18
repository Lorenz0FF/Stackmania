/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.compatibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database of known mod/plugin conflicts and compatibility information.
 * Learns from runtime data to improve predictions.
 */
public class ConflictDatabase {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/ConflictDB");
    private static final String DATABASE_FILE = "stackmania-config/conflict-database.dat";
    
    private final Map<String, Set<String>> knownIncompatible = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> knownCompatible = new ConcurrentHashMap<>();
    private final Map<String, UniversalCompatibilityLayer.Conflict> conflictDetails = new ConcurrentHashMap<>();
    private final Map<String, Integer> conflictFrequency = new ConcurrentHashMap<>();
    
    public ConflictDatabase() {
        load();
        initializeKnownConflicts();
    }
    
    private void initializeKnownConflicts() {
        // Known problematic combinations from Mohist issues
        addKnownIncompatible("enhanced-celestials", "*", "Registry desync issues");
        addKnownIncompatible("the-deep-void", "*", "Dimension registry corruption");
        
        // Known working combinations
        addKnownCompatible("create", "worldguard");
        addKnownCompatible("create", "essentialsx");
        addKnownCompatible("jei", "essentialsx");
    }
    
    public void addKnownIncompatible(String modId, String pluginId, String reason) {
        String key = modId.toLowerCase();
        knownIncompatible.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(pluginId.toLowerCase());
        
        conflictDetails.put(key + "+" + pluginId.toLowerCase(), 
            new UniversalCompatibilityLayer.Conflict(
                UniversalCompatibilityLayer.Conflict.Type.VERSION_INCOMPATIBLE,
                reason, modId, pluginId
            ));
    }
    
    public void addKnownCompatible(String modId, String pluginId) {
        String key = modId.toLowerCase();
        knownCompatible.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(pluginId.toLowerCase());
    }
    
    public boolean isKnownIncompatible(String modId, String pluginId) {
        Set<String> incompatible = knownIncompatible.get(modId.toLowerCase());
        if (incompatible == null) return false;
        return incompatible.contains(pluginId.toLowerCase()) || incompatible.contains("*");
    }
    
    public boolean isKnownCompatible(String modId, String pluginId) {
        Set<String> compatible = knownCompatible.get(modId.toLowerCase());
        if (compatible == null) return false;
        return compatible.contains(pluginId.toLowerCase()) || compatible.contains("*");
    }
    
    public List<UniversalCompatibilityLayer.Conflict> findPotentialConflicts(BytecodeAnalysis analysis) {
        List<UniversalCompatibilityLayer.Conflict> conflicts = new ArrayList<>();
        
        String sourceId = analysis.getSourceId().toLowerCase();
        
        // Check if this mod/plugin has known conflicts
        if (knownIncompatible.containsKey(sourceId)) {
            Set<String> incompatible = knownIncompatible.get(sourceId);
            for (String target : incompatible) {
                UniversalCompatibilityLayer.Conflict detail = conflictDetails.get(sourceId + "+" + target);
                if (detail != null) {
                    conflicts.add(detail);
                }
            }
        }
        
        // Check for patterns that commonly cause conflicts
        if (analysis.modifiesCoreSystems()) {
            conflicts.add(new UniversalCompatibilityLayer.Conflict(
                UniversalCompatibilityLayer.Conflict.Type.CORE_MODIFICATION,
                "Modifies core Minecraft systems - high conflict risk",
                sourceId, null
            ));
        }
        
        if (analysis.usesReflection() && !analysis.getNmsAccess().isEmpty()) {
            conflicts.add(new UniversalCompatibilityLayer.Conflict(
                UniversalCompatibilityLayer.Conflict.Type.POTENTIAL_REFLECTION,
                "Uses reflection on NMS classes - may break with updates",
                sourceId, null
            ));
        }
        
        return conflicts;
    }
    
    public void recordConflict(UniversalCompatibilityLayer.Conflict conflict) {
        String key = conflict.getSourceId() + "+" + 
            (conflict.getTargetId() != null ? conflict.getTargetId() : "*");
        
        conflictDetails.put(key, conflict);
        conflictFrequency.merge(key, 1, Integer::sum);
        
        if (conflict.getTargetId() != null) {
            addKnownIncompatible(conflict.getSourceId(), conflict.getTargetId(), 
                conflict.getDescription());
        }
        
        LOGGER.info("Recorded conflict: {} (frequency: {})", key, conflictFrequency.get(key));
    }
    
    public void recordSuccess(String modId, String pluginId) {
        addKnownCompatible(modId, pluginId);
        LOGGER.debug("Recorded successful combination: {} + {}", modId, pluginId);
    }
    
    public int getConflictFrequency(String modId, String pluginId) {
        String key = modId.toLowerCase() + "+" + pluginId.toLowerCase();
        return conflictFrequency.getOrDefault(key, 0);
    }
    
    public void save() {
        try {
            File file = new File(DATABASE_FILE);
            file.getParentFile().mkdirs();
            
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(new HashMap<>(knownIncompatible));
                oos.writeObject(new HashMap<>(knownCompatible));
                oos.writeObject(new HashMap<>(conflictFrequency));
            }
            
            LOGGER.info("Conflict database saved: {} incompatible, {} compatible pairs",
                knownIncompatible.size(), knownCompatible.size());
                
        } catch (IOException e) {
            LOGGER.error("Failed to save conflict database: {}", e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void load() {
        File file = new File(DATABASE_FILE);
        if (!file.exists()) {
            LOGGER.info("No existing conflict database found, starting fresh");
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, Set<String>> loadedIncompatible = (Map<String, Set<String>>) ois.readObject();
            Map<String, Set<String>> loadedCompatible = (Map<String, Set<String>>) ois.readObject();
            Map<String, Integer> loadedFrequency = (Map<String, Integer>) ois.readObject();
            
            knownIncompatible.putAll(loadedIncompatible);
            knownCompatible.putAll(loadedCompatible);
            conflictFrequency.putAll(loadedFrequency);
            
            LOGGER.info("Conflict database loaded: {} incompatible, {} compatible pairs",
                knownIncompatible.size(), knownCompatible.size());
                
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.warn("Failed to load conflict database: {}", e.getMessage());
        }
    }
    
    public DatabaseStats getStats() {
        return new DatabaseStats(
            knownIncompatible.values().stream().mapToInt(Set::size).sum(),
            knownCompatible.values().stream().mapToInt(Set::size).sum(),
            conflictDetails.size(),
            conflictFrequency.values().stream().mapToInt(Integer::intValue).sum()
        );
    }
    
    public static class DatabaseStats {
        public final int incompatiblePairs;
        public final int compatiblePairs;
        public final int detailedConflicts;
        public final int totalOccurrences;
        
        public DatabaseStats(int incompatible, int compatible, int detailed, int occurrences) {
            this.incompatiblePairs = incompatible;
            this.compatiblePairs = compatible;
            this.detailedConflicts = detailed;
            this.totalOccurrences = occurrences;
        }
    }
}
