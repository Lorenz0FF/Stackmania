/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.stackmania.core;

import com.stackmania.material.MaterialCacheManager;
import com.stackmania.player.PersistentPlayerManager;
import com.stackmania.registry.SafeRegistryManager;
import com.stackmania.security.StackmaniaSecurityManager;
import com.mohistmc.i18n.i18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stackmania Core - Main entry point for the Stackmania hybrid server.
 * 
 * Stackmania is an optimized fork of Mohist designed for stability,
 * security, and compatibility with both Forge mods and Bukkit plugins.
 * 
 * Key improvements over Mohist:
 * - No automatic plugin replacement (security)
 * - Fixed Material double-injection
 * - Persistent Player objects across respawns
 * - Safe Registry management with cleanup
 * - Paper API compatibility layer
 * 
 * @author Valonia Games
 * @version 1.0.0
 */
@Mod("stackmania")
@OnlyIn(Dist.DEDICATED_SERVER)
public class StackmaniaCore {
    
    public static final String NAME = "Stackmania";
    public static final String MOD_ID = "stackmania";
    public static final String VERSION = "1.20.1";
    public static final String BUILD_TARGET = "SpyGut";
    
    public static final Logger LOGGER = LogManager.getLogger(NAME);
    
    public static i18n i18n;
    public static ClassLoader classLoader;
    public static StackmaniaVersion versionInfo;
    
    private static boolean initialized = false;
    private static long startTime;
    
    public StackmaniaCore() {
        startTime = System.currentTimeMillis();
        classLoader = StackmaniaCore.class.getClassLoader();
        
        LOGGER.info("╔══════════════════════════════════════════════════════════╗");
        LOGGER.info("║              STACKMANIA SERVER LOADING                    ║");
        LOGGER.info("║          Optimized Forge + Bukkit Hybrid Server          ║");
        LOGGER.info("║                   by Valonia Games                        ║");
        LOGGER.info("╚══════════════════════════════════════════════════════════╝");
        
        // Initialize core systems in order
        initializeSecurity();
        initializeRegistryManager();
        initializeMaterialCache();
        initializePlayerManager();
        
        initialized = true;
        LOGGER.info("{} core systems initialized in {}ms", NAME, System.currentTimeMillis() - startTime);
    }
    
    /**
     * Initialize security systems - MUST be first
     */
    private void initializeSecurity() {
        LOGGER.info("Initializing security manager...");
        StackmaniaSecurityManager.initialize();
    }
    
    /**
     * Initialize the safe registry manager for handling mod additions/removals
     */
    private void initializeRegistryManager() {
        LOGGER.info("Initializing safe registry manager...");
        SafeRegistryManager.initialize();
    }
    
    /**
     * Initialize the material cache to prevent double-injection issues
     */
    private void initializeMaterialCache() {
        LOGGER.info("Initializing material cache manager...");
        MaterialCacheManager.initialize();
    }
    
    /**
     * Initialize the persistent player manager
     */
    private void initializePlayerManager() {
        LOGGER.info("Initializing persistent player manager...");
        PersistentPlayerManager.initialize();
    }
    
    /**
     * Called when the server is fully started to initialize version info
     */
    public static void initVersion() {
        String lang = StackmaniaConfig.getLanguage();
        i18n = new i18n(StackmaniaCore.class.getClassLoader(), lang);
        
        Map<String, String> arguments = new HashMap<>();
        String[] cbs = CraftServer.class.getPackage().getImplementationVersion().split("-");
        
        arguments.put("stackmania", getStackmaniaVersion());
        arguments.put("bukkit", cbs[0]);
        arguments.put("craftbukkit", cbs[1]);
        arguments.put("spigot", cbs[2]);
        arguments.put("neoforge", cbs[3]);
        arguments.put("forge", ForgeVersion.getVersion());
        arguments.put("target", BUILD_TARGET);
        
        versionInfo = new StackmaniaVersion(arguments);
        
        LOGGER.info("Stackmania {} initialized - Target: {} - Forge: {}", 
            getStackmaniaVersion(), BUILD_TARGET, ForgeVersion.getVersion());
    }
    
    /**
     * Get the Stackmania version string
     */
    public static String getStackmaniaVersion() {
        String implVersion = StackmaniaCore.class.getPackage().getImplementationVersion();
        return implVersion != null ? implVersion : VERSION;
    }
    
    /**
     * Check if Stackmania core is fully initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get the time taken to start in milliseconds
     */
    public static long getStartupTime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Shutdown hook for cleanup
     */
    public static void shutdown() {
        LOGGER.info("Stackmania shutting down...");
        
        // Cleanup in reverse order
        PersistentPlayerManager.shutdown();
        MaterialCacheManager.shutdown();
        SafeRegistryManager.shutdown();
        StackmaniaSecurityManager.shutdown();
        
        LOGGER.info("Stackmania shutdown complete.");
    }
}
