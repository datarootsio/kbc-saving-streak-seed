#!/usr/bin/env bash
# Shared deployment lifecycle; Make supplies the branch-specific settings.
set -euo pipefail
cd "$APP_DIR"
action=${1:?Pass a deployment action}
runtime="/opt/$SERVICE/$EXERCISE"
unit="/etc/systemd/system/$SERVICE.service"
site="/etc/nginx/sites-available/$SERVICE"
link="/etc/nginx/sites-enabled/$SERVICE"
sudo_cmd=()
[[ -z ${SUDO-sudo} ]] || read -r -a sudo_cmd <<< "${SUDO-sudo}"

url() {
    if [[ -n ${VSCODE_PROXY_URI:-} ]]; then
        printf 'Open the exercise: %s\n' "${VSCODE_PROXY_URI//\{\{port\}\}/$HTTP_PORT}"
    else
        printf 'Open the exercise: http://localhost:%s/\n' "$HTTP_PORT"
        echo 'In the VM browser IDE, run make url to print its public proxy URL.'
    fi
}
health() {
    local attempt
    for attempt in {1..60}; do
        if curl --fail --silent --max-time 2 "$BASE_URL$HEALTH_PATH" >/dev/null; then
            curl --fail --silent --show-error --max-time 5 "$BASE_URL/" >/dev/null
            echo "$EXERCISE: page and API are healthy"
            url
            return
        fi
        sleep 1
    done
    echo "Deployment did not become healthy. Run make status and make logs." >&2
    return 1
}
case "$action" in
    preflight)
        [[ $(uname -s) == Linux ]] || { echo 'make deploy runs on the Linux workshop VM. Use make run locally.' >&2; exit 1; }
        [[ $SERVICE =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Invalid SERVICE name' >&2; exit 1; }
        for port in "$HTTP_PORT" "$BACKEND_PORT"; do
            [[ $port =~ ^[0-9]+$ ]] && ((port > 0 && port < 65536)) || { echo 'Ports must be between 1 and 65535' >&2; exit 1; }
            [[ $port != 8080 ]] || { echo 'Port 8080 belongs to the browser IDE; choose another port.' >&2; exit 1; }
        done
        [[ $HTTP_PORT != "$BACKEND_PORT" ]] || { echo 'HTTP_PORT and BACKEND_PORT must differ.' >&2; exit 1; }
        for tool in nginx rsync curl systemctl tar xz sha256sum sha512sum unzip; do
            command -v "$tool" >/dev/null || { echo "Missing $tool. Install the VM tools: sudo apt-get install -y nginx rsync curl unzip xz-utils make" >&2; exit 1; }
        done
        if ((${#sudo_cmd[@]})); then "${sudo_cmd[@]}" true; fi
        ;;
    install)
        stage=$(mktemp -d)
        trap 'rm -rf "$stage"' EXIT
        mkdir -p "$stage/runtime" "$STATE_DIR"
        java_bin=$(bash deploy/toolchain.sh bash -c 'command -v java')
        if [[ $APP_KIND == notification ]]; then
            jar=$(find backend/target -maxdepth 1 -name '*.jar' ! -name '*.original' -print -quit)
            [[ -n $jar && -f frontend/dist/index.html ]]
            cp "$jar" "$stage/runtime/app.jar"
            "${sudo_cmd[@]}" mkdir -p "$WEB_ROOT"
            "${sudo_cmd[@]}" rsync -a --delete frontend/dist/ "$WEB_ROOT/"
            "${sudo_cmd[@]}" chmod -R a+rX "$WEB_ROOT"
            {
                echo '#!/usr/bin/env bash'
                printf 'export SPRING_PROFILES_ACTIVE=dev SERVER_ADDRESS=127.0.0.1 SERVER_PORT=%q\n' "$BACKEND_PORT"
                printf 'export SAVING_STREAK_DB=%q\n' "$STATE_DIR/saving-streak.db"
                printf 'exec %q -Xmx%q -jar %q\n' "$java_bin" "$MEMORY" "$runtime/app.jar"
            } > "$stage/runtime/start.sh"
            cp deploy/nginx-notification.conf.in "$stage/nginx.conf"
        elif [[ $APP_KIND == reminder ]]; then
            jar=$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' -print -quit)
            [[ -n $jar ]]
            cp "$jar" "$stage/runtime/app.jar"
            {
                echo '#!/usr/bin/env bash'
                printf 'export SERVER_ADDRESS=127.0.0.1 SERVER_PORT=%q\n' "$BACKEND_PORT"
                printf 'export REMINDERS_DATA_FILE=%q\n' "$STATE_DIR/reminders.json"
                printf 'exec %q -Xmx%q -jar %q\n' "$java_bin" "$MEMORY" "$runtime/app.jar"
            } > "$stage/runtime/start.sh"
            cp deploy/nginx-proxy.conf.in "$stage/nginx.conf"
        else
            [[ -f server/classes/com/google/refine/Refine.class ]]
            mkdir -p "$stage/runtime/server/target" "$stage/runtime/main"
            cp -a server/classes "$stage/runtime/server/"
            cp -a server/target/lib "$stage/runtime/server/target/"
            rsync -a --exclude=node_modules main/webapp/ "$stage/runtime/main/webapp/"
            cp LICENSE.txt "$stage/runtime/"
            {
                echo '#!/usr/bin/env bash'
                printf 'exec %q -Xms128M -Xmx%q -Drefine.headless=true -Drefine.interface=127.0.0.1 -Drefine.host=127.0.0.1 -Drefine.port=%q -Drefine.data_dir=%q -Drefine.webapp=main/webapp -Drefine.max_form_content_size=52428800 -Dpython.path=main/webapp/WEB-INF/lib/jython -cp %q com.google.refine.Refine\n' \
                    "$java_bin" "$MEMORY" "$BACKEND_PORT" "$STATE_DIR" 'server/classes:server/target/lib/*'
            } > "$stage/runtime/start.sh"
            cp deploy/nginx-proxy.conf.in "$stage/nginx.conf"
        fi
        # Build and stage before stopping the old exercise. Stop before replacing files.
        if systemctl cat "$SERVICE" >/dev/null 2>&1; then
            "${sudo_cmd[@]}" systemctl stop "$SERVICE"
        fi
        "${sudo_cmd[@]}" mkdir -p "$runtime"
        "${sudo_cmd[@]}" rsync -a --delete "$stage/runtime/" "$runtime/"
        "${sudo_cmd[@]}" chmod -R a+rX "$runtime"
        cat > "$stage/app.service" <<EOF
[Unit]
Description=KBC workshop exercise: $EXERCISE
After=network-online.target
Wants=network-online.target

[Service]
Type=exec
User=$(id -un)
Group=$(id -gn)
WorkingDirectory=$runtime
ExecStart=/bin/bash $runtime/start.sh
Restart=on-failure
RestartSec=3
SuccessExitStatus=143
TimeoutStopSec=45
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
        sed -i -e "s|@HTTP_PORT@|$HTTP_PORT|g" -e "s|@BACKEND_PORT@|$BACKEND_PORT|g" \
            -e "s|@WEB_ROOT@|$WEB_ROOT|g" "$stage/nginx.conf"
        "${sudo_cmd[@]}" install -m 644 "$stage/app.service" "$unit"
        "${sudo_cmd[@]}" install -m 644 "$stage/nginx.conf" "$site"
        "${sudo_cmd[@]}" ln -sfn "$site" "$link"
        # Only the default workshop site replaces Ubuntu's default on port 80.
        if [[ $HTTP_PORT == 80 ]]; then "${sudo_cmd[@]}" rm -f /etc/nginx/sites-enabled/default; fi
        "${sudo_cmd[@]}" nginx -t
        "${sudo_cmd[@]}" systemctl daemon-reload
        "${sudo_cmd[@]}" systemctl enable "$SERVICE"
        ;;
    start|restart)
        "${sudo_cmd[@]}" systemctl "$action" "$SERVICE"
        "${sudo_cmd[@]}" systemctl start nginx
        "${sudo_cmd[@]}" systemctl reload nginx
        ;;
    stop) "${sudo_cmd[@]}" systemctl stop "$SERVICE" ;;
    status) systemctl --no-pager --lines=10 status "$SERVICE" ;;
    logs) journalctl -u "$SERVICE" -f -n 60 ;;
    health) health ;;
    url) url ;;
    *) echo "Unknown action: $action" >&2; exit 1 ;;
esac
