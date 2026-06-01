# registry-sync — diff tool for Mohist + Forge registry desync diagnosis

This directory holds the offline diff tool that compares the two TSV dumps
produced by the [`registry-dumper`](../registry-dumper/) mini-mod (one from
the Mohist server, one from the Forge client) and tells you, in plain
English, **which of the three failure modes** the registry desync falls
into.

For the bigger picture (what the bug is, how we got here, the operational
plan), read `docs/REGISTRY_SYNC_DIAGNOSIS.md` at the root of the repo.

## Quick usage

```bash
python3 tools/registry-sync/diff_registries.py \
  /path/to/server_post_block_state_registry.tsv \
  /path/to/client_post_block_state_registry.tsv
```

- Argument 1: **server-side** TSV (typically `server_post_block_state_registry.tsv`)
- Argument 2: **client-side** TSV (typically `client_post_block_state_registry.tsv`)
- Argument 3 (optional): `--format=summary|full|json` (default `summary`)

The script is pure-stdlib Python 3.10+. No `pip install`. No external
network. Runs on Linux, macOS, and Windows.

Exit codes:

| code | meaning                                                |
|------|--------------------------------------------------------|
| 0    | diff produced (the registries may still be desynced; read the verdict) |
| 1    | fatal error (file missing, unreadable, etc.)           |
| 2    | bad CLI usage                                          |

## What it does

1. Parses both TSVs (header metadata + `id → blockstate` map).
2. Walks the ids in order and finds the **first mismatch**.
3. Computes the **vanilla range OK** — the largest `N` such that ids
   `0..N` are identical on both sides and live in the `minecraft:`
   namespace. This is normally the alignment we keep; the divergence
   begins where modded entries start.
4. **Per-mod breakdown** — for each non-vanilla namespace
   (`tacz`, `create`, `mekanism`, …), counts:
   - `server_only`: ids the server has and the client doesn't (and the
     blockstate doesn't appear anywhere else on the client),
   - `client_only`: ids the client has and the server doesn't,
   - `shifted`: blockstates present on **both** sides but at **different**
     ids (a registry shift), with the delta (`client_id - server_id`),
     or `"mixed"` if the delta isn't uniform within that mod.
5. Picks a **verdict** mapping to one of the three documented hypotheses.

## TSV format expected

The mini-mod writes one entry per line:

```
<id>\t<blockstate string>
```

Header comments at the top of the file carry the metadata the diff tool
needs:

```
# kind=block_state_registry
# mc=1.20.1
# forge=47.4.13
# side=server
# label=server_post
# timestamp=2026-06-01T12:34:56Z
# total_entries=15234
```

`label` distinguishes the four dumps the mini-mod produces in a single
run:

- `server_pre` — before `RegistryManager.applySnapshot` / before the
  client connects
- `server_post` — after the handshake, while the client is connected
- `client_pre` — before the client receives the snapshot (local table)
- `client_post` — after the client applied the snapshot it received

The bug investigation almost always compares
`server_post` ↔ `client_post`. The `*_pre` files are kept for
forensic comparison and to show the delta the snapshot was *supposed*
to apply.

## `--format=json`

```bash
python3 tools/registry-sync/diff_registries.py \
  server_post.tsv client_post.tsv --format=json > diff.json
```

The JSON payload is stable and meant for scripted use (CI, dashboards,
posting into BENCHMARKS.md or a Discord report). Top-level keys:

```json
{
  "server_file": "...",
  "client_file": "...",
  "server": { "side": "server", "label": "server_post", "mc": "1.20.1",
              "forge": "47.4.13", "actual_entries": 15234, ... },
  "client": { ... },
  "warnings": [ "..." ],
  "first_mismatch": { "id": 2348,
                      "server": "Block{tacz:gun_smith_table}[...]",
                      "client": "Block{tacz:gun_smith_table}[...]" },
  "vanilla_range_ok_up_to": 2347,
  "per_mod": {
    "tacz":     { "server_only": 142, "client_only": 0,   "shifted": 142, "shift_value": 0 },
    "create":   { "server_only": 0,   "client_only": 0,   "shifted": 89,  "shift_value": 12 },
    "mekanism": { "server_only": 5,   "client_only": 5,   "shifted": 0,   "shift_value": null }
  },
  "verdict": "case_3_runtime_table_mismatch",
  "verdict_label": "case (3) — snapshot sent and applied, but ..."
}
```

