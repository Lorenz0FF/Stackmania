/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.core;

import com.mohistmc.util.YamlUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Centralized configuration manager for Stackmania.
 * Replaces MohistConfig with cleaner, more secure defaults.
 */
public class StackmaniaConfig {

    private static final List<String> HEADER = Arrays.asList("""
            ╔══════════════════════════════════════════════════════════╗
            ║              STACKMANIA CONFIGURATION                     ║
            ║          Optimized Forge + Bukkit Hybrid Server          ║
            ╚══════════════════════════════════════════════════════════╝
            
            This is the main configuration file for Stackmania.
            Documentation: https://github.com/ValoniGames/Stackmania/wiki
            
            """.split("\\n"));

    private static final File CONFIG_DIR = new File("stackmania-config");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "stackmania.yml");
    
    public static YamlConfiguration config;
    public static YamlConfiguration yml;

    // ==================== GENERAL ====================
    public static boolean showLogo = true;
    public static String language = Locale.getDefault().toString();
    public static boolean checkUpdates = true;
    public static String serverModName = "stackmania";

    // ==================== SECURITY ====================
    // NOTE: Plugin hot-loading is REMOVED for security reasons
    // Use server restart to load/unload plugins
    public static boolean enableSecurityLogs = true;
    public static boolean validatePluginSources = true;

    // ==================== PERFORMANCE ====================
    public static int serverThreadPriority = 8;
    public static boolean asyncWorldSave = false;
    public static boolean watchdogEnabled = true;

    // ==================== REGISTRY ====================
    public static boolean autoCleanupRegistries = false;
    public static boolean safeModeOnCorruption = true;
    public static boolean logRegistryChanges = true;

    // ==================== COMPATIBILITY ====================
    public static boolean velocityEnabled = false;
    public static boolean velocityOnlineMode = false;
    public static String velocitySecret = "";
    public static boolean bukkitPermissionsHandler = true;

    // ==================== GAMEPLAY ====================
    public static int maximumRepairCost = 40;
    public static boolean enchantmentFix = false;
    public static int maxEnchantmentLevel = 32767;
    public static int maxBeesInHive = 3;

    // ==================== ENTITY MANAGEMENT ====================
    public static boolean entityClearEnabled = false;
    public static int entityClearInterval = 1800;

    // ==================== MESSAGES ====================
    public static String motdFirstLine = "<gradient:#00ff88:#0088ff>Stackmania Server</gradient>";
    public static String motdSecondLine = "<gray>Forge + Bukkit Hybrid</gray>";

    public static void init() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        config = new YamlConfiguration();
        
        if (CONFIG_FILE.exists()) {
            try {
                config.load(CONFIG_FILE);
            } catch (IOException | InvalidConfigurationException ex) {
                Bukkit.getLogger().log(Level.SEVERE, "Could not load stackmania.yml", ex);
            }
        }

        config.options().setHeader(HEADER);
        config.options().copyDefaults(true);

        yml = config;
        
        loadConfig();
        save();
    }

    private static void loadConfig() {
        // General
        showLogo = getBoolean("general.show_logo", true);
        language = getString("general.language", Locale.getDefault().toString());
        checkUpdates = getBoolean("general.check_updates", true);
        serverModName = getString("general.server_mod_name", "stackmania");

        // Security
        enableSecurityLogs = getBoolean("security.enable_logs", true);
        validatePluginSources = getBoolean("security.validate_plugin_sources", true);

        // Performance
        serverThreadPriority = getInt("performance.server_thread_priority", 8);
        asyncWorldSave = getBoolean("performance.async_world_save", false);
        watchdogEnabled = getBoolean("performance.watchdog_enabled", true);

        // Registry
        autoCleanupRegistries = getBoolean("registry.auto_cleanup", false);
        safeModeOnCorruption = getBoolean("registry.safe_mode_on_corruption", true);
        logRegistryChanges = getBoolean("registry.log_changes", true);

        // Compatibility
        velocityEnabled = getBoolean("velocity.enabled", false);
        velocityOnlineMode = getBoolean("velocity.online_mode", false);
        velocitySecret = getString("velocity.secret", "");
        bukkitPermissionsHandler = getBoolean("compatibility.bukkit_permissions_handler", true);

        // Gameplay
        maximumRepairCost = getInt("gameplay.anvil.maximum_repair_cost", 40);
        enchantmentFix = getBoolean("gameplay.anvil.enchantment_fix", false);
        maxEnchantmentLevel = getInt("gameplay.anvil.max_enchantment_level", 32767);
        maxBeesInHive = getInt("gameplay.max_bees_in_hive", 3);

        // Entity Management
        entityClearEnabled = getBoolean("entity.clear.enabled", false);
        entityClearInterval = getInt("entity.clear.interval_seconds", 1800);

        // Messages
        motdFirstLine = getString("messages.motd.first_line", motdFirstLine);
        motdSecondLine = getString("messages.motd.second_line", motdSecondLine);
    }

    public static void save() {
        YamlUtils.save(CONFIG_FILE, config);
    }

    public static String getLanguage() {
        return language;
    }

    public static boolean isProxyOnlineMode() {
        return Bukkit.getOnlineMode() || (velocityEnabled && velocityOnlineMode);
    }

    // ==================== HELPER METHODS ====================

    private static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, def);
    }

    private static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, def);
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, def);
    }

    private static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, def);
    }

    private static <T> List<String> getStringList(String path, T def) {
        config.addDefault(path, def);
        return config.getStringList(path);
    }
}
