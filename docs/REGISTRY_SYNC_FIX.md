# Registry Sync Fix — diagnosis + defensive patch + operator remediation

This document records what was learned about the modded-block desync between
TWC server and the Valonia Launcher client in May–June 2026, what was patched
in the fork as a precaution, and the three concrete remediation options the
operator can apply *now* to make the bug stop happening on TWC.

This is the companion to `docs/REGISTRY_SYNC_DIAGNOSIS.md` (which describes
the runbook and the dumper tooling in `tools/registry-dumper/`).
Read `REGISTRY_SYNC_DIAGNOSIS.md` first if you have not.

---

## TL;DR

| What | Where |
|---|---|
| **Symptom** | TWC client renders modded blocks as a *different* modded block; vanilla blocks render correctly. |
| **Probable cause on TWC** | A client-only mod (e.g. `voidfog`) registers entries in a shared registry, shifting the iteration order of `ForgeRegistries.BLOCKS` on the client only. Mohist accepts the connection where Forge stock would reject it, and `Block.BLOCK_STATE_REGISTRY` ends up with mismatched id assignments. |
| **Defensive patch in this PR** | Empties the `PluginDynamicRegistrFix.plugins` allow-list so the Mohist post-freeze write trap door cannot open. Adds a stack-trace log when any code attempts a post-freeze `register()` on a Forge registry, so the offending caller is identifiable on the next boot. |
| **What this patch does NOT do** | Fix the root cause on TWC. The post-freeze trap door was *not* the active trigger on TWC (no UltraCosmetics or RealisticVillagers in the plugin set). The defensive patch removes a latent risk and surfaces the real culprit if it surfaces again. |
| **What the operator should do** | Pick one of the three remediation options below. |

---

## Investigation summary (three parallel agents)

Three READ-ONLY investigations were performed on `src/main/java/`:

### Agent A — handshake / network path

- `net.minecraftforge.network.*` is **intact** in the fork (no Mohist or
  Stackmania patches; the only `// Mohist start` marker is on
  `NetworkHooks.openScreen`, unrelated).
- `NetworkInitialization.java:55-67` registers `S2CRegistry` and
  `S2CConfigData` as login packets. `RegistryManager.generateRegistryPackets`
  (`RegistryManager.java:198`) takes a snapshot and ships it.
- `HandshakeHandler.handleRegistryMessage:303` applies the snapshot
  client-side and disconnects on mismatch (`:325`).
- **Conclusion**: the snapshot is sent. The Forge code path is honored.

### Agent B — registry freeze / snapshot / apply path

- `freezeData()` is wired declaratively via
  `ForgeStatesProvider.java:26`, executed at `ModLoadingPhase.COMPLETE` —
  before `CraftServer.loadPlugins()` runs (`DedicatedServer.java:227`).
- `ForgeInjectBukkit.addEnumMaterialsInBlocks()`
  (`src/main/java/com/mohistmc/forge/ForgeInjectBukkit.java:133-172`) **reads**
  `ForgeRegistries.BLOCKS` post-freeze and writes into the Bukkit `Material`
  enum + `CraftMagicNumbers.BLOCK_MATERIAL`. It does **not** mutate the Forge
  registry.
- **Trap door found**: `PluginDynamicRegistrFix.canLock` flips to `false`
  when a plugin matching the hard-coded list
  `["UltraCosmetics", "RealisticVillagers"]` is loaded
  (`PluginDynamicRegistrFix.java:13`), via
  `CraftServer.java:446`. While `canLock` is false,
  `ForgeRegistry.isLocked()` returns false (`ForgeRegistry.java:677`:
  `canLock && isFrozen`), so post-freeze `register()` calls bypass the
  `IllegalStateException("added too late")` guard at `:398-399`.
- **However, this trap door is INACTIVE on TWC**. TWC's plugin list
  (`LuckPerms, Vault, ProtocolLib, EssentialsX, RedProtect, TCPShield,
  CoreProtect, BlueMap, Chunky, WorldEdit, EssX chat/spawn`) contains neither
  of the two allow-listed plugin names.

