#!/usr/bin/env bash
# run_bench_matrix.sh
#
# Drive the full Stackmania A/B bench matrix.
#
# For each label (baseline + 14 core modules + 3 opt-ins + 3 ModernFix matrix
# corners), this script:
#   1. Patches stackmania.yml to the right state
#   2. Restarts the server (Pterodactyl API, docker, or prompts the operator)
#   3. Sleeps SOAK_MINUTES so TPS/MSPT stabilise
#   4. Runs "/stackmania bench dump" through the appropriate channel
#   5. Archives the resulting JSON dump under $OUTPUT_DIR with the right label
#
# Idempotent: re-running skips labels already recorded in $OUTPUT_DIR/.progress.
#
# Usage:
#   ./run_bench_matrix.sh path/to/bench.env
#
# See README.md for details and example env files.

set -euo pipefail

###############################################################################
# Config loading
###############################################################################

BENCH_ENV_FILE="${1:-bench.env}"
if [[ ! -f "$BENCH_ENV_FILE" ]]; then
    echo "ERROR: env file not found: $BENCH_ENV_FILE" >&2
    echo "Copy example-pterodactyl.env or example-docker-compose.yml and edit." >&2
    exit 1
fi

# shellcheck disable=SC1090
set -a
source "$BENCH_ENV_FILE"
set +a

: "${SERVER_TYPE:?SERVER_TYPE must be set (pterodactyl|docker|manual)}"
: "${STACKMANIA_CONFIG_PATH:?STACKMANIA_CONFIG_PATH must be set}"
: "${BENCH_DUMP_DIR:?BENCH_DUMP_DIR must be set}"
: "${OUTPUT_DIR:?OUTPUT_DIR must be set}"
SOAK_MINUTES="${SOAK_MINUTES:-30}"

mkdir -p "$OUTPUT_DIR"
PROGRESS_FILE="$OUTPUT_DIR/.progress"
touch "$PROGRESS_FILE"

LOG_FILE="$OUTPUT_DIR/run_bench_matrix.log"

log() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
    echo "$msg" | tee -a "$LOG_FILE"
}

is_done() {
    grep -Fxq "$1" "$PROGRESS_FILE"
}

mark_done() {
    echo "$1" >> "$PROGRESS_FILE"
}

###############################################################################
# Module lists
###############################################################################

CORE_MODULES=(
    tick_optimizer
    aggressive_memory
    crash_recovery
    security
    mod_loader_bridge
    material_cache
    persistent_player
    bukkit_bridge
    perfect_registry
    performance_monitor
    stackmania_memory
    universal_platform_adapter
    fabric_compatibility
    sinytra_bridge
)

# Opt-in features default to false; bench measures them flipped ON.
OPT_IN_FEATURES=(
    mob_cap
    dynamic_view_distance
    parallel_init
)

###############################################################################
# YAML patching
###############################################################################

