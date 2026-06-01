package com.stackmania.registrydumper;

import com.mojang.logging.LogUtils;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Registry Dumper — diagnostics-only Forge mod for MC 1.20.1 / Forge 47.4.13.
 *
 * Dumps two tables, on both server and client sides, at three lifecycle points:
 *   - server: "server_pre"  (FMLLoadCompleteEvent — registries frozen)
 *   - server: "server_post" (ServerStartedEvent  — post CraftBukkit init on Mohist)
 *   - client: "client_pre"  (FMLLoadCompleteEvent — pre-handshake snapshot)
 *   - client: "client_post" (ClientPlayerNetworkEvent.LoggingIn — post-handshake snapshot)
 *
 * Tables dumped:
 *   - net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY
 *     (the IdMapper&lt;BlockState&gt; that drives chunk-packet serialization — the smoking gun
 *     for "wrong-blockstate-on-client" bugs in hybrid Mohist setups)
 *   - net.minecraftforge.registries.ForgeRegistries.BLOCKS
 *     (the namespaced registry — useful to confirm whether the parent ID space is in sync
 *     before zooming in on per-state diffs)
 *
 * Output: ./registry-dumps/&lt;label&gt;_&lt;table&gt;.tsv relative to the JVM working directory
 * (server: server root; client: .minecraft/).
 */
@Mod(RegistryDumperMod.MODID)
public class RegistryDumperMod {

    public static final String MODID = "registrydumper";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Resolved against JVM working dir:
    //   - server: /srv/minecraft/registry-dumps/ (or pterodactyl volume root)
    //   - client: %APPDATA%/.minecraft/registry-dumps/
    private static final Path OUTPUT_DIR = Paths.get("registry-dumps");

    public RegistryDumperMod() {
        // Register on both the Forge global event bus (server lifecycle, client networking)
        // and the per-mod event bus (FML lifecycle events like FMLLoadCompleteEvent).
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        LOGGER.info("[registry-dumper] mod constructor — side={}", FMLEnvironment.dist);
    }

    /** Mod event bus — fires at the end of mod loading, before any world load. Registries are frozen. */
    @SubscribeEvent
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        String side = FMLEnvironment.dist.isClient() ? "client" : "server";
        dump(side + "_pre");
    }

    /** Forge event bus, server-only. Fires after ServerAboutToStartEvent + dimension/world init. */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        dump("server_post");
    }

    /**
     * Forge event bus, client-only. Fires after FMLHandshake completes and the client
     * has finished applying the server's registry snapshot. THIS is the table that must
     * match the server's "server_post" dump — if they diverge, blocks render wrong.
     *
     * Note: in Forge 1.20.1 47.4.x the inner class is {@code LoggingIn} (renamed from the
     * 1.19.x-era {@code LoggedInEvent}); verified against
     * src/main/java/net/minecraftforge/client/event/ClientPlayerNetworkEvent.java in the
     * parent fork.
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        dump("client_post");
    }

    // -----------------------------------------------------------------------
    // Dump implementation
    // -----------------------------------------------------------------------

    private static void dump(String label) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path outBlockState = OUTPUT_DIR.resolve(label + "_block_state_registry.tsv");
            Path outBlocks     = OUTPUT_DIR.resolve(label + "_blocks_registry.tsv");
            dumpBlockStateRegistry(outBlockState, label);
            dumpBlocksRegistry(outBlocks, label);
            LOGGER.info("[registry-dumper] dumped {} -> {} and {}", label, outBlockState, outBlocks);
        } catch (Throwable t) {
            // Diagnostics must NEVER take the JVM down. Log and move on.
            LOGGER.error("[registry-dumper] dump failed for {}: {}", label, t.toString(), t);
        }
    }

    /**
     * Dumps {@link Block#BLOCK_STATE_REGISTRY}, the IdMapper&lt;BlockState&gt; that determines
     * the integer ID assigned to each block state for network serialization (chunk packets,
     * particle packets, block-update packets). If the client and server disagree on this
     * mapping, the client will render a different block than what's actually placed.
     *
     * On 1.20.1, both BLOCK_STATE_REGISTRY (public static final field on net.minecraft.world.level.block.Block)
     * and IdMapper#size() (public method) are accessible without reflection.
     */
    private static void dumpBlockStateRegistry(Path out, String label) throws IOException {
        IdMapper<BlockState> reg = Block.BLOCK_STATE_REGISTRY;
        int size = reg.size();
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("# kind=block_state_registry"
                    + " mc=1.20.1"
                    + " forge=47.4.13"
                    + " side=" + (FMLEnvironment.dist.isClient() ? "client" : "server")
                    + " label=" + label
                    + " timestamp=" + Instant.now()
                    + " total_entries=" + size
                    + "\n");
            for (int i = 0; i < size; i++) {
                BlockState state = reg.byId(i);
                w.write(i + "\t" + (state == null ? "<null>" : state.toString()) + "\n");
            }
        }
    }

    /**
     * Dumps the iteration order of {@link ForgeRegistries#BLOCKS} keys. This is the
     * namespaced-block registry — one entry per block (not per state). It's the parent
     * ID space; if this is in sync but BLOCK_STATE_REGISTRY isn't, the desync is happening
     * inside per-block state container init, not at registry sync time.
     */
    private static void dumpBlocksRegistry(Path out, String label) throws IOException {
        var blocks = ForgeRegistries.BLOCKS;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("# kind=blocks_registry"
                    + " mc=1.20.1"
                    + " forge=47.4.13"
                    + " side=" + (FMLEnvironment.dist.isClient() ? "client" : "server")
                    + " label=" + label
                    + " timestamp=" + Instant.now()
                    + "\n");
            int i = 0;
            for (var key : blocks.getKeys()) {
                w.write(i + "\t" + key + "\n");
                i++;
            }
        }
    }
}
