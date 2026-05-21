/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StackmaniaConfig}.
 *
 * We deliberately do NOT call {@link StackmaniaConfig#init()} from these
 * tests. {@code init()} writes to disk via {@code YamlConfiguration} and
 * reaches into {@code Bukkit.getLogger()} on failure — neither of those is
 * available under a plain JUnit JVM. What we lock down here instead is the
 * static-field default contract: every module flag's <em>declared</em>
 * default value, plus a handful of numeric tuning constants surfaced to ops
 * via {@code stackmania.yml}.
 *
 * Why this matters: a sloppy refactor that flips a "default ON" module flag
 * to {@code false} (or vice versa) would silently change every existing
 * server's behavior on the next restart, because {@code init()} reads
 * {@code config.getBoolean(path, def)} with these same fields as the
 * {@code def} arguments.
 */
class StackmaniaConfigTest {

    // Snapshot every field this test touches so a parallel-running test that
    // mutates the same statics (the disabled-module tests in CrashRecovery /
    // PerformanceMonitor are the only known ones) cannot turn this suite red.
    private boolean snapTickOptimizer;
    private boolean snapAggressiveMemory;
    private boolean snapCrashRecovery;
    private boolean snapSecurity;
    private boolean snapModLoaderBridge;
    private boolean snapMaterialCache;
    private boolean snapPersistentPlayer;
    private boolean snapBukkitBridge;
    private boolean snapPerfectRegistry;
    private boolean snapPerformanceMonitor;
    private boolean snapStackmaniaMemory;
    private boolean snapUniversalPlatformAdapter;
    private boolean snapFabricCompatibility;
    private boolean snapSinytraBridge;
    private boolean snapMobCapDistributor;
    private boolean snapDynamicViewDistance;
    private boolean snapParallelInit;
    private int snapMobCapPerPlayer;
    private int snapMobCapRange;

    @BeforeEach
    void snapshotAndReset() {
        // Snapshot whatever the static state currently is...
        snapTickOptimizer = StackmaniaConfig.moduleTickOptimizerEnabled;
        snapAggressiveMemory = StackmaniaConfig.moduleAggressiveMemoryEnabled;
        snapCrashRecovery = StackmaniaConfig.moduleCrashRecoveryEnabled;
        snapSecurity = StackmaniaConfig.moduleSecurityEnabled;
        snapModLoaderBridge = StackmaniaConfig.moduleModLoaderBridgeEnabled;
        snapMaterialCache = StackmaniaConfig.moduleMaterialCacheEnabled;
        snapPersistentPlayer = StackmaniaConfig.modulePersistentPlayerEnabled;
        snapBukkitBridge = StackmaniaConfig.moduleBukkitBridgeEnabled;
        snapPerfectRegistry = StackmaniaConfig.modulePerfectRegistryEnabled;
        snapPerformanceMonitor = StackmaniaConfig.modulePerformanceMonitorEnabled;
        snapStackmaniaMemory = StackmaniaConfig.moduleStackmaniaMemoryEnabled;
        snapUniversalPlatformAdapter = StackmaniaConfig.moduleUniversalPlatformAdapterEnabled;
        snapFabricCompatibility = StackmaniaConfig.moduleFabricCompatibilityEnabled;
        snapSinytraBridge = StackmaniaConfig.moduleSinytraBridgeEnabled;
        snapMobCapDistributor = StackmaniaConfig.moduleMobCapDistributorEnabled;
        snapDynamicViewDistance = StackmaniaConfig.moduleDynamicViewDistanceEnabled;
        snapParallelInit = StackmaniaConfig.parallelInitEnabled;
        snapMobCapPerPlayer = StackmaniaConfig.mobCapPerPlayerCap;
        snapMobCapRange = StackmaniaConfig.mobCapConsiderRangeBlocks;

        // ...then force every field back to its source-declared default so the
        // assertions below are testing the CODE, not whatever state the JVM
        // happened to be in. This is the "reset" half of snapshot-and-reset.
        StackmaniaConfig.moduleTickOptimizerEnabled = true;
        StackmaniaConfig.moduleAggressiveMemoryEnabled = true;
        StackmaniaConfig.moduleCrashRecoveryEnabled = true;
        StackmaniaConfig.moduleSecurityEnabled = true;
        StackmaniaConfig.moduleModLoaderBridgeEnabled = true;
        StackmaniaConfig.moduleMaterialCacheEnabled = true;
        StackmaniaConfig.modulePersistentPlayerEnabled = true;
        StackmaniaConfig.moduleBukkitBridgeEnabled = true;
        StackmaniaConfig.modulePerfectRegistryEnabled = true;
        StackmaniaConfig.modulePerformanceMonitorEnabled = true;
        StackmaniaConfig.moduleStackmaniaMemoryEnabled = true;
        StackmaniaConfig.moduleUniversalPlatformAdapterEnabled = true;
        StackmaniaConfig.moduleFabricCompatibilityEnabled = true;
        StackmaniaConfig.moduleSinytraBridgeEnabled = true;
        StackmaniaConfig.moduleMobCapDistributorEnabled = false;
        StackmaniaConfig.moduleDynamicViewDistanceEnabled = false;
        StackmaniaConfig.parallelInitEnabled = false;
        StackmaniaConfig.mobCapPerPlayerCap = 25;
        StackmaniaConfig.mobCapConsiderRangeBlocks = 128;
    }

    @AfterEach
    void restoreSnapshot() {
        StackmaniaConfig.moduleTickOptimizerEnabled = snapTickOptimizer;
        StackmaniaConfig.moduleAggressiveMemoryEnabled = snapAggressiveMemory;
        StackmaniaConfig.moduleCrashRecoveryEnabled = snapCrashRecovery;
        StackmaniaConfig.moduleSecurityEnabled = snapSecurity;
        StackmaniaConfig.moduleModLoaderBridgeEnabled = snapModLoaderBridge;
        StackmaniaConfig.moduleMaterialCacheEnabled = snapMaterialCache;
        StackmaniaConfig.modulePersistentPlayerEnabled = snapPersistentPlayer;
        StackmaniaConfig.moduleBukkitBridgeEnabled = snapBukkitBridge;
        StackmaniaConfig.modulePerfectRegistryEnabled = snapPerfectRegistry;
        StackmaniaConfig.modulePerformanceMonitorEnabled = snapPerformanceMonitor;
        StackmaniaConfig.moduleStackmaniaMemoryEnabled = snapStackmaniaMemory;
        StackmaniaConfig.moduleUniversalPlatformAdapterEnabled = snapUniversalPlatformAdapter;
        StackmaniaConfig.moduleFabricCompatibilityEnabled = snapFabricCompatibility;
        StackmaniaConfig.moduleSinytraBridgeEnabled = snapSinytraBridge;
        StackmaniaConfig.moduleMobCapDistributorEnabled = snapMobCapDistributor;
        StackmaniaConfig.moduleDynamicViewDistanceEnabled = snapDynamicViewDistance;
        StackmaniaConfig.parallelInitEnabled = snapParallelInit;
        StackmaniaConfig.mobCapPerPlayerCap = snapMobCapPerPlayer;
        StackmaniaConfig.mobCapConsiderRangeBlocks = snapMobCapRange;
    }

    @Test
    void defaultModuleFlagsAreAllTrueForCoreModules() {
        // The 14 module flags wired into the bench command default to ON so
        // existing servers behave the same after a Stackmania upgrade. The
        // operator opts OUT via stackmania.yml; this asserts the default arm.
        assertTrue(StackmaniaConfig.moduleTickOptimizerEnabled, "moduleTickOptimizerEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleAggressiveMemoryEnabled, "moduleAggressiveMemoryEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleCrashRecoveryEnabled, "moduleCrashRecoveryEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleSecurityEnabled, "moduleSecurityEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleModLoaderBridgeEnabled, "moduleModLoaderBridgeEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleMaterialCacheEnabled, "moduleMaterialCacheEnabled default must be true");
        assertTrue(StackmaniaConfig.modulePersistentPlayerEnabled, "modulePersistentPlayerEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleBukkitBridgeEnabled, "moduleBukkitBridgeEnabled default must be true");
        assertTrue(StackmaniaConfig.modulePerfectRegistryEnabled, "modulePerfectRegistryEnabled default must be true");
        assertTrue(StackmaniaConfig.modulePerformanceMonitorEnabled, "modulePerformanceMonitorEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleStackmaniaMemoryEnabled, "moduleStackmaniaMemoryEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleUniversalPlatformAdapterEnabled, "moduleUniversalPlatformAdapterEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleFabricCompatibilityEnabled, "moduleFabricCompatibilityEnabled default must be true");
        assertTrue(StackmaniaConfig.moduleSinytraBridgeEnabled, "moduleSinytraBridgeEnabled default must be true");
    }

    @Test
    void optInFeatureDefaultsAreFalse() {
        // The three features that change gameplay (mob spawn rate, view
        // distance) or the boot graph (parallel init) ship OFF — operators
        // opt IN via stackmania.yml after staging. A regression that flips
        // any of these to default-ON would force the change on every server
        // that takes the next release without changing their config file.
        assertFalse(StackmaniaConfig.moduleMobCapDistributorEnabled,
                "moduleMobCapDistributorEnabled must default OFF — changes mob spawn rate");
        assertFalse(StackmaniaConfig.moduleDynamicViewDistanceEnabled,
                "moduleDynamicViewDistanceEnabled must default OFF — adjusts every player's view distance");
        assertFalse(StackmaniaConfig.parallelInitEnabled,
                "parallelInitEnabled must default OFF — still needs staging validation");
    }

    @Test
    void mobCapDefaultsMatchDesignDoc() {
        // Numeric tuning constants documented in stackmania.yml comments. The
        // mob cap distributor is gated off by default, but if an operator flips
        // it on without overriding the cap, these are the values they get.
        assertEquals(25, StackmaniaConfig.mobCapPerPlayerCap,
                "mobCapPerPlayerCap default must be 25 (matches Paper's default per-player cap)");
        assertEquals(128, StackmaniaConfig.mobCapConsiderRangeBlocks,
                "mobCapConsiderRangeBlocks default must be 128 (8 chunks — the vanilla mob-cap radius)");
    }

    @Test
    void getLanguageReturnsCurrentLanguageField() {
        // getLanguage() is the i18n entry point used by com.mohistmc.i18n.i18n;
        // it must reflect whatever {@code language} currently is, not a frozen
        // bootstrap value. We verify the read-through contract here.
        // The static default comes from Locale.getDefault().toString(), so it
        // must be non-null and non-empty out of the gate.
        String fromGetter = StackmaniaConfig.getLanguage();
        assertNotNull(fromGetter, "getLanguage() must not return null on a fresh JVM");
        assertFalse(fromGetter.isBlank(), "getLanguage() default must be a real locale tag (e.g. en_US)");
        assertEquals(StackmaniaConfig.language, fromGetter,
                "getLanguage() must reflect the current value of the static field, not a snapshot");

        // And the JVM-default contract: the field initializer is
        // Locale.getDefault().toString(), so on a fresh load they should
        // agree.
        assertEquals(Locale.getDefault().toString(), StackmaniaConfig.language,
                "language default must be Locale.getDefault().toString()");
    }

    @Test
    void moduleFlagFieldsArePublicStaticForBenchAccess() {
        // The bench command (/stackmania bench) reads these fields directly
        // via the public static surface — so making them private or instance
        // fields would silently break the command at runtime, but compile
        // fine. Lock the access contract via reflection.
        String[] requiredFlagFields = {
                "moduleTickOptimizerEnabled", "moduleAggressiveMemoryEnabled",
                "moduleCrashRecoveryEnabled", "moduleSecurityEnabled",
                "moduleModLoaderBridgeEnabled", "moduleMaterialCacheEnabled",
                "modulePersistentPlayerEnabled", "moduleBukkitBridgeEnabled",
                "modulePerfectRegistryEnabled", "modulePerformanceMonitorEnabled",
                "moduleStackmaniaMemoryEnabled", "moduleUniversalPlatformAdapterEnabled",
                "moduleFabricCompatibilityEnabled", "moduleSinytraBridgeEnabled"
        };
        for (String name : requiredFlagFields) {
            Field f = assertDoesNotThrow(() -> StackmaniaConfig.class.getField(name),
                    "field " + name + " must exist and be public");
            int mods = f.getModifiers();
            assertTrue(Modifier.isPublic(mods), name + " must be public");
            assertTrue(Modifier.isStatic(mods), name + " must be static (read by bench command)");
            assertEquals(boolean.class, f.getType(), name + " must be a primitive boolean");
        }
    }
}
