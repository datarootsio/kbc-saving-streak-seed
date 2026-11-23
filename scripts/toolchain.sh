#!/usr/bin/env bash
set -euo pipefail

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

if command -v cobc >/dev/null 2>&1; then
    echo "GnuCOBOL toolchain ready: $(cobc --version | head -n 1)"
    exit 0
fi

echo "cobc not found on PATH. Installing GnuCOBOL..."

if command -v apt-get >/dev/null 2>&1; then
    SUDO=""
    if [ "$(id -u)" -ne 0 ]; then
        SUDO="sudo"
        if ! sudo -n true 2>/dev/null && [ ! -t 0 ]; then
            echo "Error: GnuCOBOL (cobc) is not installed yet and sudo requires your VM password." >&2
            echo "Run 'make tools' (or 'sudo apt-get update && sudo apt-get install -y gnucobol') once in a terminal tab, then retry." >&2
            exit 1
        fi
    fi
    $SUDO env DEBIAN_FRONTEND=noninteractive apt-get update
    $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y gnucobol
elif command -v brew >/dev/null 2>&1; then
    yes | env HOMEBREW_NO_AUTO_UPDATE=1 HOMEBREW_ASK=0 brew install gnucobol
else
    echo "Error: Neither apt-get nor brew is available to install gnucobol." >&2
    exit 1
fi

if ! command -v cobc >/dev/null 2>&1; then
    echo "Error: cobc installation did not place cobc on PATH." >&2
    exit 1
fi

echo "GnuCOBOL installed: $(cobc --version | head -n 1)"
