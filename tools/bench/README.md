# Stackmania bench matrix tooling

## What this is

A pair of scripts that runs the full A/B bench matrix for Stackmania
(14 core modules + 3 opt-in features + ModernFix 2x2 corner cases) without
sitting in front of the console for 12 hours. You launch it once, walk away,
come back to `BENCHMARKS_results.md`.

## Prerequisites

- Stackmania 1.1.2 or newer deployed somewhere you control. The plugin must
  expose the `/stackmania bench dump` console command.
- Access to the server console: Pterodactyl client API, `docker exec`, or
  manual prompts at the keyboard.
- Bash 4+ (Git Bash on Windows works), `curl`, and Python 3.10+ on the host
  that runs the script.
- `yq` (mikefarah's Go version) is **recommended** for YAML patching.
  Without it the script falls back to `sed`/Python, which works against the
  documented inline-map and block forms but is less forgiving of exotic
  YAML formatting.

## Quick start (Pterodactyl)

```bash
cp example-pterodactyl.env bench.env
# edit bench.env: panel URL, API key, server ID, paths
chmod +x run_bench_matrix.sh
./run_bench_matrix.sh bench.env
```

## Quick start (Docker)

```bash
cp example-docker-compose.yml docker-compose.yml
# drop stackmania-1.20.1-1.1.2-server.jar + forge installer + your mods/ into ./server/
docker compose up -d
# write bench.env (see Manual section), then:
./run_bench_matrix.sh bench.env
```

A minimal bench.env for docker:

```env
SERVER_TYPE=docker
DOCKER_CONTAINER_NAME=stackmania-bench
STACKMANIA_CONFIG_PATH=./server/stackmania-config/stackmania.yml
BENCH_DUMP_DIR=./server/plugins/Stackmania
OUTPUT_DIR=./bench-results
SOAK_MINUTES=30
```

## Quick start (manual)

If you run the server on bare metal, in a non-Docker VM, or behind any other
panel: set `SERVER_TYPE=manual`. The script will prompt you at each step
("restart now, press ENTER when up") instead of automating restarts and
console commands. You still get the YAML patching, dump archiving, soak
timer, and final parsing for free.

```env
SERVER_TYPE=manual
STACKMANIA_CONFIG_PATH=/opt/stackmania/stackmania-config/stackmania.yml
BENCH_DUMP_DIR=/opt/stackmania/plugins/Stackmania
OUTPUT_DIR=./bench-results
SOAK_MINUTES=30
```

## What it does

For each of the following labels it patches `stackmania.yml`, restarts,
soaks for `SOAK_MINUTES`, runs `stackmania bench dump`, and archives the
result to `$OUTPUT_DIR/<label>.json`:

1. `baseline_full` — all 14 core modules on, 3 opt-ins off
2. `disable_<core_module>` for each of the 14 core modules
3. `enable_<opt_in_feature>` for each of the 3 opt-ins
4. `mf_on_sm_off`, `mf_off_sm_on`, `mf_off_sm_off` — the ModernFix matrix
   corners (the fourth corner `mf_on_sm_on` is the same data as
   `baseline_full`, so it's not re-run)

Total wall time at `SOAK_MINUTES=30`:
`(1 + 14 + 3 + 3) * (30 min soak + ~5 min restart/dump) ≈ 9-12h`.

Bump `SOAK_MINUTES` higher for noisier infra. The longer the soak, the
smaller the noise floor in `parse_dumps.py`'s output.

## ModernFix special case

ModernFix is a Forge mod, not a Stackmania module, so it cannot be flipped
via `stackmania.yml`. The matrix corners that need it OFF require you to
physically move `ModernFix*.jar` out of the server's `mods/` folder.

The script prompts you exactly when to do this:

```
==> MANUAL ACTION REQUIRED <==
Move ModernFix*.jar OUT of the server's mods/ folder.
Press <ENTER> once done.
```

A second prompt fires at the end to remind you to put it back. If you have
a custom mod manager (Pterodactyl mod loader plugin, Sinytra UI, etc.) you
can automate this externally; the prompts are blocking so the bench won't
proceed without confirmation.

The "SM off" corners disable three modules together because they are
functionally interdependent: `aggressive_memory`, `stackmania_memory`, and
`performance_monitor`. Disabling only one in isolation produced misleading
results in earlier hand-rolled benches.

## Resuming after interruption

Every completed label is appended to `$OUTPUT_DIR/.progress`. Re-running
`./run_bench_matrix.sh bench.env` skips labels already in that file and
picks up where it left off. To force a re-run of a single label, delete its
line from `.progress` and the matching `<label>.json` file.

## Output

After the matrix finishes you get:

```
$OUTPUT_DIR/
  baseline_full.json
  disable_tick_optimizer.json
  disable_aggressive_memory.json
  ...
  enable_parallel_init.json
  mf_on_sm_off.json
  mf_off_sm_on.json
  mf_off_sm_off.json
  .progress
  run_bench_matrix.log
  BENCHMARKS_results.md   <-- this is the one you paste into docs/BENCHMARKS.md
```

You can re-render the markdown at any time without re-running the bench:

```bash
python3 parse_dumps.py ./bench-results > BENCHMARKS_results.md
```

## Troubleshooting

- **TPS not stable after 30 min.** Some hosts (esp. shared VPS, noisy
  neighbours) need 45-60 min. Bump `SOAK_MINUTES`. If TPS still drifts,
  check `spark profiler` separately — the bench can't isolate signal from
  external CPU pressure.
- **Dump not found in `$BENCH_DUMP_DIR`.** Verify the path matches the
  actual `plugins/Stackmania/` directory the running server writes to.
  In Docker setups, make sure the host-side and container-side paths are
  bind-mounted and synced.
- **`yq: command not found` warnings.** Install mikefarah's yq:
  `https://github.com/mikefarah/yq/releases`. Pure-sed fallback handles
  the documented YAML formats but is fragile against reformatting.
- **Pterodactyl API returns 403.** The client API token needs both the
  "control" and "console" scopes on the target server. Regenerate it from
  Account Settings > API Credentials.
- **`baseline_full.json not found`** when running `parse_dumps.py`. The
  bash driver must complete at least the baseline cycle before parsing has
  anything to compare against. If the baseline cycle was interrupted,
  delete `.progress` and start over.
- **Dumps show deltas under the noise threshold.** That's a finding, not a
  bug: it means the module under test has no measurable steady-state cost
  on an idle server. Re-run under load (real players, a stress-test
  profile, or `mineflayer` bots) before drawing conclusions about
  default-on/default-off.
