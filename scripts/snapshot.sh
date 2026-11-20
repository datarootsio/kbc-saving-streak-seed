#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE_DIR=".baseline"
CHECKPOINT_DIR=".checkpoint"
TRACKED_PATHS=("copy" "src" "tests" "data/seed")

copy_tree_to() {
    local dest="$1"
    rm -rf "$dest"
    mkdir -p "$dest"
    for item in "${TRACKED_PATHS[@]}"; do
        if [ -e "$item" ]; then
            mkdir -p "$dest/$(dirname "$item")"
            cp -Rp "$item" "$dest/$item"
        fi
    done
}

ensure_snapshots() {
    if [ ! -d "$BASELINE_DIR" ]; then
        copy_tree_to "$BASELINE_DIR"
    fi
    if [ ! -d "$CHECKPOINT_DIR" ]; then
        copy_tree_to "$CHECKPOINT_DIR"
    fi
}

diff_against() {
    local ref_dir="$1"
    local label="$2"
    local has_diff=0

    if [ ! -d "$ref_dir" ]; then
        echo "No $label snapshot found yet. Run 'make checkpoint' first."
        exit 0
    fi

    for item in "${TRACKED_PATHS[@]}"; do
        if [ -e "$ref_dir/$item" ] || [ -e "$item" ]; then
            set +e
            diff -ruN -x '.DS_Store' "$ref_dir/$item" "$item"
            local rc=$?
            set -e
            if [ "$rc" -eq 1 ]; then
                has_diff=1
            elif [ "$rc" -gt 1 ]; then
                echo "Error comparing $item against $label." >&2
                exit "$rc"
            fi
        fi
    done

    if [ "$has_diff" -eq 0 ]; then
        echo "No changes compared with $label."
    fi
}

restore_from() {
    local src_dir="$1"
    local label="$2"
    for item in "${TRACKED_PATHS[@]}"; do
        rm -rf "$item"
        if [ -e "$src_dir/$item" ]; then
            mkdir -p "$(dirname "$item")"
            cp -Rp "$src_dir/$item" "$item"
        fi
    done
    rm -rf "$ROOT_DIR/bin" "$ROOT_DIR/data/work"
    echo "Workspace restored from $label snapshot."
}

ACTION="${1:-}"
case "$ACTION" in
    ensure)
        ensure_snapshots
        ;;
    checkpoint)
        if [ ! -d "$BASELINE_DIR" ]; then
            copy_tree_to "$BASELINE_DIR"
        fi
        copy_tree_to "$CHECKPOINT_DIR"
        echo "Checkpoint saved in $CHECKPOINT_DIR/."
        ;;
    diff)
        if [ -d "$CHECKPOINT_DIR" ]; then
            diff_against "$CHECKPOINT_DIR" "checkpoint"
        elif [ -d "$BASELINE_DIR" ]; then
            diff_against "$BASELINE_DIR" "baseline"
        else
            echo "No checkpoint found yet. Run 'make checkpoint' before editing."
        fi
        ;;
    diff-baseline)
        diff_against "$BASELINE_DIR" "initial baseline"
        ;;
    restore)
        if [ -d "$CHECKPOINT_DIR" ]; then
            restore_from "$CHECKPOINT_DIR" "checkpoint"
        elif [ -d "$BASELINE_DIR" ]; then
            restore_from "$BASELINE_DIR" "initial baseline"
        else
            echo "No snapshot available to restore." >&2
            exit 1
        fi
        ;;
    restore-baseline)
        if [ -d "$BASELINE_DIR" ]; then
            restore_from "$BASELINE_DIR" "initial baseline"
        else
            echo "No initial baseline snapshot available to restore." >&2
            exit 1
        fi
        ;;
    *)
        echo "Usage: $0 {ensure|checkpoint|diff|diff-baseline|restore|restore-baseline}" >&2
        exit 1
        ;;
esac
