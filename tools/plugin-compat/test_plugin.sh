#!/usr/bin/env bash
#
# Run one plugin through the Stackmania plugin-compat container.
#
# Usage:  test_plugin.sh <plugin_id>
# Where <plugin_id> matches a key under `plugins:` in plugins.yml.
#
# Steps:
#   1. Read url / filename / pass_pattern / extra_fail_patterns from plugins.yml.
#   2. curl the plugin jar into /server/plugins/.
#   3. Invoke entrypoint.sh which boots the server, waits, shuts it down,
#      and dumps the captured log on stdout.
#   4. Grep the captured log for the pass pattern + the global fail patterns
#      + plugin-specific extra_fail_patterns.
#   5. Print a single-line verdict and exit 0 (PASS) or 1 (FAIL).
#
# Designed to run identically:
#   - inside the Docker container (CI),
#   - on a maintainer's workstation where the working dir is tools/plugin-compat.

set -euo pipefail

# --- Inputs ----------------------------------------------------------------
if [[ $# -lt 1 ]]; then
    echo "usage: test_plugin.sh <plugin_id>" >&2
    echo "available plugin_ids:" >&2
    yq eval '.plugins | keys | .[]' "$(dirname "$0")/plugins.yml" 2>/dev/null \
        || python3 -c "import yaml,sys; [print(k) for k in yaml.safe_load(open(sys.argv[1]))['plugins']]" \
              "$(dirname "$0")/plugins.yml" 2>/dev/null \
        || true
    exit 2
fi

PLUGIN_ID="$1"

# --- Locate plugins.yml + plugins dir -------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGINS_YML="${SCRIPT_DIR}/plugins.yml"
# Plugins live alongside the server jar inside the container, or in
# ./plugins/ next to the script when running locally.
if [[ -d /server/plugins ]]; then
    PLUGINS_DIR="/server/plugins"
else
    PLUGINS_DIR="${SCRIPT_DIR}/plugins"
    mkdir -p "${PLUGINS_DIR}"
fi

if [[ ! -s "${PLUGINS_YML}" ]]; then
    echo "FATAL: plugins.yml not found at ${PLUGINS_YML}" >&2
    exit 2
fi

# --- YAML accessor ---------------------------------------------------------
# Prefer yq (fast, type-correct). Fall back to python3+pyyaml for hosts
# that don't have yq (covered by the Dockerfile, but useful for laptops).
yaml_get() {
    local path="$1"
    if command -v yq >/dev/null 2>&1; then
        yq eval "${path} // \"\"" "${PLUGINS_YML}"
    else
        python3 - "${PLUGINS_YML}" "${path}" <<'PY'
import sys, yaml
doc = yaml.safe_load(open(sys.argv[1]))
expr = sys.argv[2]
# Translate a yq-ish path like .plugins.placeholderapi.url into a chain
# of dict/list lookups. Supports `[]` to dump a list one per line.
parts = expr.lstrip('.').split('.')
node = doc
for p in parts:
    if p == '':
        continue
    if p.endswith('[]'):
        node = node[p[:-2]]
        for item in node:
            print(item)
        sys.exit(0)
    node = node[p]
print('' if node is None else node)
PY
    fi
}

PLUGIN_NAME=$(yaml_get ".plugins.${PLUGIN_ID}.name")
PLUGIN_URL=$(yaml_get ".plugins.${PLUGIN_ID}.url")
PLUGIN_FILE=$(yaml_get ".plugins.${PLUGIN_ID}.filename")
PASS_PATTERN=$(yaml_get ".plugins.${PLUGIN_ID}.pass_pattern")

if [[ -z "${PLUGIN_NAME}" || -z "${PLUGIN_URL}" || -z "${PLUGIN_FILE}" || -z "${PASS_PATTERN}" ]]; then
    echo "FATAL: plugin '${PLUGIN_ID}' missing required fields in plugins.yml" >&2
    exit 2
fi

if [[ "${PLUGIN_URL}" == https://TODO/* ]]; then
    echo "FAIL: ${PLUGIN_NAME} — url placeholder not yet filled in plugins.yml" >&2
    exit 1
fi

# Read extra_fail_patterns into an array (one per line).
EXTRA_FAIL_PATTERNS=()
while IFS= read -r line; do
    [[ -n "${line}" ]] && EXTRA_FAIL_PATTERNS+=("${line}")
done < <(yaml_get ".plugins.${PLUGIN_ID}.extra_fail_patterns[]" 2>/dev/null || true)

# Global fail patterns — apply to every plugin. These are the regressions
# we care about catching across the board (the PAPI 1.1.1 bug, etc.).
GLOBAL_FAIL_PATTERNS=(
    "MohistMC\\.classLoader is null"
    "Fatal error trying to convert"
    "java\\.lang\\.NoSuchMethodError.*MohistMC"
)

# Ignored / known-noise patterns — match these are not failures even if
# they superficially look like errors. Keep this list tight.
IGNORE_PATTERNS=(
    "NoClassDefFoundError: org/embeddedt/modernfix/forge/config/NightConfigWatchThrottler"
)

# --- Download the plugin ---------------------------------------------------
echo "[test_plugin] ${PLUGIN_NAME}: downloading ${PLUGIN_URL}"
DEST="${PLUGINS_DIR}/${PLUGIN_FILE}"
# -f fail on HTTP >=400, -L follow redirects, --retry for transient blips.
if ! curl -fL --retry 3 --retry-delay 2 -o "${DEST}" "${PLUGIN_URL}"; then
    echo "FAIL: ${PLUGIN_NAME} — could not download ${PLUGIN_URL}"
    exit 1
fi
if [[ ! -s "${DEST}" ]]; then
    echo "FAIL: ${PLUGIN_NAME} — downloaded file is empty"
    exit 1
fi
echo "[test_plugin] ${PLUGIN_NAME}: downloaded $(stat -c%s "${DEST}" 2>/dev/null || wc -c <"${DEST}") bytes"

# --- Boot server + capture log --------------------------------------------
# entrypoint.sh dumps the log between BEGIN/END sentinels. We capture its
# stdout into a temp file and grep that.
CAPTURE="$(mktemp)"
trap 'rm -f "${CAPTURE}"' EXIT

ENTRYPOINT="${SCRIPT_DIR}/entrypoint.sh"
if [[ ! -x "${ENTRYPOINT}" ]]; then
    ENTRYPOINT="/server/entrypoint.sh"
fi

echo "[test_plugin] ${PLUGIN_NAME}: booting server"
if ! "${ENTRYPOINT}" > "${CAPTURE}" 2>&1; then
    echo "[test_plugin] ${PLUGIN_NAME}: server exited non-zero (boot crash?)"
    # Don't return early — we still grep, the crash may itself match a
    # fail pattern which gives the operator a clearer message.
fi

# Strip the ignored noise lines before grepping. This avoids a regex
# like `NullPointerException.*` from matching against a stack frame that
# originates from ModernFix and not the plugin.
FILTERED="$(mktemp)"
trap 'rm -f "${CAPTURE}" "${FILTERED}"' EXIT
if (( ${#IGNORE_PATTERNS[@]} > 0 )); then
    grep_args=()
    for p in "${IGNORE_PATTERNS[@]}"; do
        grep_args+=(-e "${p}")
    done
    grep -vE "${grep_args[@]}" "${CAPTURE}" > "${FILTERED}" || cp "${CAPTURE}" "${FILTERED}"
else
    cp "${CAPTURE}" "${FILTERED}"
fi

# --- Verdict ---------------------------------------------------------------
verdict_fail() {
    local reason="$1"
    echo "FAIL: ${PLUGIN_NAME} — reason: ${reason}"
    exit 1
}

# 1. Hard fail on global regression patterns.
for pat in "${GLOBAL_FAIL_PATTERNS[@]}"; do
    if grep -E -q "${pat}" "${FILTERED}"; then
        verdict_fail "global fail pattern matched: ${pat}"
    fi
done

# 2. Hard fail on plugin-specific patterns.
for pat in "${EXTRA_FAIL_PATTERNS[@]}"; do
    if grep -E -q "${pat}" "${FILTERED}"; then
        verdict_fail "plugin fail pattern matched: ${pat}"
    fi
done

# 3. Require the pass pattern.
if ! grep -E -q "${PASS_PATTERN}" "${FILTERED}"; then
    verdict_fail "pass pattern not found: ${PASS_PATTERN}"
fi

echo "PASS: ${PLUGIN_NAME}"
exit 0
