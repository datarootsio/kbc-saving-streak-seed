#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BIN="$ROOT_DIR/bin/run-acctopen"
if [ ! -x "$BIN" ]; then
    echo "Binary $BIN not found; building first..."
    make -s build
fi

CASE_FILTER="${1:-}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/kbc-cobol-tests.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

total=0
passed=0
failed=0

extract_section() {
    local section="$1"
    local file="$2"
    awk -v target="[$section]" '
        /^\[.*\]$/ { in_sec = ($0 == target); next }
        in_sec { print }
    ' "$file"
}

get_kv() {
    local key="$1"
    local text="$2"
    printf '%s\n' "$text" | awk -F'=' -v k="$key" '$1 == k { sub(/^[^=]*=/, "", $0); print $0; exit }'
}

count_lines() {
    local file="$1"
    if [ ! -f "$file" ]; then
        echo 0
    else
        awk 'END { print NR }' "$file"
    fi
}

shopt -s nullglob
case_files=(tests/cases/*.case)
if [ "${#case_files[@]}" -eq 0 ]; then
    echo "No test cases found in tests/cases/*.case" >&2
    exit 1
fi

echo "Running KBC Retail Account Opening test suite..."
echo "------------------------------------------------------------"

for case_file in "${case_files[@]}"; do
    case_name="$(basename "$case_file" .case)"
    if [ -n "$CASE_FILTER" ] && [[ "$case_name" != *"$CASE_FILTER"* ]]; then
        continue
    fi

    total=$((total + 1))
    work_dir="$TMP_ROOT/$case_name"
    mkdir -p "$work_dir"

    cp data/seed/accounts.seed.dat    "$work_dir/accounts.dat"
    cp data/seed/subaccounts.seed.dat "$work_dir/subaccounts.dat"
    cp data/seed/mandates.seed.dat    "$work_dir/mandates.dat"
    cp data/seed/customers.seed.dat   "$work_dir/customers.dat"
    : > "$work_dir/audit.dat"

    export DD_ACCTFILE="$work_dir/accounts.dat"
    export DD_SUBFILE="$work_dir/subaccounts.dat"
    export DD_AUDFILE="$work_dir/audit.dat"
    export DD_ELBFILE="$work_dir/mandates.dat"
    export DD_RISKFILE="$work_dir/customers.dat"

    pre_input="$(extract_section "PRE_INPUT" "$case_file")"
    if [ -n "$(printf '%s' "$pre_input" | tr -d '[:space:]')" ]; then
        awk -v dir="$work_dir" '
            BEGIN { step = 1 }
            /^---$/ { step++; next }
            { print > (dir "/pre_" step ".req") }
        ' <<< "$pre_input"
        for pre_req in "$work_dir"/pre_*.req; do
            [ -f "$pre_req" ] && "$BIN" < "$pre_req" > /dev/null
        done
    fi

    acct_before="$(count_lines "$DD_ACCTFILE")"
    sub_before="$(count_lines "$DD_SUBFILE")"
    aud_before="$(count_lines "$DD_AUDFILE")"

    input_block="$(extract_section "INPUT" "$case_file")"
    output="$("$BIN" <<< "$input_block")"

    acct_after="$(count_lines "$DD_ACCTFILE")"
    sub_after="$(count_lines "$DD_SUBFILE")"
    aud_after="$(count_lines "$DD_AUDFILE")"

    acct_delta=$((acct_after - acct_before))
    sub_delta=$((sub_after - sub_before))
    aud_delta=$((aud_after - aud_before))

    actual_rc="$(get_kv "RC" "$output")"
    actual_reason="$(get_kv "REASON" "$output")"
    actual_iban="$(get_kv "IBAN" "$output")"
    actual_parent="$(get_kv "PARENT_IBAN" "$output")"
    actual_audit="$(get_kv "AUDIT" "$output")"
    actual_msg="$(get_kv "MESSAGE" "$output")"

    last_acct="$(tail -n 1 "$DD_ACCTFILE" 2>/dev/null || true)"
    last_sub="$(tail -n 1 "$DD_SUBFILE" 2>/dev/null || true)"
    last_aud="$(tail -n 1 "$DD_AUDFILE" 2>/dev/null || true)"

    errors=()
    while IFS= read -r line; do
        trimmed="$(printf '%s' "$line" | sed 's/^[[:space:]]*//')"
        [ -z "$trimmed" ] && continue
        [[ "$trimmed" == \#* ]] && continue

        ekey="${trimmed%%=*}"
        eval_str="${trimmed#*=}"

        case "$ekey" in
            RC)
                if [ "$actual_rc" != "$eval_str" ]; then
                    errors+=("expected RC=$eval_str, got RC=$actual_rc")
                fi
                ;;
            REASON)
                if [ "$actual_reason" != "$eval_str" ]; then
                    errors+=("expected REASON=$eval_str, got REASON=$actual_reason")
                fi
                ;;
            IBAN_EMPTY)
                if [ "$eval_str" = "true" ] && [ -n "$actual_iban" ]; then
                    errors+=("expected empty IBAN, got IBAN=$actual_iban")
                elif [ "$eval_str" = "false" ] && [ -z "$actual_iban" ]; then
                    errors+=("expected allocated IBAN, got empty IBAN")
                fi
                ;;
            IBAN_PREFIX)
                if [[ "$actual_iban" != "$eval_str"* ]] || [ "${#actual_iban}" -ne 16 ]; then
                    errors+=("expected 16-char IBAN starting with $eval_str, got '$actual_iban'")
                fi
                ;;
            IBAN_BANK)
                bank_slice="${actual_iban:4:3}"
                if [ "$bank_slice" != "$eval_str" ]; then
                    errors+=("expected IBAN bank code $eval_str, got '$bank_slice' in '$actual_iban'")
                fi
                ;;
            PARENT_IBAN)
                if [ "$actual_parent" != "$eval_str" ]; then
                    errors+=("expected PARENT_IBAN=$eval_str, got '$actual_parent'")
                fi
                ;;
            AUDIT)
                if [ "$actual_audit" != "$eval_str" ]; then
                    errors+=("expected AUDIT=$eval_str, got '$actual_audit'")
                fi
                ;;
            MESSAGE_CONTAINS)
                if [[ "$actual_msg" != *"$eval_str"* ]]; then
                    errors+=("expected MESSAGE containing '$eval_str', got '$actual_msg'")
                fi
                ;;
            ACCT_DELTA)
                if [ "$acct_delta" -ne "$eval_str" ]; then
                    errors+=("expected ACCT_DELTA=$eval_str, got $acct_delta")
                fi
                ;;
            ACCT_LAST_CONTAINS)
                if [[ "$last_acct" != *"$eval_str"* ]]; then
                    errors+=("expected last ACCTFILE record containing '$eval_str', got '$last_acct'")
                fi
                ;;
            SUB_DELTA)
                if [ "$sub_delta" -ne "$eval_str" ]; then
                    errors+=("expected SUB_DELTA=$eval_str, got $sub_delta")
                fi
                ;;
            SUB_LAST_CONTAINS)
                if [[ "$last_sub" != *"$eval_str"* ]]; then
                    errors+=("expected last SUBFILE record containing '$eval_str', got '$last_sub'")
                fi
                ;;
            AUDIT_DELTA)
                if [ "$aud_delta" -ne "$eval_str" ]; then
                    errors+=("expected AUDIT_DELTA=$eval_str, got $aud_delta")
                fi
                ;;
            AUDIT_LAST_CONTAINS)
                if [[ "$last_aud" != *"$eval_str"* ]]; then
                    errors+=("expected last AUDFILE record containing '$eval_str', got '$last_aud'")
                fi
                ;;
            *)
                errors+=("unknown assertion key '$ekey' in $case_file")
                ;;
        esac
    done < <(extract_section "EXPECT" "$case_file")

    if [ "${#errors[@]}" -eq 0 ]; then
        printf "  [PASS] %s\n" "$case_name"
        passed=$((passed + 1))
    else
        printf "  [FAIL] %s\n" "$case_name"
        for err in "${errors[@]}"; do
            printf "         - %s\n" "$err"
        done
        failed=$((failed + 1))
    fi
done

echo "------------------------------------------------------------"
printf "Summary: %d passed, %d failed, %d total\n" "$passed" "$failed" "$total"

if [ "$failed" -ne 0 ]; then
    exit 1
fi
