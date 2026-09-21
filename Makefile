# Saving Streak — build and run, the same way every time.
#
# The app is two pieces: a Spring Boot jar on loopback and a static frontend bundle.
# nginx is the only thing on the network: it serves the bundle and forwards /api to
# the jar, so the browser sees one origin and the frontend's relative /api calls work
# exactly as they do behind the Vite dev proxy.
#
#   make deploy   build everything, install it, restart it, prove it answers
#   make help     every target, with what it does
#
# Deployment notes and the reasoning behind the layout live in README.md.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

# --- what and where -----------------------------------------------------------------
APP_DIR      := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
RUN_USER     := $(shell id -un)
SERVICE      := saving-streak
ENV_FILE     := /etc/$(SERVICE).env
UNIT_FILE    := /etc/systemd/system/$(SERVICE).service
NGINX_SITE   := /etc/nginx/sites-available/$(SERVICE)
NGINX_LINK   := /etc/nginx/sites-enabled/$(SERVICE)
WEB_ROOT     := /var/www/$(SERVICE)

# Not 8080: that is code-server's default, so the backend takes the next port. It binds
# 127.0.0.1 (see deploy/saving-streak.env.in) and is reached only through nginx. Override it
# if something on a given machine already holds 8081.
BACKEND_PORT ?= 8081
HTTP_PORT    ?= 80

JAVA         := $(shell command -v java 2>/dev/null)
# Read from the pom rather than typed here, so a version bump cannot leave this stale.
# Scoped to the project's own artifactId, so the parent's <version> is not picked up.
VERSION      := $(shell sed -n '/<artifactId>saving-streak<\/artifactId>/,/<\/version>/ s|.*<version>\(.*\)</version>.*|\1|p' $(APP_DIR)/backend/pom.xml | head -1)
JAR          := $(APP_DIR)/backend/target/saving-streak-$(VERSION).jar
DIST         := $(APP_DIR)/frontend/dist

SUDO         ?= sudo
BASE_URL     ?= http://localhost:$(HTTP_PORT)

# Where a browser actually reaches this app. Nothing outside the VNet can open HTTP_PORT
# directly, so the usable route is code-server's port proxy — read its port from its own config
# rather than assuming, and fall back to its default if the file is not there.
VM_IP        := $(shell hostname -I | awk '{print $$1}')

# The address a browser opens. code-server exports VSCODE_PROXY_URI into every terminal it starts,
# as a template like https://<this-host>/proxy/{{port}}/, so the public hostname is read from the
# IDE that is serving it rather than written down here. That is what lets one Makefile serve every
# VM: nothing about this machine is baked in, and a different VM prints its own address.
#
# Outside such a terminal there is nothing to read, and rather than invent a hostname the url
# target says so and falls back to the VNet address.
APP_URL      := $(if $(VSCODE_PROXY_URI),$(subst {{port}},$(HTTP_PORT),$(VSCODE_PROXY_URI)),)
LAN_URL      := http://$(VM_IP):$(HTTP_PORT)/

# Every template placeholder in one place, so `install` cannot substitute one and forget another.
SUBST = sed -e 's|@APP_DIR@|$(APP_DIR)|g' \
            -e 's|@RUN_USER@|$(RUN_USER)|g' \
            -e 's|@ENV_FILE@|$(ENV_FILE)|g' \
            -e 's|@JAVA@|$(JAVA)|g' \
            -e 's|@JAR@|$(JAR)|g' \
            -e 's|@WEB_ROOT@|$(WEB_ROOT)|g' \
            -e 's|@BACKEND_PORT@|$(BACKEND_PORT)|g' \
            -e 's|@HTTP_PORT@|$(HTTP_PORT)|g'

.PHONY: help tools build build-backend build-frontend test install install-web \
        install-service install-nginx deploy start stop restart status logs health \
        url reset-data clean uninstall

