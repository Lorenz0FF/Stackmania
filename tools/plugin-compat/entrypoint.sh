#!/usr/bin/env bash
#
# Boot Stackmania, wait for "Done!", hold for $BOOT_WAIT_SECONDS to give
# plugins time to fully enable, then shut down gracefully and dump the
# captured log on stdout for the parent test harness to grep.
#
# Exit codes:
#   0  server reached "Done!" and shut down cleanly
#   1  boot timeout (server never logged "Done!")
#   2  jar missing or unexecutable
#   >2 propagated from the JVM if it crashes outright
#
# Environment knobs:
#   STACKMANIA_JAR        path to the server jar               (default: /server/stackmania-server.jar)
#   JAVA_OPTS             extra JVM flags                      (default: -Xmx2G -Xms2G)
#   BOOT_WAIT_SECONDS     extra seconds to wait after "Done!"  (default: 60)
#   BOOT_TIMEOUT_SECONDS  hard ceiling on time to reach "Done!" (default: 300)

set -euo pipefail

STACKMANIA_JAR="${STACKMANIA_JAR:-/server/stackmania-server.jar}"
JAVA_OPTS="${JAVA_OPTS:--Xmx2G -Xms2G}"
BOOT_WAIT_SECONDS="${BOOT_WAIT_SECONDS:-60}"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-300}"

LOG_DIR="/server/logs"
LATEST_LOG="${LOG_DIR}/latest.log"
BOOT_LOG="${LOG_DIR}/boot-capture.log"
PID_FILE="/tmp/stackmania.pid"
STDIN_FIFO="/tmp/stackmania.stdin"

mkdir -p "${LOG_DIR}"
: > "${BOOT_LOG}"
: > "${LATEST_LOG}"

if [[ ! -s "${STACKMANIA_JAR}" ]]; then
    echo "FATAL: Stackmania jar not found at ${STACKMANIA_JAR}" >&2
    exit 2
fi

# --- Graceful shutdown -----------------------------------------------------
# We feed the server's stdin via a FIFO so we can `stop` it cleanly. SIGTERM
# from `docker stop` is forwarded as a `stop` command before falling back to
# SIGKILL after a grace window.
cleanup() {
    local rc=$?
    # If the server is still alive, attempt a clean stop.
    if [[ -f "${PID_FILE}" ]]; then
        local pid
        pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
        if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
            echo "[entrypoint] sending save-all + stop to PID ${pid}"
            printf 'save-all\n' > "${STDIN_FIFO}" 2>/dev/null || true
            sleep 2
            printf 'stop\n'     > "${STDIN_FIFO}" 2>/dev/null || true
            # Give Forge up to 30s to flush worlds, then SIGTERM, then SIGKILL.
            for _ in $(seq 1 30); do
                kill -0 "${pid}" 2>/dev/null || break
                sleep 1
            done
            if kill -0 "${pid}" 2>/dev/null; then
                echo "[entrypoint] stop timed out, sending SIGTERM"
                kill -TERM "${pid}" 2>/dev/null || true
                sleep 5
            fi
            if kill -0 "${pid}" 2>/dev/null; then
                echo "[entrypoint] still alive, sending SIGKILL"
                kill -KILL "${pid}" 2>/dev/null || true
            fi
        fi
    fi
    # Always dump the final log on stdout for the caller to grep.
    if [[ -s "${LATEST_LOG}" ]]; then
        echo "===== BEGIN latest.log ====="
        cat "${LATEST_LOG}"
        echo "===== END latest.log ====="
    elif [[ -s "${BOOT_LOG}" ]]; then
        echo "===== BEGIN boot-capture.log (latest.log not produced) ====="
        cat "${BOOT_LOG}"
        echo "===== END boot-capture.log ====="
    fi
    rm -f "${STDIN_FIFO}"
    exit "${rc}"
}
trap cleanup EXIT INT TERM

# --- Launch ----------------------------------------------------------------
mkfifo "${STDIN_FIFO}"
# Keep the FIFO open by holding a writer FD; otherwise the JVM sees EOF on
# stdin immediately and may exit.
exec 9> "${STDIN_FIFO}"

echo "[entrypoint] Booting ${STACKMANIA_JAR} with JAVA_OPTS=${JAVA_OPTS}"
# shellcheck disable=SC2086  # JAVA_OPTS intentionally word-split
java ${JAVA_OPTS} -jar "${STACKMANIA_JAR}" nogui \
    < "${STDIN_FIFO}" \
    > "${BOOT_LOG}" 2>&1 &
SERVER_PID=$!
echo "${SERVER_PID}" > "${PID_FILE}"
echo "[entrypoint] PID=${SERVER_PID}, waiting for 'Done' line (timeout ${BOOT_TIMEOUT_SECONDS}s)"

# --- Wait for boot ---------------------------------------------------------
# Forge prints `Done (X.Xs)! For help, type "help"` once everything is ready.
# We tail the boot log to detect it. Bukkit/Spigot uses the same phrasing.
booted=0
elapsed=0
while (( elapsed < BOOT_TIMEOUT_SECONDS )); do
    if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
        echo "[entrypoint] server PID ${SERVER_PID} died before booting" >&2
        wait "${SERVER_PID}" || true
        # Surface logs into latest.log path before cleanup dumps them.
        cp -f "${BOOT_LOG}" "${LATEST_LOG}" 2>/dev/null || true
        exit 1
    fi
    if grep -qE 'Done \([0-9.]+s\)!|\[Server thread/INFO\].*Done' "${BOOT_LOG}" 2>/dev/null; then
        booted=1
        echo "[entrypoint] server reached Done after ${elapsed}s"
        break
    fi
    sleep 2
    elapsed=$(( elapsed + 2 ))
done

if (( booted == 0 )); then
    echo "[entrypoint] timed out after ${BOOT_TIMEOUT_SECONDS}s waiting for Done" >&2
    cp -f "${BOOT_LOG}" "${LATEST_LOG}" 2>/dev/null || true
    exit 1
fi

# --- Hold window -----------------------------------------------------------
# Give plugins a moment to fully enable + log their banners. PAPI in
# particular logs `[PlaceholderAPI] Enabled` slightly after Done.
echo "[entrypoint] holding for ${BOOT_WAIT_SECONDS}s to capture plugin enable lines"
sleep "${BOOT_WAIT_SECONDS}"

# --- Locate latest.log ----------------------------------------------------
# Forge writes logs/latest.log once logging is initialised. If it never
# appeared (very early crash), fall back to the boot capture.
if [[ -s "${LATEST_LOG}" ]]; then
    :
elif [[ -s "${BOOT_LOG}" ]]; then
    cp -f "${BOOT_LOG}" "${LATEST_LOG}"
fi

# Trigger cleanup() which will stop the server and dump the log.
exit 0
