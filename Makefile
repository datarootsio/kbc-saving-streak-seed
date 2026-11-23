SHELL := /bin/bash
export PATH := /opt/homebrew/bin:/usr/local/bin:$(PATH)

COBC       := cobc
COBCFLAGS  := -fixed -Wall -Wno-unfinished -I copy
BIN_DIR    := bin
TARGET     := $(BIN_DIR)/run-acctopen
MAIN_SRC   := src/ACCTCLI.cbl
SUB_SRCS   := $(filter-out $(MAIN_SRC),$(sort $(wildcard src/*.cbl)))
COPYBOOKS  := $(sort $(wildcard copy/*.cpy))
CASE       ?=

.PHONY: help tools deploy check-format build test checkpoint diff diff-baseline restore restore-baseline reset-data clean

help:
	@echo "KBC Retail Account Opening — COBOL Workshop Commands"
	@echo ""
	@echo "  make tools          Verify or install the GnuCOBOL (cobc) compiler"
	@echo "  make deploy         Prepare the workshop VM toolchain and verify the suite"
	@echo "  make build          Check 72-column COBOL format and compile $(TARGET)"
	@echo "  make test           Build and run all test cases (optional: CASE=name)"
	@echo "  make reset-data     Reset data/work/*.dat from data/seed/*.seed.dat"
	@echo "  make checkpoint     Save a local snapshot in .checkpoint/"
	@echo "  make diff           Show unified diff of edits since last checkpoint"
	@echo "  make diff-baseline  Show unified diff of all edits since workshop start"
	@echo "  make restore        Revert working files back to .checkpoint/"
	@echo "  make clean          Remove compiled binaries and data/work/"

tools:
	@bash scripts/toolchain.sh
	@bash scripts/snapshot.sh ensure

deploy: tools test
	@echo "COBOL mainframe exercise is ready on this VM (CLI service — no web port needed)."

check-format:
	@awk 'length($$0) > 72 { \
		printf "%s:%d: fixed-format COBOL line exceeds column 72 (%d chars)\n", FILENAME, FNR, length($$0); \
		bad = 1 \
	} END { exit bad }' $(COPYBOOKS) $(MAIN_SRC) $(SUB_SRCS)

$(TARGET): $(COPYBOOKS) $(MAIN_SRC) $(SUB_SRCS)
	@command -v $(COBC) >/dev/null 2>&1 || bash scripts/toolchain.sh
	@mkdir -p $(BIN_DIR)
	@$(COBC) -x $(COBCFLAGS) $(MAIN_SRC) $(SUB_SRCS) -o $(TARGET)
	@echo "Compiled $(TARGET) cleanly."

build: check-format $(TARGET)
	@bash scripts/snapshot.sh ensure

test: build
	@bash tests/run_tests.sh "$(CASE)"

checkpoint:
	@bash scripts/snapshot.sh checkpoint

diff:
	@bash scripts/snapshot.sh diff

diff-baseline:
	@bash scripts/snapshot.sh diff-baseline

restore:
	@bash scripts/snapshot.sh restore

restore-baseline:
	@bash scripts/snapshot.sh restore-baseline

reset-data:
	@mkdir -p data/work
	@cp data/seed/accounts.seed.dat    data/work/accounts.dat
	@cp data/seed/subaccounts.seed.dat data/work/subaccounts.dat
	@cp data/seed/mandates.seed.dat    data/work/mandates.dat
	@cp data/seed/customers.seed.dat   data/work/customers.dat
	@: > data/work/audit.dat
	@echo "Working data files in data/work/ reset from data/seed/."

clean:
	@rm -rf $(BIN_DIR) data/work
	@echo "Cleaned $(BIN_DIR)/ and data/work/."
