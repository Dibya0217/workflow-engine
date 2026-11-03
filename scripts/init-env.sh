#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then printf '%s\n' '.env already exists; leaving it unchanged.'; exit 0; fi
umask 077
{
  printf 'DATABASE_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'JWT_SECRET=%s\n' "$(openssl rand -hex 48)"
  printf 'ADMIN_USERNAME=flowlab-admin\n'
  printf 'ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 18)"
  printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 18)"
  printf 'KAFKA_ENABLED=true\nTASK_LEASE_SECONDS=30\nWORKER_CONCURRENCY=8\n'
} > .env
printf '%s\n' 'Created .env with random local secrets. Read it locally for login credentials; do not commit it.'
