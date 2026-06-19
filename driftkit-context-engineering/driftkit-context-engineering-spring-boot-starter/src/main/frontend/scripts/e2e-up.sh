#!/usr/bin/env bash
#
# Bring up the live E2E stack for the Context Engineering UI:
#   - MongoDB on :27017 (via docker, if not already running)
#   - DriftKitDevRunner (Spring) on :8085
#
# Vite (:8080) is started by Playwright itself (see playwright.config.ts webServer).
# After this script reports "backend ready", run:  npm run e2e
#
# Env: DEEPSEEK_API_KEY enables the live model-call Playground test (optional).
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
MODULE="driftkit-context-engineering/driftkit-context-engineering-spring-boot-starter"
MAIN_CLASS="ai.driftkit.context.spring.DriftKitDevRunner"

# 1. MongoDB
if ! nc -z localhost 27017 2>/dev/null; then
  echo "==> Starting MongoDB (docker)…"
  docker run -d --rm -p 27017:27017 --name driftkit-e2e-mongo mongo:7 >/dev/null
  until nc -z localhost 27017 2>/dev/null; do sleep 1; done
fi
echo "==> MongoDB ready on :27017"

# 2. Backend (test-scope main class). We launch with a plain `java -cp` built from
# the project's real dependency classpath — NOT exec-maven-plugin, which injects
# its own jars (incl. a broken xalan TransformerFactory service descriptor that
# crashes Spring's FormContentFilter at startup). Plain java = the same classpath
# the IDE uses, so it starts cleanly and portably on any machine.
cd "$REPO_ROOT"
echo "==> Compiling backend…"
mvn -q -pl "$MODULE" -am -DskipTests test-compile

CP_FILE="$(mktemp)"
mvn -q -pl "$MODULE" dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -DincludeScope=test
CP="$MODULE/target/classes:$MODULE/target/test-classes:$(cat "$CP_FILE")"

echo "==> Starting DriftKitDevRunner on :8085 (logs: /tmp/driftkit-e2e-backend.log)…"
java -cp "$CP" "$MAIN_CLASS" >/tmp/driftkit-e2e-backend.log 2>&1 &
echo $! > /tmp/driftkit-e2e-backend.pid

echo -n "==> Waiting for backend"
for _ in $(seq 1 60); do
  if curl -s -o /dev/null http://localhost:8085/data/v1.0/admin/prompt/ 2>/dev/null; then
    echo " — ready on :8085"
    echo "Stop later with: kill \$(cat /tmp/driftkit-e2e-backend.pid)"
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " — TIMEOUT. See /tmp/driftkit-e2e-backend.log"
exit 1
