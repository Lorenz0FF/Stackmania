package com.mohistmc.bukkit.pluginfix;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Mohist trap door that lets a few specific plugins write into Forge registries
 * after the freeze. The mechanism: when a plugin matching {@link #plugins} is
 * loaded, {@code canLock} is flipped to {@code false} and
 * {@code ForgeRegistry.isLocked()} (which returns {@code canLock && isFrozen})
 * starts returning false — so subsequent {@code register()} calls bypass the
 * "added too late" exception and silently mutate the Forge registry.
 *
 * <p>The problem with that mechanism: once the registry is mutated post-freeze,
 * the server's {@code Block.BLOCK_STATE_REGISTRY} (used to encode chunk packets)
 * picks up the new entries, but the snapshot already sent to connected clients
 * via the Forge handshake does not. Clients then decode chunk packets with a
 * stale id-to-state mapping and render the wrong modded blocks.
 *
 * <p>Stackmania ships with an <strong>empty</strong> plugins list, which means
 * the trap door is closed by default. The Mohist behavior is preserved as a
 * code path; to re-enable it for a specific plugin that genuinely needs late
 * registration, edit the list below and rebuild. Document the risk every time:
 * any plugin in this list creates a window for server/client registry
 * desync against any client that connects between {@code unlockRegistries} and
 * {@code lockRegistries}.
 *
 * <p>See {@code docs/REGISTRY_SYNC_FIX.md} for the full diagnosis and the
 * three concrete remediation options for operators running plugins that used
 * to rely on this trap door.
 *
 * @author Mgazul by MohistMC, defanged 2026-06-01 by Stackmania
 */
public class PluginDynamicRegistrFix {

    private static final Logger LOGGER = LogManager.getLogger("Stackmania/RegistrySync");

    public static boolean canLock = true;

    /**
     * Plugins authorised to open the post-freeze write window. Empty in
     * Stackmania — re-add a plugin name here only if you have measured that
     * the resulting registry desync risk is acceptable for your server.
     */
    private static final List<String> plugins = List.of();

    public static void unlockRegistries(Plugin plugin) {
        if (plugins.contains(plugin.getName())) {
            canLock = false;
            LOGGER.warn(
                "[trap-door] plugin {} unlocked Forge registries for late writes. "
                    + "Any client connecting before lockRegistries() will receive a "
                    + "registry snapshot that does NOT match the server's runtime table, "
                    + "and will render modded blocks incorrectly.",
                plugin.getName());
        }
    }

    public static void lockRegistries(Plugin plugin) {
        if (plugins.contains(plugin.getName())) {
            canLock = true;
            LOGGER.info("[trap-door] plugin {} re-locked Forge registries.", plugin.getName());
        }
    }
}
