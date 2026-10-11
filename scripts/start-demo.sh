#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -s .env ]]; then
  echo "No .env detected. Generating local development credentials."
  bash scripts/init-local-env.sh
fi

echo "Starting the isolated ChairX demo stack (database: chairx_demo)."
echo "This uses localhost ports 5432, 8080 and 5173; stop an existing ChairX stack first."
docker compose -f docker-compose.yml -f docker-compose.demo.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build --wait
echo "Demo UI: http://localhost:5173"
echo "The demo database persists on restart. Reset only with: ./scripts/reset-demo.sh"
