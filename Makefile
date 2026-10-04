# Deploy the exercise selected by the current branch on the workshop VM.
SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help
APP_DIR := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
EXERCISE := data-cleaning-debug
APP_KIND := refine
HEALTH_PATH := /command/core/get-all-project-metadata
SERVICE ?= saving-streak
HTTP_PORT ?= 80
BACKEND_PORT ?= 8081
BASE_URL ?= http://127.0.0.1:$(HTTP_PORT)
WEB_ROOT ?= /var/www/$(SERVICE)
STATE_DIR ?= $(HOME)/.local/share/kbc-workshop/$(EXERCISE)
TOOLCHAIN_DIR ?= $(HOME)/.cache/kbc-workshop/toolchains
MEMORY ?= 512m
SUDO ?= sudo
PORT ?= 3333
DATA_DIR ?= $(CURDIR)/data/$(EXERCISE)
export APP_DIR EXERCISE APP_KIND HEALTH_PATH SERVICE HTTP_PORT BACKEND_PORT BASE_URL WEB_ROOT STATE_DIR TOOLCHAIN_DIR MEMORY SUDO

.PHONY: help tools build test run deploy install start stop restart status logs health url clean server-test lint
help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo "Exercise: $(EXERCISE); deploy port: $(HTTP_PORT); backend: $(BACKEND_PORT)"

tools: ## Prepare the cached Java, Node and Maven toolchain
	bash deploy/toolchain.sh --check

build: tools ## Install frontend dependencies and compile OpenRefine
	bash deploy/toolchain.sh ./refine build

test: ## Run backend tests
	bash deploy/toolchain.sh ./refine test

run: ## Run locally in the foreground (Ctrl-C stops it)
	bash deploy/toolchain.sh ./refine -i 127.0.0.1 -p $(PORT) -d "$(DATA_DIR)" -x refine.headless=true

# Recursive recipes are deliberately sequential, including when make -j is used.
deploy: ## Build, install, restart and verify the selected exercise
	bash deploy/manage.sh preflight
	$(MAKE) --no-print-directory build
	$(MAKE) --no-print-directory install
	$(MAKE) --no-print-directory restart
	$(MAKE) --no-print-directory health

install: ## Install the built app, service and nginx site
	bash deploy/manage.sh install

start: ## Start the deployed app
stop: ## Stop the deployed app
restart: ## Restart the deployed app
status: ## Show the deployed service status
logs: ## Follow the deployed app log
health: ## Check the deployed page and API
url: ## Print the browser URL
start stop restart status logs health url:
	bash deploy/manage.sh $@

clean: ## Remove build output, keeping exercise data
	bash deploy/toolchain.sh ./refine clean

server-test: ## Run application tests
	bash deploy/toolchain.sh ./refine server_test

lint: ## Apply Java formatting
	bash deploy/toolchain.sh ./refine lint
