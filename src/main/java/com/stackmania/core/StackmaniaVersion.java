/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.core;

import java.util.Map;

/**
 * Version information holder for Stackmania.
 */
public class StackmaniaVersion {
    
    private final String stackmania;
    private final String bukkit;
    private final String craftbukkit;
    private final String spigot;
    private final String neoforge;
    private final String forge;
    private final String target;
    
    public StackmaniaVersion(Map<String, String> arguments) {
        this.stackmania = arguments.getOrDefault("stackmania", "unknown");
        this.bukkit = arguments.getOrDefault("bukkit", "unknown");
        this.craftbukkit = arguments.getOrDefault("craftbukkit", "unknown");
        this.spigot = arguments.getOrDefault("spigot", "unknown");
        this.neoforge = arguments.getOrDefault("neoforge", "unknown");
        this.forge = arguments.getOrDefault("forge", "unknown");
        this.target = arguments.getOrDefault("target", "unknown");
    }
    
    public String getStackmania() { return stackmania; }
    public String getBukkit() { return bukkit; }
    public String getCraftbukkit() { return craftbukkit; }
    public String getSpigot() { return spigot; }
    public String getNeoforge() { return neoforge; }
    public String getForge() { return forge; }
    public String getTarget() { return target; }
    
    public String getFullVersion() {
        return String.format("Stackmania %s (Forge %s, Bukkit %s, Target: %s)", 
            stackmania, forge, bukkit, target);
    }
    
    @Override
    public String toString() {
        return getFullVersion();
    }
}
