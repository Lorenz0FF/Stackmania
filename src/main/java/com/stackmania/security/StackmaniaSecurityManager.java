/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.security;

import com.stackmania.core.StackmaniaConfig;
import com.stackmania.core.StackmaniaCore;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Security manager for Stackmania.
 * 
 * KEY SECURITY PRINCIPLES:
 * 1. NO automatic plugin downloading or replacement
 * 2. NO hot-loading of plugins at runtime (use restart)
 * 3. Validate all plugin sources
 * 4. Log all security-relevant events
 * 
 * This replaces the dangerous plugin management system from Mohist.
 */
public class StackmaniaSecurityManager {
    
    private static final Logger LOGGER = StackmaniaCore.LOGGER;
    
    private static boolean initialized = false;
    private static final Set<String> trustedSources = new HashSet<>();
    private static final Set<String> blockedOperations = new HashSet<>();
    
    static {
        // Block dangerous operations by default
        blockedOperations.add("plugin.hotload");
        blockedOperations.add("plugin.hotunload");
        blockedOperations.add("plugin.hotreplacement");
        blockedOperations.add("code.download");
        blockedOperations.add("jar.modification");
    }
    
    /**
     * Initialize the security manager
     */
    public static void initialize() {
        if (initialized) return;
        
        LOGGER.info("[Security] Initializing Stackmania Security Manager...");
        LOGGER.info("[Security] Plugin hot-loading is DISABLED for security");
        LOGGER.info("[Security] Use server restart to add/remove plugins");
        
        initialized = true;
    }
    
    /**
     * Shutdown the security manager
     */
    public static void shutdown() {
        LOGGER.info("[Security] Security manager shutting down");
        trustedSources.clear();
        initialized = false;
    }
    
    /**
     * Check if an operation is allowed
     */
    public static boolean isOperationAllowed(String operation) {
        if (blockedOperations.contains(operation)) {
            logSecurityEvent("BLOCKED", "Operation blocked: " + operation);
            return false;
        }
        return true;
    }
    
    /**
     * Validate a plugin file before loading
     */
    public static boolean validatePlugin(File pluginFile) {
        if (!pluginFile.exists()) {
            logSecurityEvent("WARN", "Plugin file does not exist: " + pluginFile.getName());
            return false;
        }
        
        if (!pluginFile.getName().endsWith(".jar")) {
            logSecurityEvent("BLOCKED", "Invalid plugin file extension: " + pluginFile.getName());
            return false;
        }
        
        // Check file is in plugins directory
        File pluginsDir = new File("plugins");
        try {
            if (!pluginFile.getCanonicalPath().startsWith(pluginsDir.getCanonicalPath())) {
                logSecurityEvent("BLOCKED", "Plugin outside plugins directory: " + pluginFile.getName());
                return false;
            }
        } catch (Exception e) {
            logSecurityEvent("ERROR", "Failed to validate plugin path: " + e.getMessage());
            return false;
        }
        
        logSecurityEvent("OK", "Plugin validated: " + pluginFile.getName());
        return true;
    }
    
    /**
     * Check if runtime plugin operations are allowed
     * ALWAYS returns false - plugins should only be loaded at startup
     */
    public static boolean isRuntimePluginOperationAllowed() {
        logSecurityEvent("BLOCKED", "Runtime plugin operation attempted - not allowed");
        return false;
    }
    
    /**
     * Log a security event
     */
    public static void logSecurityEvent(String level, String message) {
        if (!StackmaniaConfig.enableSecurityLogs) return;
        
        switch (level) {
            case "BLOCKED":
                LOGGER.warn("[Security:BLOCKED] {}", message);
                break;
            case "WARN":
                LOGGER.warn("[Security:WARN] {}", message);
                break;
            case "ERROR":
                LOGGER.error("[Security:ERROR] {}", message);
                break;
            default:
                LOGGER.info("[Security:{}] {}", level, message);
        }
    }
    
    /**
     * Add a trusted source for plugins
     */
    public static void addTrustedSource(String source) {
        trustedSources.add(source);
        logSecurityEvent("INFO", "Added trusted source: " + source);
    }
    
    /**
     * Check if security manager is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
