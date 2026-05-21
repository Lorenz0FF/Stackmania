/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.material;

import net.minecraft.resources.ResourceLocation;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MaterialCacheManager}.
 *
 * The cache is a static singleton so we reset it between tests via the
 * provided shutdown/initialize pair. {@code Material.STONE} is used as the
 * test fixture because it is a vanilla enum value — no Bukkit runtime
 * initialization needed.
 */
class MaterialCacheManagerTest {

    @BeforeEach
    void resetCache() {
        MaterialCacheManager.shutdown();
        MaterialCacheManager.initialize();
    }

    @Test
    void getReturnsNullForUnknownName() {
        assertNull(MaterialCacheManager.get("totally_unknown_material"),
                "get() on an empty cache must return null");
    }

    @Test
    void registerThenGetReturnsTheSameMaterial() {
        Material registered = MaterialCacheManager.register(
                "test_block",
                Material.STONE,
                new ResourceLocation("stackmania", "test_block"),
                true,
                false);
        assertSame(Material.STONE, registered,
                "register() must return the same Material that was passed in for a fresh entry");
        assertSame(Material.STONE, MaterialCacheManager.get("test_block"),
                "get() must return the registered Material");
    }

    @Test
    void isRegisteredReflectsRegistrationState() {
        ResourceLocation rl = new ResourceLocation("stackmania", "iron_block");
        assertFalse(MaterialCacheManager.isRegistered("iron_block"),
                "isRegistered() must be false before register()");
        MaterialCacheManager.register("iron_block", Material.IRON_BLOCK, rl, true, true);
        assertTrue(MaterialCacheManager.isRegistered("iron_block"),
                "isRegistered() must be true after register()");
        assertTrue(MaterialCacheManager.isRegistered(rl),
                "isRegistered(ResourceLocation) must also recognize the entry");
    }

    @Test
    void duplicateRegistrationReturnsExistingEntryAndIncrementsCounter() {
        ResourceLocation rl = new ResourceLocation("stackmania", "gold_block");
        Material first = MaterialCacheManager.register("gold_block", Material.GOLD_BLOCK, rl, true, false);
        // Re-register with a *different* Material value to prove the cache wins
        // — duplicate detection must hand back the original entry rather than
        // overwrite it.
        Material second = MaterialCacheManager.register("gold_block", Material.STONE, rl, true, false);
        assertSame(first, second,
                "duplicate register() must return the previously stored Material, not the new one");

        // The "Materials: N, Duplicates Avoided: M" stats line is part of the
        // public contract used in shutdown logs.
        String stats = MaterialCacheManager.getStats();
        assertTrue(stats.contains("Duplicates Avoided: 1"),
                "duplicatesAvoided counter must have ticked once; got: " + stats);
    }

    @Test
    void registerCanUpgradeBlockOnlyEntryToBlockAndItem() {
        ResourceLocation rl = new ResourceLocation("stackmania", "dual_form");
        MaterialCacheManager.register("dual_form", Material.STONE, rl, true, false);
        // Same key, now also an item — should not increment duplicatesAvoided
        // counter back to zero, but should not lose the block flag either.
        MaterialCacheManager.register("dual_form", Material.STONE, rl, false, true);
        // We can't directly read isBlock/isItem (package-private record), so we
        // assert via the only public side-channel: size remains 1 (no duplicate
        // entry was created).
        assertEquals(1, MaterialCacheManager.size(),
                "registering with the additional 'item' flag must not create a second entry");
    }
}
