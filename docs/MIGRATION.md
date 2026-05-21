# Migrating to Stackmania

This guide covers moving an existing Minecraft 1.20.1 hybrid server to Stackmania from the three most common starting points: vanilla Mohist, Arclight, and Magma.

> **Back up first.** Every section below assumes you have a working backup of `world/`, `plugins/`, `mods/`, and your config directory. Stackmania does not modify upstream files in place, but stopping mid-migration with no backup is needlessly painful.

---

## From Mohist 1.20.1

This is the cheapest migration — Stackmania *is* a Mohist fork, so most paths align.

### What to back up

```text
mohist-config/           # whole directory
plugins/                 # whole directory
mods/                    # whole directory
world/, world_nether/, world_the_end/
banned-players.json, banned-ips.json, ops.json, whitelist.json
server.properties
```

### Steps

1. Stop the Mohist server cleanly (don't `kill -9` — let it flush chunks).
2. Replace `mohist-<version>-server.jar` (or whatever you named it) with `stackmania-1.20.1-server.jar`.
3. Start the server **once** with no players connecting. Stackmania will:
   - Generate a fresh `stackmania-config/` directory with default settings.
   - **Not** read your existing `mohist-config/`. There is no automatic config porting because the schema diverges.
4. Stop the server.
5. Open `mohist-config/mohist.yml` side-by-side with `stackmania-config/stackmania.yml`. Copy across any settings you customized — most are namespaced identically.
6. Start the server again. Connect, test plugins, watch the log for boot errors.

### Common gotchas (Mohist → Stackmania)

- **`mohist-config/` is left in place.** Stackmania does not delete it. If you have a plugin that reads from `mohist-config/` directly (rare), it still works. Once you've confirmed everything migrated, you can delete it manually.
- **`plugin.yml` name change** — Stackmania's internal plugin renamed itself from `mohist` to `stackmania` in 1.1.x. If you wrote a plugin that depends on `mohist` in its `depend:` or `softdepend:` lists, change it to `stackmania`. Plugins that only check `Bukkit.getName()` or `Bukkit.getVersion()` strings need to be updated for the new identifier.
- **PAPI (PlaceholderAPI)** — works out of the box in 1.1.2+ thanks to a classloader fix. If you were on 1.1.0 or 1.1.1, you may have had to apply a workaround; remove it on upgrade.
- **ModernFix race trace at boot** — silenced in 1.1.x. If you have your own log silencer for this, you can drop it.

---

## From Arclight 1.20.1

Arclight uses a different patch base. Plugins that work on Mohist do not always work on Arclight and vice versa — expect to re-test.

### What to back up

```text
arclight.conf
plugins/, mods/, world/, server.properties, *.json
```

### Steps

1. Stop the server. Snapshot the entire server directory if you can — Arclight occasionally writes to non-obvious paths (`logs/debug/`, `crash-reports/`).
2. Move `arclight-<version>.jar` out of the directory (don't delete yet — you may need to roll back).
3. Drop in `stackmania-1.20.1-server.jar`.
4. Start the server **with no plugins or mods**:
   - Move `plugins/` to `plugins-staged/`.
   - Move `mods/` to `mods-staged/`.
   - Confirm a vanilla-config Stackmania boots cleanly.
5. Move mods back in. Restart. Watch the log for class-loading errors — Forge mod compat is the highest risk surface.
6. Move plugins back in **one group at a time** (essentials first, then perms, then game logic). Restart between groups. Plugins that worked on Arclight but not on a Bukkit-derived hybrid will surface here, and isolating which group broke is much faster.

### Common gotchas (Arclight → Stackmania)

- **Forge version mismatch.** Arclight pinned a different Forge version. If a mod hard-required Arclight's specific Forge build, it won't load on Stackmania (Forge 47.4.13).
- **Config paths.** `arclight.conf` is not portable. You'll be redoing the config from scratch.
- **Event ordering differences.** A few plugins observed event ordering quirks specific to Arclight's patch base. If a plugin's events fire in an unexpected order, check the plugin's issue tracker for "Mohist" or "CraftBukkit hybrid" notes.

---

## From Magma 1.20.1

Magma is the closest to Mohist in lineage, but the divergence has grown. Treat it more like Arclight than like Mohist.

### What to back up

```text
magma.yml (and any magma-config/ if present)
plugins/, mods/, world/, server.properties, *.json
```

### Steps

Same as Arclight. The "boot vanilla → add mods → add plugins in groups" pattern is the safe path.

### Common gotchas (Magma → Stackmania)

- **Magma's API extensions.** Magma added a handful of Bukkit API methods not present upstream. Plugins that rely on them will throw `NoSuchMethodError` on Stackmania. Stackmania's `PerfectBukkitAPI` shim covers Paper methods, not Magma extensions.
- **Performance plugin overlap.** If you were running Magma-recommended performance plugins (Spark, Pufferfish-style mods), some duplicate work that Stackmania's modules do. Disable Stackmania's `aggressive_memory` and `stackmania_memory` modules first as an experiment if you see odd memory behavior — see [BENCHMARKS.md](BENCHMARKS.md) for the matrix to actually decide which to keep.

---

## Migrating builds (1.1.x → 1.2.0, etc.)

Within the Stackmania version line:

- Stop server, swap jar, start server, done. Configs migrate automatically (new defaults are added, unknown keys are preserved, no keys are silently dropped).
- If a config key is renamed (rare), the release notes will list the rename and the old key will keep working for one minor version.
- Backup `stackmania-config/` before every minor-version bump anyway.

---

## Building from source instead

If you want to track the bleeding edge or apply your own patches, see [docs/CONTRIBUTING.md](CONTRIBUTING.md).

**Do not bump ForgeGradle without reading CONTRIBUTING.md first.** The pin to 6.0.47 is load-bearing. Several attempted bumps in 1.0.x/1.1.x broke the build in subtle ways (missing `srgify` task, Minecraft remap mismatches). The current pin is stable.
