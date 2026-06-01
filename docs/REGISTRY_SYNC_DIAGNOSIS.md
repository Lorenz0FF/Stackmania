# Registry sync diagnosis — Mohist 1.20.1 + Forge 47.4.13 blockstate desync

Status: **Phase 0 — instrumentation in progress.** Verdict pending real
TSV captures from a production restart.

Branch: `fix/registry-sync-instrumentation`
Repository: this one (`Lorenz0FF/Stackmania`)
Target deployment: TWC (`/opt/Stackmania` on the production node,
Pterodactyl volume `cf1e29e8-ed9c-429e-bc89-dea74703cb00`)

This document is the single source of truth for the registry-sync
investigation. It tells the operator **what to do, in what order, on
which file paths**, and what each verdict from the diff tool means for
the next step.

---

## 1. Symptom

On TWC (Stackmania 1.1.2 = Mohist 1.20.1 + Forge 47.4.13, ~209 mods +
Bukkit plugins):

- **Vanilla blocks** placed by the server display correctly on the
  client.
- **Modded blocks** placed by the server display on the client as a
  **different modded block** — wrong texture, wrong shape, wrong
  tooltip, but not "missing block" / `air`. It's a *mis-pointed* render,
  not an absent one.

Players see this immediately on entering any modded base. It is **not**
random and **not** intermittent — for a given server build, the
mis-mapping is deterministic and stable across reconnects.

## 2. Diagnostic confirmed (do not re-demonstrate)

Mechanism: every modded chunk packet
(`ClientboundLevelChunkWithLightPacket`) encodes the block in the chunk
section as an **integer id** that indexes into the table
`net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY` (an
`IdMapper<BlockState>`). The client decodes those ids through **its own
copy** of the same table.

For this to work, the two tables must agree byte-for-byte on every
`(id → blockstate)` pair.

- On a **pure Forge** server, the handshake aborts the client connection
  if the registry snapshot doesn't apply cleanly. This is *why* pure
  Forge clients can rely on the table the server holds.
- On **Mohist** (a Forge + Bukkit hybrid), the handshake either fails
  to push the Forge registry snapshot, fails to apply it on one of the
  sides, or applies it but the **runtime table** later used to encode
  packets has drifted from what was synced. **The handshake does not
  reject the client**, so the mismatch is invisible at connect time and
  only manifests as "wrong block textures" once chunks start flowing.

The vanilla section stays aligned because vanilla blocks are registered
at the very start of the table by the vanilla `Bootstrap` code, before
any mod adds anything. The shift accumulates in the modded segment of
the table.

This is the **registry-sync** class of bug. It is well-known on hybrid
servers and has historically been the reason Arclight and Ketting
ship custom patches in `RegistryManager` and `GameData`.

## 3. Hypotheses (Phase 0.e)

We need to discriminate between three failure modes. The diff tool
maps them to the labels `case_1` / `case_2` / `case_3`:

- **Case (1) — snapshot never sent.** The server side does not push the
  Forge registry snapshot to the joining client (some Mohist
  short-circuit or a missing channel registration). The client falls
  back to its **local** registry. Indicators: server table much larger
  than client table; vanilla aligned; modded section entirely
  `server_only`.

- **Case (2) — snapshot sent but ignored.** The snapshot reaches the
  client but `RegistryManager.applySnapshot` /
  `GameData.revertToFrozen` is short-circuited (either on the server
  before sending, or on the client on receive). Both sides end up
  carrying disjoint modded sets — each side built its own table from
  its own mod jars, then never reconciled. Indicators: both tables
  roughly same size, but every mod namespace has non-zero `server_only`
  AND non-zero `client_only`.

- **Case (3) — snapshot applied but runtime table drifted.** The
  snapshot was sent **and applied**, but a later code path on the
  server (Mohist's Bukkit-side registration, or a Mohist patch that
  reorders blockstates) rebuilds the runtime
  `Block.BLOCK_STATE_REGISTRY` with a **different id assignment**.
  The chunk encoder uses that drifted table; the client decodes
  through the synchronized one; ids mis-map. Indicators: same set of
  blockstates on both sides, but at different ids — lots of `shifted`,
  zero or near-zero `server_only`/`client_only`.

