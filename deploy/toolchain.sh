#!/usr/bin/env bash
# Keep workshop build tools in the user's cache, shared across exercise branches.
set -euo pipefail
if [[ $(uname -s) == Linux && $(uname -m) == x86_64 ]]; then
    cache=${TOOLCHAIN_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/kbc-workshop/toolchains}
    mkdir -p "$cache"
    install_archive() (
        local name=$1 url=$2 checksum=$3 algorithm=$4 archive stage
        [[ -x "$cache/$name/bin/${5}" ]] && return
        archive=$(mktemp "$cache/download.XXXXXX")
        stage=$(mktemp -d "$cache/extract.XXXXXX")
        trap 'rm -f "$archive"; rm -rf "$stage"' EXIT
        echo "Installing $name in $cache (first deployment only)"
        curl --fail --location --retry 3 --silent --show-error "$url" -o "$archive"
        printf '%s  %s\n' "$checksum" "$archive" | "sha${algorithm}sum" --check --status
        tar -xf "$archive" --strip-components=1 -C "$stage"
        mv "$stage" "$cache/$name"
        rm -f "$archive"
        trap - EXIT
    )
    install_archive jdk-21.0.12.1 \
        'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz' \
        ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 256 java
    install_archive node-24.21.0 \
        https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.xz \
        fd8e59d5a511510f6a298afb548f18c7d2b1be404d8b4a27d94fbe49f56cb2d6 256 node
    install_archive maven-3.9.16 \
        https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.tar.gz \
        831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6 512 mvn
    export JAVA_HOME="$cache/jdk-21.0.12.1"
    export MAVEN_HOME="$cache/maven-3.9.16"
    export PATH="$JAVA_HOME/bin:$cache/node-24.21.0/bin:$MAVEN_HOME/bin:$PATH"
fi
if [[ ${1:-} == --check ]]; then
    java -version
    node --version
    npm --version
    if [[ ${APP_KIND:-} == refine ]]; then mvn --version; fi
else
    exec "$@"
fi