# flip_module <name> <true|false>
# Patches stackmania.yml so modules.<name>.enabled = <value>, or for opt-in
# features (top-level keys), top-level <name>.enabled = <value>.
flip_module() {
    local name="$1"
    local value="$2"
    local cfg="$STACKMANIA_CONFIG_PATH"

    if [[ ! -f "$cfg" ]]; then
        echo "ERROR: config not found: $cfg" >&2
        return 1
    fi

    # Detect if it lives under modules: or at top level (opt-in features).
    local container="modules"
    local is_optin=false
    for f in "${OPT_IN_FEATURES[@]}"; do
        if [[ "$f" == "$name" ]]; then
            container=""
            is_optin=true
            break
        fi
    done

    if command -v yq >/dev/null 2>&1; then
        if $is_optin; then
            yq -i ".${name}.enabled = ${value}" "$cfg"
        else
            yq -i ".modules.${name}.enabled = ${value}" "$cfg"
        fi
    else
        # Fallback sed-based flip. Works on the inline-map form documented in
        # the spec, e.g. `tick_optimizer: { enabled: true }`. Falls back to
        # multi-line block form too (key on its own line, then `  enabled: x`).
        if grep -Eq "^[[:space:]]*${name}:[[:space:]]*\{[[:space:]]*enabled:" "$cfg"; then
            # Inline-map form.
            sed -i.bak -E \
                "s|(^[[:space:]]*${name}:[[:space:]]*\{[[:space:]]*enabled:[[:space:]]*)(true|false)|\1${value}|" \
                "$cfg"
        elif grep -Eq "^[[:space:]]*${name}:[[:space:]]*$" "$cfg"; then
            # Block form: find the next `enabled:` line under the key.
            python3 - "$cfg" "$name" "$value" <<'PY'
import re, sys, pathlib
path, name, value = sys.argv[1], sys.argv[2], sys.argv[3]
src = pathlib.Path(path).read_text(encoding="utf-8").splitlines(keepends=True)
out = []
in_block = False
key_indent = 0
patched = False
for line in src:
    m = re.match(rf"^([ \t]*){re.escape(name)}:[ \t]*$", line)
    if m and not patched:
        in_block = True
        key_indent = len(m.group(1))
        out.append(line)
        continue
    if in_block:
        m2 = re.match(r"^([ \t]*)enabled:[ \t]*(true|false)\b", line)
        if m2 and len(m2.group(1)) > key_indent:
            line = re.sub(r"(enabled:[ \t]*)(true|false)", rf"\1{value}", line, count=1)
            patched = True
            in_block = False
        elif re.match(r"^[ \t]*\S", line) and not line.startswith(" " * (key_indent + 1)):
            in_block = False
    out.append(line)
if not patched:
    sys.stderr.write(f"WARN: did not patch {name} in {path}\n")
pathlib.Path(path).write_text("".join(out), encoding="utf-8")
PY
        else
            echo "WARN: could not locate '$name' in $cfg, leaving as-is" >&2
        fi
        rm -f "${cfg}.bak"
    fi
}

# reset_all_modules: enable all core modules, disable all opt-in features.
# This is the baseline state we restore between cycles so each test isolates
# exactly one variable.
reset_all_modules() {
    log "Resetting config to baseline (all core ON, all opt-in OFF)"
    for m in "${CORE_MODULES[@]}"; do
        flip_module "$m" true
    done
    for f in "${OPT_IN_FEATURES[@]}"; do
        flip_module "$f" false
    done
}

###############################################################################
# Server control
###############################################################################

restart_server() {
    case "$SERVER_TYPE" in
        pterodactyl)
            : "${PTERODACTYL_API_URL:?}"
            : "${PTERODACTYL_API_KEY:?}"
            : "${PTERODACTYL_SERVER_ID:?}"
            log "Pterodactyl: sending restart signal to $PTERODACTYL_SERVER_ID"
            curl -fsS \
                -H "Authorization: Bearer $PTERODACTYL_API_KEY" \
                -H "Content-Type: application/json" \
                -H "Accept: application/json" \
                -X POST \
                -d '{"signal":"restart"}' \
                "$PTERODACTYL_API_URL/servers/$PTERODACTYL_SERVER_ID/power" \
                >/dev/null
            # Wait for "running" state.
            local tries=0
            while (( tries < 60 )); do
                local state
                state=$(curl -fsS \
                    -H "Authorization: Bearer $PTERODACTYL_API_KEY" \
                    -H "Accept: application/json" \
                    "$PTERODACTYL_API_URL/servers/$PTERODACTYL_SERVER_ID/resources" \
                    | grep -oE '"current_state":"[a-z]+"' \
                    | head -n1 \
                    | sed -E 's/.*"([a-z]+)".*/\1/')
                if [[ "$state" == "running" ]]; then
                    log "Pterodactyl: server reports running"
                    return 0
                fi
                sleep 10
                tries=$((tries + 1))
            done
            echo "ERROR: server did not reach 'running' state within 10 min" >&2
            return 1
            ;;
        docker)
            : "${DOCKER_CONTAINER_NAME:?DOCKER_CONTAINER_NAME required for docker mode}"
            log "Docker: restarting $DOCKER_CONTAINER_NAME"
            docker restart "$DOCKER_CONTAINER_NAME" >/dev/null
            ;;
        manual)
            echo
            echo "==> MANUAL ACTION REQUIRED <=="
            echo "Restart the server, then press <ENTER> once it is fully up."
            read -r
            ;;
        *)
            echo "ERROR: unknown SERVER_TYPE=$SERVER_TYPE" >&2
            return 1
            ;;
    esac
}

