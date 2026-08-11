#!/usr/bin/env bash
# Runs every check that must pass before a change is considered done.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA_HOME
fi

echo "== backend: build and test =="
(cd "$ROOT_DIR/backend" && ./gradlew build)

echo "== frontend: typecheck =="
(cd "$ROOT_DIR/frontend" && npm run typecheck)

echo "== frontend: lint =="
(cd "$ROOT_DIR/frontend" && npm run lint)

echo "== frontend: test =="
(cd "$ROOT_DIR/frontend" && npm test)

echo "== frontend: production build =="
(cd "$ROOT_DIR/frontend" && npm run build)

echo "All checks passed."
