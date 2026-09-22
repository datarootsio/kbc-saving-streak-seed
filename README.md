# Saving Streak

A training application: money moves from a current account into a savings account, and every whole
euro moved earns a point that can be spent on a reward.

Follow the [workshop exercises](exercises.md) to work through the delivery loop and platform guardrails.

Pull requests receive [automated AI reviews](docs/ai-pr-review.md) through PR-Agent and OpenRouter.

- `backend/` — Spring Boot 3.5 (Java 17, Maven wrapper), SQLite at `data/saving-streak.db`
- `frontend/` — React 18 + Vite 6, dev server on 5173 proxying `/api` to the backend

Run it locally with the Vite dev server and `backend/mvnw spring-boot:run`, or deploy it as
below — `make deploy` builds both and serves them behind nginx.

## Running the deployed app

The app runs on this VM as two long-lived pieces, and nginx is the only one on the network:

```
browser ──▶ nginx :80 ──┬──▶ /var/www/saving-streak        the built frontend bundle
                        └──▶ 127.0.0.1:8081  /api/…        saving-streak.service (the jar)
```

One origin for both halves, which is the same shape as the Vite dev proxy in
`frontend/vite.config.ts`. The frontend's `/api` calls are relative and work unchanged either
way, and there is no CORS to configure. The backend listens on loopback only, so the jar cannot
be reached except through nginx.

Not port 8080: **code-server already listens there on this VM**, and starting the backend on it
fails with "Identify and stop the process that's listening on port 8080". 8081 is the default,
and `BACKEND_PORT` overrides it.

### Prerequisites, once per machine

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless nginx unzip rsync curl make
```

`unzip` is not optional. Without it `backend/mvnw` silently switches to the `.tar.gz`
distribution and then validates it against the `.zip`'s pinned SHA-256, so every build dies with
`Failed to validate Maven distribution SHA-256, your Maven distribution might be compromised`.
That message is a wrapper bug, not a compromised artifact — installing `unzip` is the fix, and
editing `distributionSha256Sum` is not.

Node and npm come from the distribution or nodesource. `make tools` checks the whole list and
prints what is missing.

### Deploying

```bash
make deploy
```

That is build, install, restart, and prove it answers — the whole path, and the one to use after
any change. It ends by asking the running app for its seeded customers, so a deploy that starts
but cannot serve fails the command rather than looking successful. Run `make help` for every
target; the useful ones day to day are:

| Target | What it does |
| --- | --- |
| `make deploy` | build → install → restart → health, the whole path |
| `make build` | the jar and the frontend bundle, nothing installed |
| `make test` | the backend test suite |
| `make status` | both services, and what is listening on 80 and 8081 |
| `make logs` | follow the backend log (`journalctl -u saving-streak -f`) |
| `make health` | prove the app answers through the proxy |
| `make reset-data` | delete the database and start again on freshly seeded demo data |
| `make uninstall` | remove the service, the site and the web root; restore nginx's default site |

### What `make install` writes

Everything deployed is generated from a template in `deploy/`, with this machine's paths
substituted in. Edit the template and re-run `make install`; never edit the deployed copy, or the
next deploy silently reverts it.

| Generated | From | Holds |
| --- | --- | --- |
| `/etc/systemd/system/saving-streak.service` | `deploy/saving-streak.service.in` | how the jar is started, restarted and logged |
| `/etc/saving-streak.env` | `deploy/saving-streak.env.in` | profile, port, database path, log level |
| `/etc/nginx/sites-available/saving-streak` | `deploy/nginx-saving-streak.conf.in` | the reverse proxy and the static site |
| `/var/www/saving-streak/` | `frontend/dist/` | the bundle, copied because nginx's workers run as `www-data` and cannot traverse a home directory at mode 750 |

The unit is `systemctl enable`d, so the app comes back by itself after a reboot. `make install`
also removes nginx's stock `default` site: both claim `default_server` on port 80 and nginx
refuses to start with both. `make uninstall` puts it back.

### The deployed configuration

The service runs with `SPRING_PROFILES_ACTIVE=dev`, which is the point of the app rather than a
shortcut: it seeds the demo customers, exposes the movable clock, and exposes the run-a-job-now
endpoints a trainer demonstrates with. `LOGGING_LEVEL_IO_DATAROOTS_SAVINGSTREAK=DEBUG` is set so
that reading that one package shows what the app actually did: one line per business event with
the values that decided it, and a WARN on every refusal with its reason.

`SAVING_STREAK_DB` is an absolute path to `data/saving-streak.db`. The default in
`application.properties` is relative, and a relative path follows the service's working
directory, which would quietly create a second empty database the first time that changed.

Seeding happens only when there are no customers at all, so a restart never multiplies the demo
data and never restores it either. `make reset-data` deletes the file and starts the app again,
which is the only way back to the starting state.

### Reaching it from a browser

`make deploy` ends by printing the address to open, and `make url` prints it on its own. On this
VM that is `https://vm1.kbc.demo.dataroots.fun/proxy/80/`, behind the same password as the IDE —
but that hostname appears nowhere in the Makefile, because the same Makefile runs on other VMs.

