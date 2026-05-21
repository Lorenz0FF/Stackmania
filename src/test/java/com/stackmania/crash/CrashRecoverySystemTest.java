/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.crash;

import com.stackmania.core.StackmaniaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CrashRecoverySystem}.
 *
 * The system is a static singleton gated by
 * {@link StackmaniaConfig#moduleCrashRecoveryEnabled}. Each test resets the
 * singleton via {@link CrashRecoverySystem#shutdown()} both before and after so
 * the {@code initialized} flag and {@code instance} reference do not leak
 * across the suite (Gradle runs all tests in one JVM by default).
 *
 * We deliberately stay away from the watchdog threads themselves — they
 * schedule background work that races with whatever the next test does. The
 * surface we exercise here is initialize/getInstance/shutdown, which is what
 * the boot sequence and {@code /stackmania bench} both actually call.
 */
class CrashRecoverySystemTest {

    private boolean originalEnabledFlag;

    @BeforeEach
    void snapshotFlagAndResetSingleton() {
        originalEnabledFlag = StackmaniaConfig.moduleCrashRecoveryEnabled;
        // Make sure each test starts from "not initialized". shutdown() flips
        // the initialized flag back to false and nulls out the instance, so
        // calling it on a fresh JVM (instance == null) is also a no-op.
        CrashRecoverySystem.shutdown();
    }

    @AfterEach
    void restoreFlagAndResetSingleton() {
        StackmaniaConfig.moduleCrashRecoveryEnabled = originalEnabledFlag;
        // Leave the singleton in the "off" state for any subsequent test class
        // so its watchdog executors do not keep ticking in the background.
        CrashRecoverySystem.shutdown();
    }

    @Test
    void getInstanceThrowsWhenUninitialized() {
        // Pre-condition: shutdown() in @BeforeEach put the system back to the
        // pristine state. getInstance() must surface that as IllegalStateException
        // rather than handing out a stale or null reference.
        assertThrows(IllegalStateException.class, CrashRecoverySystem::getInstance,
                "getInstance() before initialize() must throw IllegalStateException");
    }

    @Test
    void initializeIsIdempotent() {
        // Flag stays at its default (true) so initialize() walks the
        // "create instance + start watchdogs" path. A second initialize() call
        // must not replace the instance — that would leak the first set of
        // watchdog threads.
        CrashRecoverySystem.initialize();
        CrashRecoverySystem first = CrashRecoverySystem.getInstance();
        assertNotNull(first, "first initialize() must produce a non-null instance");

        CrashRecoverySystem.initialize();
        CrashRecoverySystem second = CrashRecoverySystem.getInstance();
        assertSame(first, second,
                "second initialize() must not replace the singleton — that would leak watchdog threads");
    }

    @Test
    void disabledModuleSkipsInstanceCreation() {
        // Flip the bench toggle off BEFORE initialize() so the gate fires.
        // The contract: the module marks itself initialized so subsequent
        // initialize() calls are no-ops, but the instance reference stays null.
        // getInstance() therefore does NOT throw (initialized == true) but
        // returns null — callers downstream must handle that.
        StackmaniaConfig.moduleCrashRecoveryEnabled = false;
        CrashRecoverySystem.initialize();

        assertNull(CrashRecoverySystem.getInstance(),
                "when module is disabled, getInstance() must return null (no watchdog threads to expose)");
    }

    @Test
    void shutdownIsSafeBeforeInitialize() {
        // shutdown() must tolerate being called on a never-initialized system —
        // this happens when the JVM aborts boot between layer construction and
        // the layer's initialize() call, and the cleanup hook still fires.
        assertDoesNotThrow(CrashRecoverySystem::shutdown,
                "shutdown() before initialize() must not throw");
        // A second shutdown() must also be inert.
        assertDoesNotThrow(CrashRecoverySystem::shutdown,
                "shutdown() called twice in a row must remain inert");
    }
}