The TWC symptom (modded block → *different* modded block, not a missing
block, with vanilla intact) is the textbook signature of **case (3)**.
We instrument first; we patch on proof.

## 4. Plan of action

### Phase 0 — instrument (we are here)

- **Mini-mod** `tools/registry-dumper/` (owned by Agent A): a Forge
  client-+-server mod that, on a configurable trigger (server boot
  complete, client login complete), dumps four TSV files:
  - `server_pre_block_state_registry.tsv` — server table before
    snapshot apply
  - `server_post_block_state_registry.tsv` — server table after
    handshake
  - `client_pre_block_state_registry.tsv` — client local table
    before receiving snapshot
  - `client_post_block_state_registry.tsv` — client table after
    snapshot apply
  - (Same four for `*_blocks_registry.tsv` — the underlying block
    registry, used to corroborate where in the stack the drift
    starts.)

- **Diff tool** `tools/registry-sync/diff_registries.py` (this work):
  compares any two TSVs and emits the verdict.

- **This doc** — the operational plan and dead-end map.

### Phase 1 — decide on proof

Run the dumper on a single TWC restart. Collect the four files from the
server volume and the four files from the launcher's instance folder.
Run the diff tool on the `*_post*` pair. Read the verdict. Cross-check
with the `*_pre*` pair (the snapshot delta the system was supposed to
apply) and the `blocks_registry` pair (to localize the drift to
blockstates vs blocks).

### Phase 2 — patch (case 1/2/3) or migrate (Phase 1 verdict: irreducible)

See sections 8 and 9 below.

## 5. Dead-ends confirmed

These have been ruled out and **should not be re-investigated** unless
new evidence appears:

1. **No config toggle exists.** Neither `stackmania.yml` nor
   `mohist.yml` exposes a "force registry sync" / "strict handshake"
   knob. Searched both default and example configs in the fork.
2. **The fork does not patch Forge network/registry code.** The
   Stackmania 27-class custom architecture lives in performance,
   crash recovery, and plugin compat — it does **not** touch
   `net.minecraftforge.network.*` or `net.minecraftforge.registries.*`.
3. **Mohist 1.20.1 upstream is EOL** (~ January 2026). No upstream
   patches incoming. Any fix lives in this fork or in a different
   hybrid (Arclight / Ketting).
4. **Stackmania 1.1.0 vs 1.1.2 — same desync.** Bumping the fork
   version did not move the needle.
5. **OP vs non-OP players — same desync.** Permission level does not
   gate this code path.
6. **Mods are identical between client and server.** MD5-checked the
   mods folder on both sides; no version skew. So it is genuinely a
   *runtime registry* problem, not a packaging problem.
7. **Dramatic Doors ruled out.** The mod had a known registration
   quirk, but disabling it does not change the symptom.

## 6. Operational constraints

- **TWC is in production.** Volume
  `cf1e29e8-ed9c-429e-bc89-dea74703cb00` on the Pterodactyl host.
  Any restart must be announced; any file dropped into the volume must
  be `chown pterodactyl:pterodactyl` afterward or Pterodactyl's
  egg-script refuses to read it.
- **The client is launched by the Valonia Electron launcher.** Its
  `distribution.json` is signed (HMAC), so the per-instance mods
  folder cannot be hand-edited and shipped to players directly —
  any client-side mod needs to be re-packaged into a launcher build
  and re-signed before deployment to real users. For the *diagnostic
  run on a single dev machine*, dropping the mod jar into the
  launcher's per-instance `mods/` folder by hand is fine; just don't
  push it out.
- **Mohist's pre-existing in-process patches are fragile.** Adding a
  mixin or coremod that touches `RegistryManager`,
  `BlockBehaviour.BlockStateBase`, or `IdMapper` from the wrong load
  phase will either NPE during bootstrap or corrupt persistent data.
  We instrument **read-only** in Phase 0; we don't mutate.

