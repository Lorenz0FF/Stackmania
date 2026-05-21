/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.compatibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Silencer for the embeddedt/ModernFix#632 race condition.
 *
 * Symptom: on boot, a daemon thread (typically named "Thread-0") owned by
 * NightConfig's FileWatcher prints a NoClassDefFoundError or
 * ConcurrentModificationException stack trace pointing at
 * org.embeddedt.modernfix.forge.config.NightConfigWatchThrottler$1$1 and
 * NightConfigWatchThrottler$1.values(...).
 *
 * Root cause (per the ModernFix maintainer @embeddedt on the upstream issue):
 * a race between the FileWatcher daemon and Forge ModLauncher's lazy class
 * loading. The maintainer has acknowledged the bug, classifies it as
 * "relatively harmless" (no crash, no log spam during runtime), and notes
 * that the NightConfig hack was removed entirely in 1.21.1+ where this can't
 * happen any more.
 *
 * We can't fix the race on our side without patching the upstream mod, but
 * we can stop the stack trace from polluting our boot log by installing a
 * default UncaughtExceptionHandler that recognizes this exact pattern,
 * downgrades it to a single DEBUG line, and delegates everything else to
 * the previous handler. The match is strict (class name in the message
 * chain), so we cannot accidentally swallow an unrelated bug.
 */
public final class ModernFixRaceSilencer {

    private static final Logger LOGGER = LogManager.getLogger("Stackmania/ModernFixSilencer");

    private static final String MARKER_OUTER =
            "org.embeddedt.modernfix.forge.config.NightConfigWatchThrottler$1";
    private static final String MARKER_INNER =
            "org.embeddedt.modernfix.forge.config.NightConfigWatchThrottler$1$1";

    private static volatile boolean installed = false;

    private ModernFixRaceSilencer() {}

    /**
     * Idempotent — installs the silencer at most once per JVM. Safe to call
     * from any thread and at any point during boot. The earlier it runs, the
     * higher the chance of catching the FileWatcher daemon on its first
     * iteration.
     */
    public static synchronized void install() {
        if (installed) return;

        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (isKnownModernFixRace(throwable)) {
                LOGGER.debug(
                        "Suppressed known race condition (embeddedt/ModernFix#632) on thread \"{}\": {}",
                        thread.getName(),
                        throwable.toString());
                return;
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                // Mirror the JVM default behavior so we never silently swallow
                // anything we don't explicitly recognize.
                System.err.print("Exception in thread \"" + thread.getName() + "\" ");
                throwable.printStackTrace(System.err);
            }
        });

        installed = true;
        LOGGER.info(
                "Installed silencer for ModernFix NightConfigWatchThrottler race (embeddedt/ModernFix#632)");
    }

    /**
     * Walks the cause chain and returns true if any layer's message mentions
     * the inner classes of NightConfigWatchThrottler. Conservative on purpose:
     * we want to suppress only the exact symptom @embeddedt described as
     * harmless, not anything that happens to involve ModernFix in general.
     */
    private static boolean isKnownModernFixRace(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg != null && (msg.contains(MARKER_INNER) || msg.contains(MARKER_OUTER))) {
                return true;
            }
        }
        return false;
    }
}