### Agent C — chunk encoding path

- `Block.BLOCK_STATE_REGISTRY` is declared at `Block.java:68` and assigned
  from `GameData.getBlockStateIDMap()`. The patch
  `patches/minecraft/net/minecraft/world/level/block/Block.java.patch:11-13`
  is the stock Forge replacement (vanilla `new IdMapper<>()` → Forge slave
  map). No Stackmania or Mohist modification of this line.
- Population happens in `GameData.BlockCallbacks.onBake`
  (`GameData.java:431-447`), iterating `for (Block block : owner)` and adding
  each state to the id map. Forge stock, not patched.
- `PalettedContainer.write` uses `Block.BLOCK_STATE_REGISTRY` to serialise
  chunk packets (`LevelChunkSection.java:41`). Not patched.
- **Conclusion**: the table is passive and deterministic — it reflects
  whatever the Forge registry has at `bake()` time. If server and client
  diverge, the bug is in the **registry iteration order**, not in the
  encoding path.

### Convergent verdict

The combination of A + B + C points at **case 2 (snapshot effectively
ignored)** as the most likely cause on TWC — not case 3 (runtime mutation).
On TWC the trap door is closed, so the server cannot be mutating its registry
post-freeze without throwing. What can still produce a divergent registry on
the client side:

- A **client-only mod registering entries in a shared registry**. The
  Forge handshake message `Client has mods that are missing on server:
  [voidfog]` is exactly that signal. On Forge stock the client would be
  disconnected at registry-mismatch time; Mohist's hybrid login accepts the
  connection and the client keeps its own extra entries.
- Any third-party tool intercepting `ClientboundLevelChunkWithLightPacket`
  (ProtocolLib, etc.) and not rewriting the palette correctly.
- A Forge mod registering blocks during `RegisterEvent` *after* one side has
  already snapshot — only possible if the trap door is open, which it is
  not here.

---

## Defensive patch in this PR

Two narrow changes:

### 1. `PluginDynamicRegistrFix.plugins` → empty list

```diff
-    private static final List<String> plugins = List.of("UltraCosmetics", "RealisticVillagers");
+    private static final List<String> plugins = List.of();
```

Closes the trap door by default. TWC was not using it; future Stackmania
servers that ship a plugin needing the late-write window can re-add the name
here and rebuild — at which point they have to *measure* the desync risk
because every `unlockRegistries` call now logs a WARN with the plugin name.

### 2. `ForgeRegistry.register()` post-freeze logs a stack trace before throwing

```diff
-        if (isLocked())
-            throw new IllegalStateException(...);
+        if (isLocked()) {
+            LOGGER.error(REGISTRIES, "[registry-sync] POST-FREEZE WRITE BLOCKED ...",
+                this.name, value, key,
+                new Throwable("post-freeze register call site"));
+            throw new IllegalStateException(...);
+        }
```

The throw is preserved; the log is purely informational. If anything *ever*
tries to register a Forge entry after the freeze, we get the offending stack
trace in the latest.log without having to deploy the
`tools/registry-dumper/` mod to bisect.

### What is intentionally NOT patched

- `HandshakeHandler`, `ConfigSync`, `RegistryManager`, `NetworkHooks`,
  `GameData.injectSnapshot` — all intact upstream Forge code, verified by
  agent A. Patching them speculatively would create a divergence from Forge
  without a measured cause.
- The encoding side (`PalettedContainer`, `LevelChunkSection`,
  `Block.BLOCK_STATE_REGISTRY` population) — passive code path, verified by
  agent C.
- The chunk packet itself.

---

## Three concrete remediation options for the operator

These are the three things to try, in increasing order of disruption to the
production server. Stop at the first one that fixes it.

### Option A — Add `voidfog` to the server's mod set (LEAST INVASIVE)

