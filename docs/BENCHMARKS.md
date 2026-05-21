# Stackmania Benchmarks

> **Status:** protocol defined, results in progress. No published RAM / TPS / crash figure should be cited from earlier README versions — those numbers were design targets, not measurements. This document is the source of truth for 1.2.0 onward.

The intent of this document is twofold:
1. Give the maintainer a reproducible script for benching modules on the production TWC server.
2. Drive 1.2.0's keep / soft-deprecate / delete decisions for each module.

---

## Bench environment

The numbers in the "Results" section will be collected on the production [TWC](https://github.com/Lorenz0FF) server (or a like-for-like staging copy). Anyone reproducing should use a comparable workload — there is no synthetic mod pack defined for this fork.

Recommended hardware target: ≥ 8 physical cores, 16+ GB RAM dedicated to the JVM, NVMe storage. Network does not factor into idle bench but does dominate under load.

JVM flags must be identical across all runs. Use the flag set in [README.md § Recommended JVM flags](../README.md#recommended-jvm-flags).

---

## Protocol

### One-time setup

1. Pick a Stackmania build to bench. **1.1.2 is the floor** — earlier builds lack the bench harness.
2. Boot once with the default `stackmania.yml` (all modules `enabled: true`, three opt-in features `enabled: false`).
3. Confirm `stackmania-config/bench/` exists and is writable. Bench dumps go there.
4. Stop the server.

### Per-run procedure

This is the procedure for **one cell of the matrix**. Repeat once per cell.

1. Edit `stackmania-config/stackmania.yml`, flip the target module's `enabled:` flag.
2. Restart the server. Wait for the `Server READY` log line.
3. Let the server idle for **30 minutes**. No players, no auto-restart, no scheduled tasks firing.
4. Run `/stackmania bench dump`.
5. The dump lands at `stackmania-config/bench/<ISO-8601-timestamp>-<module-state>.json`. Rename it descriptively (e.g. `baseline_full.json`, `tickopt_off.json`).
6. Stop the server. Restore the flag. Move to the next cell.

> **Why 30 min idle?** GC pressure stabilizes around 10–15 min on G1 with these flags; another 15 min gives a confidence buffer. Going under 20 min produces noisy numbers, especially for the memory metrics.

> **Why idle, not loaded?** Idle is reproducible. A loaded bench is the right next step but requires synthetic players, which we don't have yet. Idle is enough to decide which modules pay for themselves at the boot/idle cost — modules whose value is "scales better under load" need a separate loaded-soak bench, tracked as a 1.3.0 task.

---

## The matrix

### Per-module on/off (15 cells)

For each module, the matrix produces one "all-on except this one" run plus the shared `baseline_full` run.

| # | Module flag | Notes |
|---|---|---|
| 0 | `baseline_full` (everything on) | Run once. All subsequent runs compare against this. |
| 1 | `tick_optimizer.enabled: false` | |
| 2 | `aggressive_memory.enabled: false` | |
| 3 | `crash_recovery.enabled: false` | |
| 4 | `security.enabled: false` | ⚠ See ARCHITECTURE.md edge case — bench numbers from this run are **not directly comparable** to baseline. Run it anyway, flag the data as caveated. |
| 5 | `mod_loader_bridge.enabled: false` | |
| 6 | `material_cache.enabled: false` | |
| 7 | `persistent_player.enabled: false` | |
| 8 | `bukkit_bridge.enabled: false` | |
| 9 | `perfect_registry.enabled: false` | |
| 10 | `performance_monitor.enabled: false` | |
| 11 | `stackmania_memory.enabled: false` | Pair with the ModernFix matrix below. |
| 12 | `universal_platform_adapter.enabled: false` | |
| 13 | `fabric_compatibility.enabled: false` | |
| 14 | `sinytra_bridge.enabled: false` | |

### Opt-in features (3 extra cells)

Run with everything else at baseline:

| # | Feature flag | Notes |
|---|---|---|
| 15 | `mob_cap.enabled: true` | Workload should include at least one farm-style mob source for this to be meaningful. |
| 16 | `dynamic_view_distance.enabled: true` | Idle bench won't trigger the dynamic drop. The cell mostly measures the sampler's overhead. |
| 17 | `parallel_init.enabled: true` | Compare startup time and the boot-period GC profile against baseline. Log line order will be jumbled — that is expected. |

### ModernFix / FerriteCore / MemoryLeakFix overlap (4 cells, 2×2)

The Stackmania memory layer overlaps with the upstream Forge mods ModernFix, FerriteCore, and MemoryLeakFix. We need to know whether keeping Stackmania's memory modules makes sense in the presence of those mods.

| Cell | `stackmania_memory.enabled` | ModernFix/FerriteCore/MemoryLeakFix in `/mods` |
|---|---|---|
| A | true | absent |
| B | true | present |
| C | false | absent |
| D | false | present |

Cell A is roughly the `baseline_full` cell, but **must be re-run on the same day with the same world seed** as the others — cross-day comparisons drift too much for memory numbers.

---

## What goes in each JSON dump

The bench harness writes a JSON shaped roughly like:

```json
{
  "stackmania_version": "1.1.2",
  "forge_version": "47.4.13",
  "jvm_flags": ["-Xms4G", "-Xmx6G", "..."],
  "uptime_seconds": 1812,
  "modules": {
    "tick_optimizer": { "enabled": true, "init_ms": 12, "samples": 1812 },
    "aggressive_memory": { "enabled": true, "init_ms": 4, "samples": 1812 },
    "...": "..."
  },
  "metrics": {
    "tps_avg": 19.97,
    "tps_min": 19.50,
    "mspt_p50_ms": 38,
    "mspt_p95_ms": 47,
    "mspt_p99_ms": 51,
    "heap_used_mb_p50": 2840,
    "heap_used_mb_p95": 3210,
    "gc_pause_p95_ms": 18,
    "gc_pause_max_ms": 42,
    "gc_full_count": 0,
    "thread_count": 86
  }
}
```

When extracting numbers for the results table, use the **p95** quantile for everything except TPS (which uses min) and GC full count (which is a raw count).

---

## Decisions to make after the matrix

Each module gets one of three verdicts in 1.2.0:

- **Keep** — measurable positive impact on at least one metric, no regression on others. Stays `enabled: true` by default.
- **Soft-deprecate** — neutral or marginal impact. Stays in the code but default flips to `enabled: false`, with a doc note. Slated for deletion in 1.3.0 if no one objects.
- **Delete** — net negative or zero-impact-but-non-trivial-maintenance. Code removed in 1.2.0.

The threshold for "measurable" is: the metric moves by more than the run-to-run noise observed in three baseline runs. If you don't have three baselines, **run them first** — every other conclusion depends on noise floor.

---

## Results

### Per-module matrix (idle 30min, default JVM flags)

| Module | RAM p95 Δ vs baseline | TPS min Δ | MSPT p95 Δ | GC pause p95 Δ | Verdict |
|---|---|---|---|---|---|
| `baseline_full` | — | — | — | — | — |
| `tick_optimizer` | TODO | TODO | TODO | TODO | TODO |
| `aggressive_memory` | TODO | TODO | TODO | TODO | TODO |
| `crash_recovery` | TODO | TODO | TODO | TODO | TODO |
| `security` | TODO ⚠ | TODO ⚠ | TODO ⚠ | TODO ⚠ | caveated |
| `mod_loader_bridge` | TODO | TODO | TODO | TODO | TODO |
| `material_cache` | TODO | TODO | TODO | TODO | TODO |
| `persistent_player` | TODO | TODO | TODO | TODO | TODO |
| `bukkit_bridge` | TODO | TODO | TODO | TODO | TODO |
| `perfect_registry` | TODO | TODO | TODO | TODO | TODO |
| `performance_monitor` | TODO | TODO | TODO | TODO | TODO |
| `stackmania_memory` | TODO | TODO | TODO | TODO | TODO |
| `universal_platform_adapter` | TODO | TODO | TODO | TODO | TODO |
| `fabric_compatibility` | TODO | TODO | TODO | TODO | TODO |
| `sinytra_bridge` | TODO | TODO | TODO | TODO | TODO |

### Opt-in features

| Feature | RAM p95 Δ | TPS min Δ | MSPT p95 Δ | Notes |
|---|---|---|---|---|
| `mob_cap.enabled: true` | TODO | TODO | TODO | Needs loaded test for real signal. |
| `dynamic_view_distance.enabled: true` | TODO | TODO | TODO | Idle test measures sampler overhead only. |
| `parallel_init.enabled: true` | TODO | TODO | TODO | Compare startup time too: TODO vs TODO. |

### ModernFix / FerriteCore / MemoryLeakFix 2×2

| Cell | `stackmania_memory` | Upstream mods | RAM p95 | GC pause p95 | Verdict |
|---|---|---|---|---|---|
| A | on | absent | TODO | TODO | — |
| B | on | present | TODO | TODO | TODO |
| C | off | absent | TODO | TODO | TODO |
| D | off | present | TODO | TODO | TODO |

Decision: TODO. The question to answer here is "does cell B beat cell D enough to justify the maintenance of `stackmania_memory`?" If no, soft-deprecate it.

---

## Reproducibility notes

- Always note the exact world seed / save being used. Different worlds have wildly different chunk-tick costs.
- The bench command is non-destructive — running it does not affect server state.
- Two runs of the **same configuration** on different days can differ by 5–10% on memory metrics. Three baselines per session.
- If you switch JVM versions between cells, mark the cell as caveated. Java 17.0.x patch differences can move GC numbers by a few percent.
