package com.mohistmc;

import com.mohistmc.eventhandler.EventDispatcherRegistry;
import com.mohistmc.i18n.i18n;
import com.mohistmc.plugins.MohistProxySelector;
import com.mohistmc.util.VersionInfo;
import com.stackmania.compatibility.ModernFixRaceSilencer;
import com.stackmania.compatibility.ModLoaderBridge;
import com.stackmania.crash.CrashRecoverySystem;
import com.stackmania.performance.PerformanceMonitor;
import com.stackmania.bukkit.StackmaniaBukkitBridge;
import com.stackmania.registry.PerfectRegistryManager;
import com.stackmania.material.MaterialCacheManager;
import com.stackmania.player.PersistentPlayerManager;
import com.stackmania.security.StackmaniaSecurityManager;
import com.stackmania.memory.StackmaniaMemoryManager;
import com.stackmania.memory.AggressiveMemoryOptimizer;
import com.stackmania.optimization.StackmaniaTickOptimizer;
import com.stackmania.compatibility.UniversalPlatformAdapter;
import com.stackmania.compatibility.FabricCompatibilityLayer;
import com.stackmania.compatibility.SinytraConnectorBridge;
import java.net.ProxySelector;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

@Mod("stackmania")
@OnlyIn(Dist.DEDICATED_SERVER)
public class MohistMC {
    public static final String NAME = "Stackmania";
    public static Logger LOGGER = LogManager.getLogger();
    public static i18n i18n;
    public static String version = "1.20.1";
    public static String modid = "stackmania";
    // Initialized at class-load time, not in the @Mod constructor, so Bukkit
    // plugin loading (PluginClassLoader.java:108) can never NPE on a plugin
    // that is instantiated before Forge invokes our @Mod constructor.
    // This is the root cause of "MohistMC.classLoader is null" in PAPI,
    // OneAC, Spartan AntiCheat and TAB plugin on stock Mohist.
    public static ClassLoader classLoader = MohistMC.class.getClassLoader();
    public static VersionInfo versionInfo;

    private static boolean stackmaniaInitialized = false;

    static {
        // Install the silencer for embeddedt/ModernFix#632 at class-load time,
        // i.e. earlier than any @Mod constructor including ModernFix's. This
        // gives us the best chance of catching the NightConfig FileWatcher
        // daemon thread on its first iteration. Safe even when ModernFix is
        // not installed — the silencer just stays armed and never fires.
        ModernFixRaceSilencer.install();
    }

    public MohistMC() {

        LOGGER.info("╔══════════════════════════════════════════════════════════╗");
        LOGGER.info("║              STACKMANIA SERVER LOADING                    ║");
        LOGGER.info("║          Optimized Forge + Bukkit Hybrid Server          ║");
        LOGGER.info("║                   by Valonia Games                        ║");
        LOGGER.info("╚══════════════════════════════════════════════════════════╝");
        
        // Initialize Stackmania layers in order
        initializeStackmaniaLayers();
        
        EventDispatcherRegistry.init();
        ProxySelector.setDefault(new MohistProxySelector(ProxySelector.getDefault()));
    }
    
