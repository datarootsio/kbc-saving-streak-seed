# Workshop deployment

`make deploy` uses the existing VM layout: nginx on port 80 and the `saving-streak` systemd service on loopback port 8081. The browser IDE proxies nginx; `VSCODE_PROXY_URI` supplies the public URL. Rebuild and deploy after switching exercises or changing code.

The Makefile selects the exercise, build command and health endpoint. `manage.sh` stages a runtime copy under `/opt/saving-streak/<exercise>`, then stops the old service, installs the copy and restarts it. The running app does not depend on the checkout, so a branch switch or `make clean` cannot remove its runtime files. Notification serves the built frontend through nginx; reminder and OpenRefine proxy the entire app. OpenRefine's backend accepts loopback Host headers supplied by nginx.

Each exercise keeps data under `~/.local/share/kbc-workshop/<exercise>`. Redeployment preserves that data. `make clean` removes build output only. Java 21, Node 24 and Maven are downloaded from their official releases, verified against pinned checksums, and cached under `~/.cache/kbc-workshop/toolchains` on the Linux x64 workshop VMs. No global Java or Node installation is replaced. On other platforms, build and run targets use the installed toolchain.

Deployment needs the existing nginx, systemd, curl, rsync, tar, xz and unzip tools plus sudo access. `make deploy` validates these before building. Application tests remain a separate `make test` command because the exercises deliberately contain bugs.

For an isolated deployment test, override the service, ports and data directory together:

```bash
make deploy SERVICE=kbc-exercise-test HTTP_PORT=18110 BACKEND_PORT=18111 \
  STATE_DIR="$HOME/kbc-test-data"
```

Use the same overrides with `make stop`, `make start`, `make status`, `make logs` and `make health`. Port 8080 belongs to the browser IDE. `TOOLCHAIN_DIR`, `WEB_ROOT`, `BASE_URL` and `MEMORY` can also be overridden. The default memory limit is 512 MB.

If deployment fails, inspect `make status` and `make logs`. A failed build leaves the previous deployment running. Switching branches changes source code; the app changes when `make deploy` finishes.
