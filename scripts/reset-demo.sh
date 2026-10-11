#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "This will permanently delete ONLY the isolated chairx-demo PostgreSQL volume and all manual demo edits."
echo "It will not delete the ordinary chairx database."
read -r -p "Type RESET-DEMO to confirm: " answer
if [[ "$answer" != "RESET-DEMO" ]]; then
  echo "Cancelled"
  exit 1
fi
docker compose -f docker-compose.yml -f docker-compose.demo.yml down --volumes
echo "Demo DB reset. Recreate it with: bash scripts/start-demo.sh"
