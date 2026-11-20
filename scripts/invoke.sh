#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

make -s build >&2

if [ ! -f "data/work/accounts.dat" ]; then
    mkdir -p data/work
    cp data/seed/accounts.seed.dat    data/work/accounts.dat
    cp data/seed/subaccounts.seed.dat data/work/subaccounts.dat
    cp data/seed/mandates.seed.dat    data/work/mandates.dat
    cp data/seed/customers.seed.dat   data/work/customers.dat
    : > data/work/audit.dat
fi

export DD_ACCTFILE="${DD_ACCTFILE:-$ROOT_DIR/data/work/accounts.dat}"
export DD_SUBFILE="${DD_SUBFILE:-$ROOT_DIR/data/work/subaccounts.dat}"
export DD_AUDFILE="${DD_AUDFILE:-$ROOT_DIR/data/work/audit.dat}"
export DD_ELBFILE="${DD_ELBFILE:-$ROOT_DIR/data/work/mandates.dat}"
export DD_RISKFILE="${DD_RISKFILE:-$ROOT_DIR/data/work/customers.dat}"

if [ "$#" -eq 1 ] && [ -f "$1" ]; then
    exec "$ROOT_DIR/bin/run-acctopen" < "$1"
elif [ "$#" -gt 0 ]; then
    printf '%s\n' "$@" | "$ROOT_DIR/bin/run-acctopen"
elif [ -t 0 ]; then
    echo "Usage:" >&2
    echo "  ./scripts/invoke.sh KEY=VALUE [KEY=VALUE ...]" >&2
    echo "  ./scripts/invoke.sh <path-to-case-file>" >&2
    echo "  ./scripts/invoke.sh < request.txt" >&2
    exit 1
else
    exec "$ROOT_DIR/bin/run-acctopen"
fi