    /**
     * Initialize all 6 Stackmania architectural layers
     */
    private void initializeStackmaniaLayers() {
        if (stackmaniaInitialized) return;
        
        long startTime = System.currentTimeMillis();
        LOGGER.info("Initializing Stackmania 6-Layer Architecture...");
        
        // Layer 1: Security (MUST be first)
        LOGGER.info("[Layer 1/6] Initializing Security Manager...");
        StackmaniaSecurityManager.initialize();
        
        // Layer 2: Universal Compatibility Layer
        LOGGER.info("[Layer 2/6] Initializing Universal Compatibility Layer...");
        ModLoaderBridge.initialize();
        
        // Layer 3: Perfect Bukkit API
        LOGGER.info("[Layer 3/6] Initializing Perfect Bukkit API...");
        MaterialCacheManager.initialize();
        PersistentPlayerManager.initialize();
        StackmaniaBukkitBridge.initialize();
        
        // Layer 4: Perfect Registry System
        LOGGER.info("[Layer 4/6] Initializing Perfect Registry System...");
        PerfectRegistryManager.initialize();
        
        // Layer 5: Zero-Crash System
        LOGGER.info("[Layer 5/6] Initializing Zero-Crash System...");
        CrashRecoverySystem.initialize();
        
        // Layer 6: Performance Perfection
        LOGGER.info("[Layer 6/6] Initializing Performance Perfection Layer...");
        PerformanceMonitor.initialize();
        
        // Layer 7: Memory Optimization (CRITICAL)
        LOGGER.info("[Layer 7/8] Initializing Memory Optimization...");
        StackmaniaMemoryManager.initialize();
        
        // Layer 8: Aggressive Memory Optimizer (45% RAM reduction target)
        LOGGER.info("[Layer 8/9] Initializing Aggressive Memory Optimizer...");
        AggressiveMemoryOptimizer.initialize();
        
        // Layer 9: Tick Optimizer (TPS 20 stable)
        LOGGER.info("[Layer 9/10] Initializing Tick Optimizer...");
        StackmaniaTickOptimizer.initialize();
        
        // Layer 10: Universal Platform Adapter (100% compatibility)
        LOGGER.info("[Layer 10/11] Initializing Universal Platform Adapter...");
        UniversalPlatformAdapter.initialize();
        
        // Layer 11: Fabric Compatibility (Fallback)
        LOGGER.info("[Layer 11/12] Initializing Fabric Compatibility Layer...");
        FabricCompatibilityLayer.initialize();
        
        // Layer 12: Sinytra Connector Bridge (REAL Fabric support)
        LOGGER.info("[Layer 12/12] Initializing Sinytra Connector Bridge...");
        SinytraConnectorBridge.initialize();
        
        stackmaniaInitialized = true;
        LOGGER.info("All 12 Stackmania layers initialized in {}ms", System.currentTimeMillis() - startTime);
        LOGGER.info("═══════════════════════════════════════════════════════════");
        LOGGER.info("Target: TPS=20.0 | GC<5ms | Crash=0% | RAM -45% | Compat=100%");
        LOGGER.info("═══════════════════════════════════════════════════════════");
    }
    
    /**
     * Shutdown all Stackmania layers (called on server stop)
     */
    public static void shutdownStackmania() {
        if (!stackmaniaInitialized) return;
        
        LOGGER.info("Shutting down Stackmania layers...");
        
        // Shutdown in reverse order
        SinytraConnectorBridge.shutdown();
        FabricCompatibilityLayer.shutdown();
        UniversalPlatformAdapter.shutdown();
        StackmaniaTickOptimizer.shutdown();
        AggressiveMemoryOptimizer.shutdown();
        StackmaniaMemoryManager.shutdown();
        PerformanceMonitor.shutdown();
        CrashRecoverySystem.shutdown();
        PerfectRegistryManager.shutdown();
        StackmaniaBukkitBridge.shutdown();
        PersistentPlayerManager.shutdown();
        MaterialCacheManager.shutdown();
        ModLoaderBridge.shutdown();
        StackmaniaSecurityManager.shutdown();
        
        stackmaniaInitialized = false;
        LOGGER.info("Stackmania shutdown complete.");
    }

    public static void initVersion() {
        String mohist_lang = MohistConfig.yml.getString("mohist.lang", Locale.getDefault().toString());
        i18n = new i18n(MohistMC.class.getClassLoader(), mohist_lang);

        Map<String, String> arguments = new HashMap<>();
        String[] cbs = CraftServer.class.getPackage().getImplementationVersion().split("-");
        arguments.put("mohist", (MohistMC.class.getPackage().getImplementationVersion() != null) ? MohistMC.class.getPackage().getImplementationVersion() : version);
        arguments.put("bukkit", cbs[0]);
        arguments.put("craftbukkit", cbs[1]);
        arguments.put("spigot", cbs[2]);
        arguments.put("neoforge", cbs[3]);
        arguments.put("forge", ForgeVersion.getVersion());
        versionInfo = new VersionInfo(arguments);
    }
}