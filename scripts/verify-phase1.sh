#!/usr/bin/env bash
# Phase 1 verification — no local Java/Maven required; uses Docker only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

USER_IMAGE="acruet-user:phase1-verify"
ADMIN_IMAGE="acruet-admin:phase1-verify"
USER_PORT=18080
ADMIN_PORT=18081

echo "==> Maven verify (containerized)"
docker run --rm \
  -v "$ROOT:/workspace" \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 \
  mvn -q -B clean verify

echo "==> Build user image"
docker build --target user -t "$USER_IMAGE" .

echo "==> Build admin image"
docker build --target admin -t "$ADMIN_IMAGE" .

cleanup() {
  docker rm -f acruet-user-phase1 acruet-admin-phase1 >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Run user container on :$USER_PORT"
docker run -d --name acruet-user-phase1 -p "${USER_PORT}:8080" "$USER_IMAGE"

echo "==> Run admin container on :$ADMIN_PORT"
docker run -d --name acruet-admin-phase1 -p "${ADMIN_PORT}:8080" "$ADMIN_IMAGE"

wait_for_health() {
  local url="$1"
  local label="$2"
  for _ in $(seq 1 30); do
    if curl -fsS "$url" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      echo "    $label health OK"
      return 0
    fi
    sleep 2
  done
  echo "    $label health check failed" >&2
  return 1
}

wait_for_health "http://127.0.0.1:${USER_PORT}/health" "user"
wait_for_health "http://127.0.0.1:${ADMIN_PORT}/health" "admin"

echo "==> Smoke check landing pages"
curl -fsS "http://127.0.0.1:${USER_PORT}/" | grep -q "a-cruet"
curl -fsS "http://127.0.0.1:${ADMIN_PORT}/" | grep -q "Administration"

echo "Phase 1 verification passed."
