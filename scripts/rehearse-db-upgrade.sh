#!/usr/bin/env bash
# Safe local-only restore/rehearsal of an operator-owned pg_dump -Fc archive.
# Does NOT connect to a running ChairX Postgres database or retained volume.
set -euo pipefail
umask 077

usage() {
  cat >&2 <<'HELP'
Usage:
  bash scripts/rehearse-db-upgrade.sh --acknowledge-sensitive-backup /absolute/path/chairx-before-upgrade.dump

Prerequisites: Docker running, Java 25, Maven, PostgreSQL custom pg_dump (-Fc).
Only use an authorized backup; it can contain personal, commercial and finance data.
It is copied inside a BRAND NEW Testcontainers PostgreSQL 17 database and removed.
No existing local/docker-compose/production database is modified by this script.
Never commit, upload or paste the backup contents or unrestricted test logs.
HELP
}
if [[ $# -ne 2 || "$1" != "--acknowledge-sensitive-backup" ]]; then
  usage
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
requested="$2"
if [[ ! -f "$requested" || ! -r "$requested" ]]; then
  echo "ERROR: backup must be an existing readable file." >&2
  exit 2
fi
dump_dir="$(cd "$(dirname "$requested")" && pwd -P)"
dump_path="${dump_dir}/$(basename "$requested")"

# Do not accidentally store confidential business/employee data in Git.
if [[ "$dump_path" == "$repo_root/"* ]]; then
  echo "ERROR: move the sensitive dump OUTSIDE the ChairX Git repository." >&2
  exit 2
fi
if [[ "$(head -c 5 "$dump_path")" != "PGDMP" ]]; then
  echo "ERROR: expected pg_dump -Fc custom archive (PGDMP); plain SQL is rejected." >&2
  exit 2
fi
for command in docker java mvn; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "ERROR: prerequisite command missing: $command" >&2
    exit 2
  fi
done
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is not running." >&2
  exit 2
fi
if ! java -version 2>&1 | head -n 1 | grep -Eq '"25([.]|")'; then
  echo "ERROR: run with Java 25 LTS." >&2
  exit 2
fi
export CHAIRX_REHEARSAL_DUMP="$dump_path"
cd "$repo_root/backend"
mvn --batch-mode --no-transfer-progress \
  '-Dtest=BackupUpgradeRehearsalTest#operatorProvidedBackup' test
echo "PASS: disposable backup rehearsal finished. Review output for any reconciliation warning."
