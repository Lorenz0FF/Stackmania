/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.security;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StackmaniaSecurityManager}.
 *
 * The security manager keeps two static sets: blocked operations (populated
 * once in its {@code static} initializer) and trusted sources (mutable). The
 * blocked set is what we verify here — its contents are the durable contract.
 */
class StackmaniaSecurityManagerTest {

    @Test
    void blocksPluginHotloadOperation() {
        // The four hot-* operations are blocked unconditionally — they are the
        // reason Stackmania removed Mohist's plugin-hot-loading subsystem.
        assertFalse(StackmaniaSecurityManager.isOperationAllowed("plugin.hotload"),
                "plugin.hotload must be blocked");
        assertFalse(StackmaniaSecurityManager.isOperationAllowed("plugin.hotunload"),
                "plugin.hotunload must be blocked");
        assertFalse(StackmaniaSecurityManager.isOperationAllowed("plugin.hotreplacement"),
                "plugin.hotreplacement must be blocked");
    }

    @Test
    void blocksRuntimeCodeDownloadAndJarModification() {
        assertFalse(StackmaniaSecurityManager.isOperationAllowed("code.download"),
                "code.download must be blocked");
        assertFalse(StackmaniaSecurityManager.isOperationAllowed("jar.modification"),
                "jar.modification must be blocked");
    }

    @Test
    void allowsUnknownOperationsByDefault() {
        // The allowlist is implicit — anything not in the blocklist passes.
        // This is intentional so the security manager doesn't become a chokepoint
        // that requires touching every Bukkit API call.
        assertTrue(StackmaniaSecurityManager.isOperationAllowed("plugin.load"),
                "plugin.load (the non-hot variant) must be allowed");
        assertTrue(StackmaniaSecurityManager.isOperationAllowed("anything.else"),
                "unknown operations must default to allowed");
        assertTrue(StackmaniaSecurityManager.isOperationAllowed(""),
                "empty operation name must default to allowed (defensive: never block on a bad input)");
    }

    @Test
    void runtimePluginOperationsAreNeverAllowed() {
        // This one is the public contract used by the rest of the codebase.
        assertFalse(StackmaniaSecurityManager.isRuntimePluginOperationAllowed(),
                "runtime plugin operations must always be denied");
    }

    @Test
    void validatePluginRejectsNonExistentFiles() {
        File phantom = new File("does-not-exist-" + System.nanoTime() + ".jar");
        assertFalse(StackmaniaSecurityManager.validatePlugin(phantom),
                "a file that does not exist on disk must not validate");
    }

    @Test
    void validatePluginRejectsNonJarExtensions() {
        // Create a real temp file with the wrong extension so the existence
        // check passes and we land on the extension check.
        try {
            File tmp = File.createTempFile("not-a-jar", ".txt");
            tmp.deleteOnExit();
            assertFalse(StackmaniaSecurityManager.validatePlugin(tmp),
                    "a file with a non-.jar extension must not validate");
        } catch (Exception e) {
            fail("could not create temp file: " + e.getMessage());
        }
    }
}
