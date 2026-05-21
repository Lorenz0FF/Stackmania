/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.performance;

import com.stackmania.core.StackmaniaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PerformanceMonitor}.
 *
 * Same shape as {@code CrashRecoverySystemTest}: a static singleton gated by
 * a {@link StackmaniaConfig} flag, plus a public flag array
 * ({@link PerformanceMonitor#RECOMMENDED_JVM_FLAGS}) that the bench command
 * surfaces verbatim — so we lock its contract here too.
 *
 * We do not start nor inspect the TPS / GC / memory monitor threads themselves
 * — they live on their own daemon executors and racing with them from a unit
 * test is not worth the flake budget. The contract we exercise here is the one
 * the boot sequence and {@code /stackmania bench} actually depend on.
 */
class PerformanceMonitorTest {

    private boolean originalEnabledFlag;

    @BeforeEach
    void snapshotFlagAndResetSingleton() {
        originalEnabledFlag = StackmaniaConfig.modulePerformanceMonitorEnabled;
        PerformanceMonitor.shutdown();
    }

    @AfterEach
    void restoreFlagAndResetSingleton() {
        StackmaniaConfig.modulePerformanceMonitorEnabled = originalEnabledFlag;
        // Leave the singleton off so monitor threads do not keep firing under
        // unrelated tests.
        PerformanceMonitor.shutdown();
    }

    @Test
    void initializeIsIdempotent() {
        PerformanceMonitor.initialize();
        PerformanceMonitor first = PerformanceMonitor.getInstance();
        assertNotNull(first, "first initialize() must produce a non-null instance");

        PerformanceMonitor.initialize();
        PerformanceMonitor second = PerformanceMonitor.getInstance();
        assertSame(first, second,
                "second initialize() must not replace the singleton — that would leak monitor threads");
    }

    @Test
    void disabledModuleSkipsInstanceCreation() {
        // The bench gate marks the layer initialized but never builds the
        // PerformanceMonitor instance, so getInstance() returns null instead of
        // throwing. Mirrors CrashRecoverySystem's behavior — keeping these two
        // gates symmetrical matters for the /stackmania bench JSON dump.
        StackmaniaConfig.modulePerformanceMonitorEnabled = false;
        PerformanceMonitor.initialize();

        assertNull(PerformanceMonitor.getInstance(),
                "when module is disabled, getInstance() must return null");
    }

    @Test
    void recommendedJvmFlagsArrayIsNotEmpty() {
        // RECOMMENDED_JVM_FLAGS is documentation-as-data: the bench command
        // prints it as the canonical "aikars-style" set we suggest to ops.
        // The exact list will drift as JVM tuning evolves, but the array must
        // (a) exist with at least the G1GC core flags and (b) never contain
        // null or blank entries — the bench formatter joins them with " " and
        // a null mid-array would produce garbled output.
        String[] flags = PerformanceMonitor.RECOMMENDED_JVM_FLAGS;
        assertNotNull(flags, "RECOMMENDED_JVM_FLAGS must not be null");
        assertTrue(flags.length >= 5,
                "RECOMMENDED_JVM_FLAGS must surface at least 5 flags; got " + flags.length);

        for (int i = 0; i < flags.length; i++) {
            String f = flags[i];
            assertNotNull(f, "RECOMMENDED_JVM_FLAGS[" + i + "] is null — bench output would break");
            assertFalse(f.isBlank(),
                    "RECOMMENDED_JVM_FLAGS[" + i + "] is blank — bench output would break");
        }

        // G1GC is the spine of the recommended set — losing it silently would
        // be a regression worth catching. Any future re-tuning that removes
        // G1GC needs to update this test deliberately.
        boolean hasG1GC = false;
        for (String f : flags) {
            if (f.contains("UseG1GC")) { hasG1GC = true; break; }
        }
        assertTrue(hasG1GC, "RECOMMENDED_JVM_FLAGS must include -XX:+UseG1GC");
    }
}