# send_console <command>
# Send a console command to the running server (no leading slash).
send_console() {
    local cmd="$1"
    case "$SERVER_TYPE" in
        pterodactyl)
            curl -fsS \
                -H "Authorization: Bearer $PTERODACTYL_API_KEY" \
                -H "Content-Type: application/json" \
                -H "Accept: application/json" \
                -X POST \
                -d "$(printf '{"command":"%s"}' "$cmd")" \
                "$PTERODACTYL_API_URL/servers/$PTERODACTYL_SERVER_ID/command" \
                >/dev/null
            ;;
        docker)
            # Most Minecraft container images expose RCON or a `mc-send-to-console`
            # helper. We try the rcon-cli wrapper first, fall back to docker exec.
            if docker exec "$DOCKER_CONTAINER_NAME" which rcon-cli >/dev/null 2>&1; then
                docker exec "$DOCKER_CONTAINER_NAME" rcon-cli "$cmd"
            else
                echo "WARN: rcon-cli not found in container, falling back to attach pipe"
                echo "$cmd" | docker exec -i "$DOCKER_CONTAINER_NAME" sh -c 'cat > /tmp/cmd'
                echo "  (manual: docker exec -it $DOCKER_CONTAINER_NAME and paste: $cmd)"
            fi
            ;;
        manual)
            echo
            echo "==> MANUAL ACTION REQUIRED <=="
            echo "In the server console run: $cmd"
            echo "Then press <ENTER>."
            read -r
            ;;
    esac
}

###############################################################################
# Bench dump + archive
###############################################################################

wait_soak() {
    local minutes="$1"
    local total=$((minutes * 60))
    local elapsed=0
    log "Soaking for $minutes min to let TPS/JIT/GC stabilise"
    while (( elapsed < total )); do
        local remaining=$((total - elapsed))
        local mm=$((remaining / 60))
        local ss=$((remaining % 60))
        printf "\r  soak remaining: %02d:%02d   " "$mm" "$ss"
        sleep 5
        elapsed=$((elapsed + 5))
    done
    printf "\r  soak complete                  \n"
}

run_bench_dump() {
    log "Triggering 'stackmania bench dump'"
    send_console "stackmania bench dump"
    # The plugin writes the file synchronously, but we give the filesystem a
    # moment to flush.
    sleep 5
}

# archive_dump <label>: copy the newest stackmania-bench-*.json under
# BENCH_DUMP_DIR into OUTPUT_DIR/<label>.json. Returns nonzero if nothing found.
archive_dump() {
    local label="$1"
    local newest
    newest=$(ls -t "$BENCH_DUMP_DIR"/stackmania-bench-*.json 2>/dev/null | head -n1 || true)
    if [[ -z "${newest:-}" ]]; then
        echo "ERROR: no stackmania-bench-*.json found in $BENCH_DUMP_DIR" >&2
        return 1
    fi
    cp -f "$newest" "$OUTPUT_DIR/${label}.json"
    log "Archived: $newest -> $OUTPUT_DIR/${label}.json"
}

###############################################################################
# Bench cycle: one label end-to-end
###############################################################################

# run_label <label> <setup_callback>
# - setup_callback patches the config the way this label needs
# - we then restart, soak, dump, archive, mark progress
# - finally reset_all_modules so the next label starts from baseline
run_label() {
    local label="$1"
    local setup="$2"

    if is_done "$label"; then
        log "Skipping '$label' (already in .progress)"
        return 0
    fi

    log "=== Starting label: $label ==="
    "$setup"
    restart_server
    wait_soak "$SOAK_MINUTES"
    run_bench_dump
    archive_dump "$label"
    mark_done "$label"
    log "=== Finished label: $label ==="
}