It is read from the environment instead: code-server exports `VSCODE_PROXY_URI` into every
terminal it starts, as the template `https://<this-host>/proxy/{{port}}/`, and the Makefile
substitutes `HTTP_PORT` into it. A different VM therefore prints its own address with nothing
edited. Run `make` from a shell that has no such variable — plain ssh, a cron job — and rather
than invent a hostname it prints the VNet address and says the public one is unknown from there.

Nothing else reaches this VM from outside. It has no public IP of its own; it sits at `10.10.1.4`
on the VNet, and the gateway in front terminates TLS and forwards **only to port 8080**, which is
code-server's. Port 80 is open on the VM and answers on the VNet, but no external request has
ever arrived on it. code-server's port proxy is the way through: it forwards `/proxy/<port>/` to
`localhost:<port>` on this VM, reaching nginx with no Azure change at all.

If someone adds an App Gateway rule and an NSG rule for port 80 later, `http://10.10.1.4/` starts
working too. Nothing here has to change for that: every route works at once.

#### Why the page used to come up blank, and what stops it now

A page served under a path prefix cannot ask for `/assets/index-*.js` or `/api/customers`: the
leading slash discards the prefix. The failure is silent — the HTML and the title load, the bundle
does not, and the page is simply white with the answer only in the browser console. All three
causes are now closed, and each fix is equally correct at `/`:

| What broke | Fix |
| --- | --- |
| `/proxy/80/` asked the proxy root for `/assets/…` and `/api/…`, answered 401 | `vite.config.ts` sets `base: './'`; `api.ts` resolves every endpoint through one `apiUrl()` helper on `document.baseURI` |
| `/proxy/80` without the slash resolved assets to `/proxy/assets/…`, answered 400 | an inline script in `index.html` adds the slash and reloads; it cannot loop, and at `/` it never fires |
| `/absproxy/80/` keeps its prefix, so nginx served index.html for the bundle and the browser rejected it on MIME type | the nginx site strips `^/absproxy/<port>` and re-runs matching, so both proxy forms land on the same rules |

Keep new `fetch` calls going through `apiUrl()`. A literal `/api/…` works at the root and breaks
behind the proxy, which is much the harder of the two to notice.

The nginx site also sets `absolute_redirect off`, so redirects are relative. nginx builds absolute
ones from its own name and port, which sends a browser on `host:8080/absproxy/80` to
`host/absproxy/80/` — losing the port, and the app with it.

### Ports are arguments, not edits

```bash
make deploy BACKEND_PORT=9090 HTTP_PORT=8088
```

Both flow into the unit, the env file and the nginx site together, so the two halves cannot drift
apart. Pass the same values to every later `make` call for that deployment, or re-run `make
deploy` with the defaults to go back.
