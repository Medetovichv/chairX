#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -e .env ]]; then
  echo "Existing .env found; leaving configured passwords unchanged."
  exit 0
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "OpenSSL is required to generate local credentials securely." >&2
  exit 1
fi

umask 077
tmp=$(mktemp ./.env.XXXXXX)
trap 'rm -f "$tmp"' EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    CHAIRX_CATALOG_PASSWORD=)
      printf '%s\n' "CHAIRX_CATALOG_PASSWORD=$(openssl rand -hex 24)"
      ;;
    CHAIRX_BOOTSTRAP_ADMIN_PASSWORD=)
      printf '%s\n' "CHAIRX_BOOTSTRAP_ADMIN_PASSWORD=$(openssl rand -hex 24)"
      ;;
    CHAIRX_DEV_MANAGER_PASSWORD=)
      printf '%s\n' "CHAIRX_DEV_MANAGER_PASSWORD=$(openssl rand -hex 24)"
      ;;
    CHAIRX_DEV_EMPLOYEE_PASSWORD=)
      printf '%s\n' "CHAIRX_DEV_EMPLOYEE_PASSWORD=$(openssl rand -hex 24)"
      ;;
    *)
      printf '%s\n' "$line"
      ;;
  esac
done < .env.example > "$tmp"

chmod 600 "$tmp"
mv "$tmp" .env
echo "Created local .env with unique random secrets (not printed)."
echo "The file is gitignored; inspect it locally if you need the development account passwords."