## 7. Operational steps for the operator

Once Agent A's mini-mod is built and we're ready to capture:

```bash
# --- 1. Build the mini-mod ---
cd /opt/Stackmania/tools/registry-dumper/
./gradlew build
# Expected artifact: build/libs/registry-dumper-1.0.0.jar

# --- 2. Deploy server-side ---
cp build/libs/registry-dumper-1.0.0.jar \
   /var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/mods/
chown pterodactyl:pterodactyl \
   /var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/mods/registry-dumper-1.0.0.jar

# --- 3. Restart TWC server (via Pterodactyl, "Restart" button) ---
# Verify: the following files appear under the volume:
#   <volume>/registry-dumps/server_pre_block_state_registry.tsv
#   <volume>/registry-dumps/server_pre_blocks_registry.tsv
#   <volume>/registry-dumps/server_post_block_state_registry.tsv
#   <volume>/registry-dumps/server_post_blocks_registry.tsv
# (server_post_* appears after the first client login completes.)

# --- 4. Deploy client-side ---
# On the dev machine running the Valonia Launcher, locate the launcher's
# per-instance mods folder:
#   %APPDATA%\valonia-launcher\instances\twc-1.1.2\mods\
# (path will vary by launcher version; check the launcher's settings).
# Copy registry-dumper-1.0.0.jar there.

# --- 5. Connect to TWC, wait ~10s in a modded base, then disconnect. ---
# Verify: client_pre_* and client_post_* TSVs appear under the
# instance's working directory:
#   %APPDATA%\valonia-launcher\instances\twc-1.1.2\registry-dumps\
# (or, if the launcher sandboxes paths, under the per-instance .minecraft
# folder.)

# --- 6. Collect all eight files to a local diagnostic directory. ---

# --- 7. Run the diff. ---
python3 tools/registry-sync/diff_registries.py \
  ./registry-dumps/server_post_block_state_registry.tsv \
  ./registry-dumps/client_post_block_state_registry.tsv

# Also worth running (corroborates "drift is in blockstates, not blocks"):
python3 tools/registry-sync/diff_registries.py \
  ./registry-dumps/server_post_blocks_registry.tsv \
  ./registry-dumps/client_post_blocks_registry.tsv

# And the snapshot delta (what the apply *should* have changed):
python3 tools/registry-sync/diff_registries.py \
  ./registry-dumps/client_pre_block_state_registry.tsv \
  ./registry-dumps/client_post_block_state_registry.tsv

# --- 8. Read the verdict, share with the team. ---
# Save the JSON output for the record:
python3 tools/registry-sync/diff_registries.py \
  ./registry-dumps/server_post_block_state_registry.tsv \
  ./registry-dumps/client_post_block_state_registry.tsv \
  --format=json > diff-server-vs-client.json
```

## 8. Patch plan (Phase 2)

To be filled in once we have the verdict. Sketch by case:

### Case (1) — force snapshot to be sent

The Forge handshake registers a payload that ships the registry
snapshot. Candidates for what Mohist is intercepting:

- `net.minecraftforge.network.HandshakeMessages` — the data classes
  for the handshake payloads.
- `net.minecraftforge.network.ConfigSync` — drives the snapshot send
  on `ServerLoginPacketListenerImpl#handleAcceptedLogin`.
- `net.minecraftforge.network.NetworkHooks` — entry points called
  from `ServerLoginNetHandler` and `ClientLoginNetHandler`.

Action: locate the Mohist patch (mixin or transformer) that touches
any of these, and either remove the short-circuit or re-implement the
send. May involve a Stackmania-specific mixin in our 27-class
architecture's network section.

### Case (2) — let apply actually apply

`RegistryManager.applySnapshot` and `GameData.revertToFrozen` are the
two methods that turn a received snapshot into a live registry update.
Mohist sometimes patches these to no-op when Bukkit has already
registered its own view — that's the short-circuit we need to remove
or gate behind a "if Forge client" check.

Action: find the patch on those two methods, then either:
- remove the no-op,
- or wrap the no-op in a check that only fires when the apply call
  is **not** coming from a Forge login handler.