`voidfog` is described as a client-side fog mod. Many such mods register at
least one `EntityType` or `Block` for side-of-the-river reasons. If you
simply place the same `voidfog-*.jar` from the client's
`mods/` folder into the server's `mods/` folder:

```bash
# Replace <voidfog-version> with whatever is on the client
scp ~/path/to/voidfog-<voidfog-version>.jar \
    pterodactyl@server:/var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/mods/
ssh pterodactyl@server "chown pterodactyl:pterodactyl /var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/mods/voidfog-<voidfog-version>.jar"
```

The mod will load on the server, see `Dist.DEDICATED_SERVER`, and either
no-op or register the same entries the client does — same iteration order on
both sides, registry aligned, blockstate ids match. **Restart the server**
and connect a test client; if modded blocks render correctly, this was the
fix. Total downtime: one restart.

This option matches the user's standing rule — **no mod is removed**, one
mod is *added*, matching the client.

### Option B — Run the Phase 0 instrumentation already shipped in PR #29

If Option A doesn't fix it, deploy
`tools/registry-dumper/` to both sides, capture the four TSVs,
run `tools/registry-sync/diff_registries.py`, and read the exact
`first_mismatch` line plus the `per_mod` breakdown. Five-minute diagnosis
from concrete data instead of three more rounds of investigation.

```bash
# Build (anywhere with JDK 17)
cd /opt/Stackmania/tools/registry-dumper/
./gradlew build

# Deploy both sides (see docs/REGISTRY_SYNC_DIAGNOSIS.md for the exact paths)
# Restart, connect, collect, diff:
python3 tools/registry-sync/diff_registries.py \
  <server>/registry-dumps/server_post_block_state_registry.tsv \
  <client>/registry-dumps/client_post_block_state_registry.tsv
```

The verdict line (`case_1_*` / `case_2_*` / `case_3_*`) plus the per-mod
breakdown points at exactly which mod or namespace is responsible.

### Option C — Migrate the bridge layer (LAST RESORT)

If Options A and B both prove that the bug is intrinsic to the Mohist
Forge↔Bukkit bridge for hybrid setups with mod-set divergences, the longer
path is to migrate to an alternative bridge: **Arclight** or **Ketting**.
Both projects implement the Forge↔Bukkit bridge with mixin-based patches
instead of source patches, and they handle the registry sync differently —
in particular, they are less permissive about client-only mods at handshake
time.

Cost realistically: 2–4 weeks. Risks listed in
`docs/REGISTRY_SYNC_DIAGNOSIS.md` section 9.

---

## What changes for plugin developers using the Mohist trap door

If a plugin in Stackmania 1.2.0+ was relying on the
`PluginDynamicRegistrFix.canLock` window to register Forge content from a
Bukkit plugin (rare but possible — UltraCosmetics and RealisticVillagers
were the documented cases), the post-freeze `register()` call will now throw
`IllegalStateException` *and* log a stack trace pointing at the plugin.

To restore the previous behavior for that one plugin only, add its name to
`PluginDynamicRegistrFix.plugins`:

```java
private static final List<String> plugins = List.of("MyPlugin");
```

…and rebuild. Every `unlockRegistries(plugin)` call will now log a WARN
explaining the desync risk for any client connecting during the open window.
The intent is to make the trap door **opt-in and observable**, not
silent-by-default.

---

## References

- Diagnosis runbook: `docs/REGISTRY_SYNC_DIAGNOSIS.md`
- Instrumentation tooling: `tools/registry-dumper/`, `tools/registry-sync/`
- The original handshake flow: `net.minecraftforge.network.{HandshakeHandler, ConfigSync, NetworkInitialization}` (Forge stock, unpatched in this fork)
- The trap door class: `src/main/java/com/mohistmc/bukkit/pluginfix/PluginDynamicRegistrFix.java`
- The lock guard: `src/main/java/net/minecraftforge/registries/ForgeRegistry.java:677`
- The throw site (now with stack-trace log): `src/main/java/net/minecraftforge/registries/ForgeRegistry.java:398`