help: ## Show this help
	@echo "Saving Streak — targets:"
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[1m%-16s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "  app dir      $(APP_DIR)"
	@echo "  jar          $(JAR)"
	@echo "  backend port $(BACKEND_PORT) (loopback only)"
	@echo "  served at    $(BASE_URL)"

# --- prerequisites ------------------------------------------------------------------
tools: ## Check the toolchain this machine needs
	@missing=""; \
	for t in java node npm nginx unzip rsync curl; do \
	  command -v $$t >/dev/null || missing="$$missing $$t"; \
	done; \
	if [ -n "$$missing" ]; then \
	  echo "missing:$$missing"; \
	  echo "install with: sudo apt-get install -y openjdk-17-jdk-headless nginx unzip rsync curl"; \
	  echo "(node and npm come from nodesource or the distribution)"; \
	  exit 1; \
	fi
	@java -version 2>&1 | head -1
	@node --version
	@nginx -v 2>&1
	@echo "toolchain ok"
	@# unzip is not decoration: without it backend/mvnw downloads the .tar.gz
	@# distribution and then checks it against the .zip's pinned SHA-256, and fails
	@# with "your Maven distribution might be compromised".

# --- build --------------------------------------------------------------------------
build: build-backend build-frontend ## Build the backend jar and the frontend bundle

build-backend: tools ## Build the Spring Boot jar
	cd $(APP_DIR)/backend && ./mvnw -B -DskipTests package
	@test -f $(JAR) || { echo "expected jar not found: $(JAR)"; exit 1; }

build-frontend: tools ## Install frontend dependencies and build the static bundle
	cd $(APP_DIR)/frontend && npm ci && npm run build
	@test -f $(DIST)/index.html || { echo "expected bundle not found: $(DIST)/index.html"; exit 1; }

test: tools ## Run the backend test suite
	cd $(APP_DIR)/backend && ./mvnw -B test

# --- install ------------------------------------------------------------------------
install: install-web install-service install-nginx ## Install the bundle, the service and the nginx site

install-web: ## Copy the built bundle to the web root nginx can read
	@test -f $(DIST)/index.html || { echo "run 'make build-frontend' first"; exit 1; }
	$(SUDO) mkdir -p $(WEB_ROOT)
	@# --delete, so last deploy's fingerprinted assets do not accumulate forever.
	$(SUDO) rsync -a --delete $(DIST)/ $(WEB_ROOT)/
	$(SUDO) chown -R root:root $(WEB_ROOT)
	$(SUDO) chmod -R a+rX $(WEB_ROOT)

install-service: ## Write /etc/saving-streak.env and the systemd unit, and enable it
	@test -n "$(JAVA)" || { echo "java not on PATH"; exit 1; }
	@test -f $(JAR) || { echo "run 'make build-backend' first"; exit 1; }
	$(SUBST) $(APP_DIR)/deploy/saving-streak.env.in | $(SUDO) tee $(ENV_FILE) >/dev/null
	$(SUBST) $(APP_DIR)/deploy/saving-streak.service.in | $(SUDO) tee $(UNIT_FILE) >/dev/null
	$(SUDO) systemctl daemon-reload
	$(SUDO) systemctl enable $(SERVICE)

install-nginx: ## Write the nginx site, disable the stock default, reload nginx
	$(SUBST) $(APP_DIR)/deploy/nginx-saving-streak.conf.in | $(SUDO) tee $(NGINX_SITE) >/dev/null
	$(SUDO) ln -sfn $(NGINX_SITE) $(NGINX_LINK)
	@# Both claim `default_server` on the same port, so nginx refuses to start with both.
	$(SUDO) rm -f /etc/nginx/sites-enabled/default
	$(SUDO) nginx -t
	$(SUDO) systemctl reload nginx

# --- run ----------------------------------------------------------------------------
deploy: build install restart health ## Build, install, restart and verify — the whole path

start: ## Start the backend and nginx
	$(SUDO) systemctl start $(SERVICE)
	$(SUDO) systemctl start nginx

stop: ## Stop the backend (nginx keeps running)
	$(SUDO) systemctl stop $(SERVICE)

restart: ## Restart the backend and reload nginx
	$(SUDO) systemctl restart $(SERVICE)
	$(SUDO) systemctl reload nginx

status: ## Show what is running and what is listening
	@systemctl --no-pager --lines=0 status $(SERVICE) || true
	@systemctl --no-pager --lines=0 status nginx || true
	@echo "--- listening ---"
	@ss -ltn | grep -E ':($(HTTP_PORT)|$(BACKEND_PORT))\b' || echo "nothing on $(HTTP_PORT) or $(BACKEND_PORT)"

logs: ## Follow the backend log (Ctrl-C to stop)
	journalctl -u $(SERVICE) -f -n 100

health: ## Wait for the app to answer through the proxy, then prove it serves real data
	@echo "waiting for $(BASE_URL) ..."
	@for i in $$(seq 1 60); do \
	  if curl -fs -o /dev/null $(BASE_URL)/api/customers; then break; fi; \
	  if [ $$i -eq 60 ]; then \
	    echo "no answer after 60s — try 'make logs'"; exit 1; \
	  fi; \
	  sleep 1; \
	done
	@printf 'page      '; curl -fsS -o /dev/null -w '%{http_code} %{content_type}\n' $(BASE_URL)/
	@printf 'api       '; curl -fsS -o /dev/null -w '%{http_code} %{content_type}\n' $(BASE_URL)/api/customers
	@printf 'customers '; curl -fsS $(BASE_URL)/api/customers | head -c 200; echo
	@echo "healthy"
	@$(MAKE) --no-print-directory url

reset-data: ## Stop the app, delete the database, start it back on freshly seeded demo data
	-$(SUDO) systemctl stop $(SERVICE)
	@# DemoData seeds only when there are no customers at all, so an existing file is
	@# never re-seeded; removing it is the only way back to the starting state.
	rm -f $(APP_DIR)/data/saving-streak.db $(APP_DIR)/data/saving-streak.db-journal \
	      $(APP_DIR)/data/saving-streak.db-wal $(APP_DIR)/data/saving-streak.db-shm
	$(SUDO) systemctl start $(SERVICE)
	@$(MAKE) --no-print-directory health

url: ## Print where to open the app in a browser
	@echo ""
	@echo "  ==> OPEN THE APP AT <=="
	@echo ""
ifeq ($(APP_URL),)
	@echo "      $(LAN_URL)"
	@echo ""
	@echo "      That address works from inside the VNet only, and it is all this shell can"
	@echo "      tell: VSCODE_PROXY_URI is not set here, so the public hostname is unknown."
	@echo "      Run make from a terminal inside the IDE and it prints the real address."
else
	@echo "      $(APP_URL)"
	@echo ""
	@echo "      Behind the same password as the IDE. A missing trailing slash corrects"
	@echo "      itself, and /absproxy/$(HTTP_PORT)/ works the same way."
	@echo ""
	@echo "      On the VNet directly: $(LAN_URL)"
	@echo "      (that route needs an Azure rule for port $(HTTP_PORT); the one above needs nothing)"
endif
	@echo ""

# --- tidy up ------------------------------------------------------------------------
clean: ## Remove build output (keeps the database)
	rm -rf $(APP_DIR)/backend/target $(DIST)

uninstall: ## Stop and remove the service, the nginx site and the web root
	-$(SUDO) systemctl disable --now $(SERVICE)
	-$(SUDO) rm -f $(UNIT_FILE) $(ENV_FILE) $(NGINX_LINK) $(NGINX_SITE)
	-$(SUDO) rm -rf $(WEB_ROOT)
	$(SUDO) systemctl daemon-reload
	-$(SUDO) ln -sfn /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default
	$(SUDO) nginx -t && $(SUDO) systemctl reload nginx
