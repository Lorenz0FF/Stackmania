/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.compatibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bytecode Analyzer for mod/plugin compatibility analysis.
 * Scans JAR files to extract class information, API usage, and potential conflicts.
 */
public class BytecodeAnalyzer {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/BytecodeAnalyzer");
    
    private static final Set<String> FORGE_PACKAGES = Set.of(
        "net.minecraftforge",
        "cpw.mods.fml",
        "net.neoforged"
    );
    
    private static final Set<String> BUKKIT_PACKAGES = Set.of(
        "org.bukkit",
        "org.spigotmc",
        "io.papermc",
        "com.destroystokyo.paper"
    );
    
    private static final Set<String> NMS_PACKAGES = Set.of(
        "net.minecraft.server",
        "org.bukkit.craftbukkit"
    );
    
    private static final Set<String> MIXIN_PACKAGES = Set.of(
        "org.spongepowered.asm.mixin"
    );
    
    public BytecodeAnalysis analyze(File jarFile) throws IOException {
        BytecodeAnalysis analysis = new BytecodeAnalysis(jarFile.getName());
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.endsWith(".class")) {
                    analyzeClass(jar, entry, analysis);
                } else if (name.equals("mods.toml") || name.equals("META-INF/mods.toml")) {
                    analysis.setHasModsToml(true);
                } else if (name.equals("plugin.yml")) {
                    analysis.setHasPluginYml(true);
                } else if (name.endsWith(".mixins.json") || name.contains("mixin")) {
                    analysis.setHasMixins(true);
                }
            }
        }
        
        return analysis;
    }
    
    private void analyzeClass(JarFile jar, JarEntry entry, BytecodeAnalysis analysis) {
        String className = entry.getName()
            .replace("/", ".")
            .replace(".class", "");
        
        analysis.addClassName(className);
        
        try (InputStream is = jar.getInputStream(entry)) {
            byte[] bytecode = is.readAllBytes();
            analyzeClassBytecode(className, bytecode, analysis);
        } catch (IOException e) {
            LOGGER.debug("Could not analyze class {}: {}", className, e.getMessage());
        }
    }
    
    private void analyzeClassBytecode(String className, byte[] bytecode, BytecodeAnalysis analysis) {
        String bytecodeStr = new String(bytecode);
        
        // Check for Forge API usage
        for (String pkg : FORGE_PACKAGES) {
            if (bytecodeStr.contains(pkg.replace(".", "/"))) {
                analysis.addForgeHook(className + " uses " + pkg);
            }
        }
        
        // Check for Bukkit API usage
        for (String pkg : BUKKIT_PACKAGES) {
            if (bytecodeStr.contains(pkg.replace(".", "/"))) {
                analysis.addBukkitApiUsage(className + " uses " + pkg);
            }
        }
        
        // Check for NMS access
        for (String pkg : NMS_PACKAGES) {
            if (bytecodeStr.contains(pkg.replace(".", "/"))) {
                analysis.addNmsAccess(className + " accesses " + pkg);
            }
        }
        
        // Check for Mixin usage
        for (String pkg : MIXIN_PACKAGES) {
            if (bytecodeStr.contains(pkg.replace(".", "/"))) {
                analysis.addMixin(className);
            }
        }
        
        // Check for reflection usage
        if (bytecodeStr.contains("java/lang/reflect/") || 
            bytecodeStr.contains("getDeclaredField") ||
            bytecodeStr.contains("getDeclaredMethod") ||
            bytecodeStr.contains("setAccessible")) {
            analysis.setUsesReflection(true);
        }
        
        // Check for core system modification
        if (bytecodeStr.contains("net/minecraft/server/MinecraftServer") ||
            bytecodeStr.contains("net/minecraft/world/level/Level") ||
            bytecodeStr.contains("net/minecraft/world/entity/player/Player")) {
            analysis.setModifiesCoreSystems(true);
        }
    }
}

/**
 * Result of bytecode analysis
 */
class BytecodeAnalysis {
    private final String sourceId;
    private final Set<String> classNames = new HashSet<>();
    private final List<String> forgeHooks = new ArrayList<>();
    private final List<String> mixins = new ArrayList<>();
    private final List<String> apiCalls = new ArrayList<>();
    private final List<String> bukkitApiUsage = new ArrayList<>();
    private final List<String> nmsAccess = new ArrayList<>();
    private final List<String> paperFeatures = new ArrayList<>();
    
    private boolean hasModsToml = false;
    private boolean hasPluginYml = false;
    private boolean hasMixins = false;
    private boolean usesReflection = false;
    private boolean modifiesCoreSystems = false;
    
    public BytecodeAnalysis(String sourceId) {
        this.sourceId = sourceId;
    }
    
    public String getSourceId() { return sourceId; }
    
    public void addClassName(String name) { classNames.add(name); }
    public Set<String> getClassNames() { return classNames; }
    
    public void addForgeHook(String hook) { forgeHooks.add(hook); }
    public List<String> getForgeHooks() { return forgeHooks; }
    
    public void addMixin(String mixin) { mixins.add(mixin); }
    public List<String> getMixins() { return mixins; }
    
    public void addApiCall(String call) { apiCalls.add(call); }
    public List<String> getApiCalls() { return apiCalls; }
    
    public void addBukkitApiUsage(String usage) { bukkitApiUsage.add(usage); }
    public List<String> getBukkitApiUsage() { return bukkitApiUsage; }
    
    public void addNmsAccess(String access) { nmsAccess.add(access); }
    public List<String> getNmsAccess() { return nmsAccess; }
    
    public void addPaperFeature(String feature) { paperFeatures.add(feature); }
    public List<String> getPaperFeatures() { return paperFeatures; }
    
    public void setHasModsToml(boolean has) { this.hasModsToml = has; }
    public boolean hasModsToml() { return hasModsToml; }
    
    public void setHasPluginYml(boolean has) { this.hasPluginYml = has; }
    public boolean hasPluginYml() { return hasPluginYml; }
    
    public void setHasMixins(boolean has) { this.hasMixins = has; }
    public boolean hasMixins() { return hasMixins; }
    
    public void setUsesReflection(boolean uses) { this.usesReflection = uses; }
    public boolean usesReflection() { return usesReflection; }
    
    public void setModifiesCoreSystems(boolean modifies) { this.modifiesCoreSystems = modifies; }
    public boolean modifiesCoreSystems() { return modifiesCoreSystems; }
    
    public boolean isMod() { return hasModsToml; }
    public boolean isPlugin() { return hasPluginYml; }
}