### Case (3) — align the runtime encoding table on the snapshot

This is the hardest. The snapshot was sent and applied; both sides
have the same set of blockstates. But the **server-side**
`Block.BLOCK_STATE_REGISTRY` was rebuilt (re-indexed) after apply by
Bukkit-side registration, so the ids it uses to encode chunks no
longer match the ids it advertised in the handshake.

Action options, in increasing order of disruption:
1. **Freeze early.** Move the freeze of `Block.BLOCK_STATE_REGISTRY`
   to *before* Mohist's Bukkit-side block registration runs. Any
   Bukkit block registration that happens after must be reflected as
   *additional* entries appended to the table, not as a re-ordering.
2. **Send the snapshot late.** Conversely, defer the snapshot send
   until *after* Bukkit-side registration has stabilized the runtime
   table, then snapshot from the truly-final table.
3. **Patch the encoder.** Route
   `ClientboundLevelChunkWithLightPacket`'s palette encoding through
   the same id table that was snapshotted, instead of the live
   `Block.BLOCK_STATE_REGISTRY`. Most invasive — touches the hot
   packet path — last resort.

Candidate classes to inspect when locating the drift:

- `net.minecraft.world.level.block.Block` (the static
  `BLOCK_STATE_REGISTRY` field)
- `net.minecraftforge.registries.GameData`
- `net.minecraftforge.registries.RegistryManager`
- `net.minecraftforge.registries.ForgeRegistry`
- whatever Mohist class hooks into Bukkit's
  `org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers` to expose
  blocks to plugins

## 9. Plan B — migrate hybrid (if Phase 1 says "irreducible")

If after instrumentation we conclude the desync cannot be fixed in
Stackmania without effectively re-implementing a substantial part of
Mohist's hybrid layer, the fallback is to migrate the TWC fork to a
different hybrid base — **Arclight** or **Ketting**. Both have an
actively-maintained Forge↔Bukkit bridge with different choices in
exactly the area where Mohist drifts.

Risks and costs:

- **27 custom Stackmania classes are NOT portable as-is.** Every
  class that touches Mohist-internal types (which is most of the
  performance and crash-recovery code) needs to be re-routed onto
  the new hybrid's equivalents. Estimated effort: 2–4 weeks of
  focused work.
- **TWC plugin re-config.** Several Bukkit plugins ship Mohist-aware
  shims. They'll need re-testing and possibly forking on the new base.
- **Mod-compat re-validation.** All ~209 mods need to be re-validated
  against the new hybrid. This is the bulk of the schedule risk —
  Arclight in particular has known incompatibilities with some
  popular content mods.
- **Player-facing downtime.** A migration window of at least one
  full weekend, with a clean backup of `world/` and a tested rollback
  path. Distribution to players via a new launcher build (re-signed
  `distribution.json`).

This plan is documented here so it's not invented under pressure if
the verdict ends up being "no patch path in Mohist."

## 10. Deliverables expected

By the end of Phase 1, the team must have on hand:

1. **The eight TSV dumps** (four `*_pre_*`, four `*_post_*`, blocks
   and blockstate registries on both sides), archived for the record.
2. **The JSON output** of
   `diff_registries.py server_post client_post --format=json`.
3. **A verdict** — one of `case_1` / `case_2` / `case_3` / `inconclusive`.
4. **Either**
   - a patch (a `fix/registry-sync` branch derived from this one) with
     the runtime change that fixes the verdict's case and a green test
     restart on TWC, or
   - a migration report (estimating cost in days, listing the 27
     custom classes that need re-routing, listing the mods that
     would need retesting) if the verdict points to Plan B.

---

See also:

- `tools/registry-dumper/` — the Forge mini-mod that emits the TSVs (Agent A)
- `tools/registry-sync/` — the diff tool and its README (this branch)
- `tools/registry-sync/diff_registries.py` — the script invoked above
- `docs/ARCHITECTURE.md` — the 27 custom classes inventory (for Plan B
  porting reference)