###############################################################################
# Setup callbacks
###############################################################################

setup_baseline() {
    reset_all_modules
}

# Disable exactly one core module.
make_setup_disable_core() {
    local module="$1"
    eval "setup_disable_${module}() {
        reset_all_modules
        flip_module '${module}' false
    }"
}

# Enable exactly one opt-in feature.
make_setup_enable_optin() {
    local feature="$1"
    eval "setup_enable_${feature}() {
        reset_all_modules
        flip_module '${feature}' true
    }"
}

for m in "${CORE_MODULES[@]}"; do
    make_setup_disable_core "$m"
done
for f in "${OPT_IN_FEATURES[@]}"; do
    make_setup_enable_optin "$f"
done

# ModernFix x Stackmania-memory matrix corners.
# "SM off" means the 3 memory-related modules are all disabled together.
setup_mf_on_sm_off() {
    reset_all_modules
    flip_module aggressive_memory false
    flip_module stackmania_memory false
    flip_module performance_monitor false
}

setup_mf_off_sm_on() {
    reset_all_modules
    echo
    echo "==> MANUAL ACTION REQUIRED <=="
    echo "Move ModernFix*.jar OUT of the server's mods/ folder."
    echo "Press <ENTER> once done."
    read -r
}

setup_mf_off_sm_off() {
    reset_all_modules
    flip_module aggressive_memory false
    flip_module stackmania_memory false
    flip_module performance_monitor false
    echo
    echo "==> MANUAL ACTION REQUIRED <=="
    echo "ModernFix*.jar must still be OUT of the server's mods/ folder."
    echo "If you removed it earlier, leave it out and press <ENTER>."
    read -r
}

restore_modernfix() {
    echo
    echo "==> MANUAL ACTION REQUIRED <=="
    echo "Bench complete. Move ModernFix*.jar BACK into mods/ folder."
    echo "Press <ENTER> once done."
    read -r
}

###############################################################################
# Main matrix loop
###############################################################################

main() {
    log "Bench matrix start. SERVER_TYPE=$SERVER_TYPE OUTPUT_DIR=$OUTPUT_DIR SOAK_MINUTES=$SOAK_MINUTES"

    # 1. Baseline (all 14 core ON, all 3 opt-in OFF). This is also MF on + SM on.
    run_label baseline_full setup_baseline

    # 2. Per-core-module ablation.
    for m in "${CORE_MODULES[@]}"; do
        run_label "disable_${m}" "setup_disable_${m}"
    done

    # 3. Per-opt-in feature: flip ON, measure effect vs baseline.
    for f in "${OPT_IN_FEATURES[@]}"; do
        run_label "enable_${f}" "setup_enable_${f}"
    done

    # 4. ModernFix x Stackmania-memory 2x2 matrix.
    #    MF on + SM on is the same data as baseline_full, so we skip it.
    run_label mf_on_sm_off  setup_mf_on_sm_off
    run_label mf_off_sm_on  setup_mf_off_sm_on
    run_label mf_off_sm_off setup_mf_off_sm_off
    if ! is_done "modernfix_restored"; then
        restore_modernfix
        mark_done "modernfix_restored"
    fi

    # 5. Restore baseline config so the operator doesn't accidentally leave the
    # server in a half-disabled state.
    reset_all_modules

    # 6. Parse results into a markdown report.
    log "Running parse_dumps.py to produce BENCHMARKS_results.md"
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if command -v python3 >/dev/null 2>&1; then
        PY=python3
    else
        PY=python
    fi
    "$PY" "$script_dir/parse_dumps.py" "$OUTPUT_DIR" \
        > "$OUTPUT_DIR/BENCHMARKS_results.md"
    log "Wrote $OUTPUT_DIR/BENCHMARKS_results.md"

    log "All done."
}

main "$@"