## Interpreting the verdict

The script collapses the diff into one of five labels.

### `case_0_no_divergence`

The two registries are identical. No bug to investigate from this angle.
(If the desync is still visible in-game, you're looking at the wrong
registry — collect the blocks registry, the items registry, or a fluid
registry next.)

### `case_1_snapshot_never_sent`

**Indicator**: the server table is much larger than the client table.
Vanilla is aligned (the client starts with its own vanilla table), but
the entire modded section is server-only. The client is operating off
its **local registry** because the snapshot Forge would normally push
during the handshake never arrived.

Likely fix surface: Forge handshake messages
(`net.minecraftforge.network.HandshakeMessages`,
`net.minecraftforge.network.ConfigSync`) — Mohist is intercepting or
short-circuiting the channel that carries the registry snapshot.

### `case_2_snapshot_ignored_by_mohist`

**Indicator**: both tables are roughly the same size, but for most mod
namespaces both `server_only` and `client_only` are non-zero — each side
holds a **disjoint set** of modded blockstates. The snapshot was sent
but the receiver kept its own local view. Often `client_post` ≈
`client_pre` in this case.

Likely fix surface: `RegistryManager.applySnapshot` /
`GameData.revertToFrozen` — Mohist short-circuits the actual apply on
the server side (the snapshot never makes it into the runtime tables)
or the client rejects the message and falls back.

### `case_3_runtime_table_mismatch`

**Indicator**: the entries on both sides are essentially the **same set**
of blockstates, but at **different ids** (lots of `shifted`, few or no
`server_only`/`client_only`). The snapshot the server *advertises*
during the handshake is fine — but the **runtime table** the server uses
later to **encode chunk packets** (`ClientboundLevelChunkWithLightPacket`,
which palettes through `Block.BLOCK_STATE_REGISTRY`) was built with a
different id assignment order. The client decodes packets through the
synchronized table; the ids don't line up; the displayed block is the
wrong one.

This is the **most probable case** for the TWC symptom (vanilla looks
right, modded looks like a *different* modded block — a classic
mis-indexed palette).

Likely fix surface: any path that touches `Block.BLOCK_STATE_REGISTRY`
or `GameData.getBlockStateIDMap()` after the freeze — usually a Mohist
patch that re-orders the table to interleave Bukkit-side registration,
desynchronizing it from the snapshot Forge already shipped.

### `inconclusive`

The script saw a divergence but the pattern didn't cleanly match any of
the three cases. Re-run with `--format=full` and read the raw per-id
divergence. Cross-check by collecting the `blocks_registry.tsv` pair
too — if those are identical but `block_state_registry` diverges, you
have proof the desync is purely in the blockstate index, not in the
block registry itself.

## Notes on robustness

- Missing file → `ERROR: file not found: <path>` on stderr, exit 1.
- Headers with mismatched `mc=` or `forge=` → warning, the diff
  continues anyway (you may genuinely be debugging a version skew).
- Side declared `server` in argument 2 (or `client` in argument 1) →
  warning that the CLI args may be swapped.
- Header `total_entries=N` vs actual parsed count → warning if they
  diverge (catches silent truncation in the dumper).
- Unicode / weird bytes in blockstate strings → decoded with
  `errors="replace"`; never raises.

## See also

- `tools/registry-dumper/` — the Forge mini-mod that emits the TSVs
- `docs/REGISTRY_SYNC_DIAGNOSIS.md` — full diagnostic write-up,
  operational steps for the operator, and the patch plan
