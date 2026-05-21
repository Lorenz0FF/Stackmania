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

    // ==================== MOB CAP DISTRIBUTOR ====================
    // Paper-style per-player mob cap distribution. Disabled by default — the
    // listener is a no-op until the operator opts in, because mob spawn rate
    // affects gameplay enough that it must be staging-tested before prod.
    public static boolean moduleMobCapDistributorEnabled = false;
    // Max hostile monsters considered "owned" by a single nearby player.
    public static int mobCapPerPlayerCap = 25;
    // Horizontal radius (blocks) used to count nearby players and monsters.
    public static int mobCapConsiderRangeBlocks = 128;

    // ==================== DYNAMIC VIEW DISTANCE ====================
    // Adjusts every player's view distance once per check interval based on
    // current TPS. Disabled by default — operators opt in via stackmania.yml.
    public static boolean moduleDynamicViewDistanceEnabled = false;
    // Highest view distance allowed when the server is healthy (TPS >= 19.5).
    public static int dynamicViewDistanceMax = 10;
    // Lowest view distance allowed when the server is under load (TPS < 18.0).
    public static int dynamicViewDistanceMin = 4;
    // How often (in ticks) to re-evaluate. 600 = 30 s at 20 TPS.
    public static int dynamicViewDistanceCheckIntervalTicks = 600;

    // ==================== PARALLEL BOOT INIT ====================
    // When enabled, the 8 "heavy" Stackmania layers (5-12 in the legacy
    // numbering) run their initialize() in parallel via CompletableFuture
    // instead of sequentially. Per-layer timing is always logged, so even
    // with this flag off you can see which layer dominates the boot budget.
    // Default OFF until we have a few profiles confirming the heavy layers
    // are race-free under concurrent init.
    public static boolean parallelInitEnabled = false;

    // ==================== MODULES (BENCHMARK TOGGLES) ====================
    // Per-module on/off switches so we can measure each Stackmania module's
    // real impact (TPS, RAM, crash rate) by flipping it off and rebooting.
    // Requires restart to take effect. Default = enabled.
    //
    // Tick-hot modules (gate the hot-path entry points, not just initialize):
    public static boolean moduleTickOptimizerEnabled = true;
    public static boolean moduleAggressiveMemoryEnabled = true;
    // Init-only modules (gate initialize(), background work skipped):
    public static boolean moduleCrashRecoveryEnabled = true;
    public static boolean moduleSecurityEnabled = true;
    public static boolean moduleModLoaderBridgeEnabled = true;
    public static boolean moduleMaterialCacheEnabled = true;
    public static boolean modulePersistentPlayerEnabled = true;
    public static boolean moduleBukkitBridgeEnabled = true;
    public static boolean modulePerfectRegistryEnabled = true;
    public static boolean modulePerformanceMonitorEnabled = true;
    public static boolean moduleStackmaniaMemoryEnabled = true;
    public static boolean moduleUniversalPlatformAdapterEnabled = true;
    public static boolean moduleFabricCompatibilityEnabled = true;
    public static boolean moduleSinytraBridgeEnabled = true;

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

        // Mob cap distributor (default OFF — operator opt-in)
        moduleMobCapDistributorEnabled = getBoolean("mob_cap.enabled", false);
        mobCapPerPlayerCap = getInt("mob_cap.per_player_cap", 25);
        mobCapConsiderRangeBlocks = getInt("mob_cap.consider_range_blocks", 128);

        // Dynamic view distance (default OFF — operator opt-in)
        moduleDynamicViewDistanceEnabled = getBoolean("dynamic_view_distance.enabled", false);
        dynamicViewDistanceMax = getInt("dynamic_view_distance.max", 10);
        dynamicViewDistanceMin = getInt("dynamic_view_distance.min", 4);
        dynamicViewDistanceCheckIntervalTicks = getInt("dynamic_view_distance.check_interval_ticks", 600);

        // Parallel boot init (default OFF — needs staging validation)
        parallelInitEnabled = getBoolean("parallel_init.enabled", false);

        // Modules (benchmark toggles)
        moduleTickOptimizerEnabled = getBoolean("modules.tick_optimizer.enabled", true);
        moduleAggressiveMemoryEnabled = getBoolean("modules.aggressive_memory.enabled", true);
        moduleCrashRecoveryEnabled = getBoolean("modules.crash_recovery.enabled", true);
        moduleSecurityEnabled = getBoolean("modules.security.enabled", true);
        moduleModLoaderBridgeEnabled = getBoolean("modules.mod_loader_bridge.enabled", true);
        moduleMaterialCacheEnabled = getBoolean("modules.material_cache.enabled", true);
        modulePersistentPlayerEnabled = getBoolean("modules.persistent_player.enabled", true);
        moduleBukkitBridgeEnabled = getBoolean("modules.bukkit_bridge.enabled", true);
        modulePerfectRegistryEnabled = getBoolean("modules.perfect_registry.enabled", true);
        modulePerformanceMonitorEnabled = getBoolean("modules.performance_monitor.enabled", true);
        moduleStackmaniaMemoryEnabled = getBoolean("modules.stackmania_memory.enabled", true);
        moduleUniversalPlatformAdapterEnabled = getBoolean("modules.universal_platform_adapter.enabled", true);
        moduleFabricCompatibilityEnabled = getBoolean("modules.fabric_compatibility.enabled", true);
        moduleSinytraBridgeEnabled = getBoolean("modules.sinytra_bridge.enabled", true);
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
