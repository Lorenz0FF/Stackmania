package com.mohistmc;

import com.mohistmc.eventhandler.EventDispatcherRegistry;
import com.mohistmc.i18n.i18n;
import com.mohistmc.plugins.MohistProxySelector;
import com.mohistmc.util.VersionInfo;
import com.stackmania.core.StackmaniaConfig;
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
import java.util.concurrent.CompletableFuture;
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

    /**
     * Runs {@code task} and logs how long it took on a single line. Failures
     * are caught and logged at ERROR level so one broken layer does not
     * cancel the whole boot — the caller's loop continues with the next
     * layer. Used by both the sequential and the parallel init paths so the
     * timing data is comparable.
     */
    private static void timed(String label, Runnable task) {
        long start = System.currentTimeMillis();
        try {
            task.run();
            LOGGER.info("{}  initialized in {} ms", label, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            LOGGER.error("{} FAILED after {} ms: {}", label, System.currentTimeMillis() - start, t.toString(), t);
        }
    }

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
     * Initialize the 12 Stackmania layers.
     *
     * Layers 1-4 (Security, ModLoader bridge, Bukkit API, Registry) always
     * run sequentially because later layers reference them. Layers 5-12
     * (Crash, Performance, Memory*2, Tick, Platform, Fabric, Sinytra) are
     * leaf modules that mostly spin up background daemons; they can run
     * concurrently when {@code parallel_init.enabled} is true in
     * stackmania.yml.
     *
     * Per-layer timing is always logged so an operator can decide which
     * heavy layer is worth parallelizing on their hardware.
     */
    private void initializeStackmaniaLayers() {
        if (stackmaniaInitialized) return;

        long startTime = System.currentTimeMillis();
        LOGGER.info("Initializing Stackmania 12-Layer Architecture (parallel_init={})",
                StackmaniaConfig.parallelInitEnabled);

        // ---- Sequential foundation (layers 1-4) ----------------------------
        timed("[Layer 1/12] SecurityManager",          StackmaniaSecurityManager::initialize);
        timed("[Layer 2/12] ModLoaderBridge",          ModLoaderBridge::initialize);
        timed("[Layer 3/12] MaterialCacheManager",     MaterialCacheManager::initialize);
        timed("[Layer 3/12] PersistentPlayerManager",  PersistentPlayerManager::initialize);
        timed("[Layer 3/12] StackmaniaBukkitBridge",   StackmaniaBukkitBridge::initialize);
        timed("[Layer 4/12] PerfectRegistryManager",   PerfectRegistryManager::initialize);

        // ---- Heavy leaf modules (layers 5-12) ------------------------------
        Runnable[] heavy = {
                () -> timed("[Layer 5/12] CrashRecoverySystem",      CrashRecoverySystem::initialize),
                () -> timed("[Layer 6/12] PerformanceMonitor",       PerformanceMonitor::initialize),
                () -> timed("[Layer 7/12] StackmaniaMemoryManager",  StackmaniaMemoryManager::initialize),
                () -> timed("[Layer 8/12] AggressiveMemoryOptimizer",AggressiveMemoryOptimizer::initialize),
                () -> timed("[Layer 9/12] StackmaniaTickOptimizer",  StackmaniaTickOptimizer::initialize),
                () -> timed("[Layer 10/12] UniversalPlatformAdapter",UniversalPlatformAdapter::initialize),
                () -> timed("[Layer 11/12] FabricCompatibilityLayer",FabricCompatibilityLayer::initialize),
                () -> timed("[Layer 12/12] SinytraConnectorBridge",  SinytraConnectorBridge::initialize),
        };

        if (StackmaniaConfig.parallelInitEnabled) {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[heavy.length];
            for (int i = 0; i < heavy.length; i++) {
                futures[i] = CompletableFuture.runAsync(heavy[i]);
            }
            // Block until every heavy layer is done. We need them all initialized
            // before the Forge mod-construct phase ends; doing this asynchronously
            // would break callers that rely on getInstance() being non-throwing
            // immediately after the constructor returns.
            CompletableFuture.allOf(futures).join();
        } else {
            for (Runnable r : heavy) r.run();
        }

        stackmaniaInitialized = true;
        LOGGER.info("All 12 Stackmania layers initialized in {}ms", System.currentTimeMillis() - startTime);
        LOGGER.info("═══════════════════════════════════════════════════════════");
        LOGGER.info("Stackmania ready. Toggle modules in stackmania-config/stackmania.yml");
        LOGGER.info("Run /stackmania bench status to inspect module state at runtime");
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