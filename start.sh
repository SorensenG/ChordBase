#!/usr/bin/env bash
set -Eeuo pipefail

export CHORDPRO_EXTRACTOR_BASE_URL="http://127.0.0.1:8000"
export SERVER_PORT="${PORT:-8080}"
JAVA_MEMORY_OPTS="${JAVA_MEMORY_OPTS:--Xms64m -Xmx192m -XX:+ExitOnOutOfMemoryError}"

extractor_pid=""
java_pid=""

terminate_children() {
    if [[ -n "${extractor_pid}" ]] && kill -0 "${extractor_pid}" 2>/dev/null; then
        kill "${extractor_pid}" 2>/dev/null || true
    fi

    if [[ -n "${java_pid}" ]] && kill -0 "${java_pid}" 2>/dev/null; then
        kill "${java_pid}" 2>/dev/null || true
    fi

    wait 2>/dev/null || true
}

trap 'terminate_children; exit 143' TERM
trap 'terminate_children; exit 130' INT

cd /app/extractor
uvicorn app.main:app --host 127.0.0.1 --port 8000 --proxy-headers &
extractor_pid=$!

java ${JAVA_MEMORY_OPTS} -jar /app/app.jar &
java_pid=$!

set +e
wait -n "${extractor_pid}" "${java_pid}"
exit_code=$?
set -e

terminate_children
exit "${exit_code}"
