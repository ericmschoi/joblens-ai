#!/usr/bin/env bash
# Runs the backend and the frontend dev server together.
# Stop both with Ctrl-C.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA_HOME
fi

JAVA_MAJOR="$("${JAVA_HOME:-/usr}/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [ "$JAVA_MAJOR" != "25" ]; then
  echo "Expected Java 25, found major version ${JAVA_MAJOR}. Set JAVA_HOME to a Java 25 JDK." >&2
  exit 1
fi

cleanup() {
  trap - INT TERM EXIT
  [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
  [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

echo "Starting backend on http://localhost:8080"
(cd "$ROOT_DIR/backend" && ./gradlew bootRun) &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:5173"
(cd "$ROOT_DIR/frontend" && npm run dev) &
FRONTEND_PID=$!

wait
